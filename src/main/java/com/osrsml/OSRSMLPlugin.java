package com.osrsml;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@PluginDescriptor(
    name = "OSRS ML AI Player",
    description = "RL + Guide powered autonomous OSRS player"
)
public class OSRSMLPlugin extends Plugin {
    
    @Inject private Client client;
    @Inject private OverlayManager overlayManager;
    @Inject private KeyManager keyManager;
    
    private MLOverlay overlay;
    private final Gson gson = new Gson();
    private Robot robot;
    private Canvas gameCanvas;
    
    public enum Mode { GPT_ADVISOR, RL_TRAINING, RL_INFERENCE, MANUAL }
    private Mode currentMode = Mode.GPT_ADVISOR;
    private boolean autoExecute = false;
    
    private static final String RL_HOST = "localhost";
    private static final int RL_PORT = 8889;
    
    private String currentTask = "Initializing...";
    private int tickCounter = 0;
    private Map<Skill, Integer> skillLevels = new HashMap<>();
    
    private String currentActivity = "Idle";
    private String lastResource = "";
    private int resourcesGathered = 0;
    private long lastActivityTime = 0;
    private long lastActionTime = 0;
    
    private String currentArea = "Unknown";
    private int currentX = 0, currentY = 0;
    
    private int[] lastRLAction = new int[]{0, 0, 0, 0};
    private double lastReward = 0;
    private int episodeSteps = 0;
    private double episodeReward = 0;
    private String lastExecutedAction = "None";
    
    private Integer walkToX = null;
    private Integer walkToY = null;
    private String walkToName = null;
    private boolean stayHere = true;
    
    private Skill targetSkill = null;
    private int targetLevel = 0;
    
    private static final long ACTION_COOLDOWN_MS = 2000;
    private boolean onBreak = false;
    private long breakStartTime = 0;
    private long breakDuration = 0;
    private long nextBreakTime = 0;
    
    private boolean initialized = false;
    private static final int RL_REQUEST_INTERVAL = 5;
    
    private volatile Rectangle cachedClickBounds = null;
    private volatile String cachedClickName = "";
    
    private final HotkeyListener modeToggleHotkey = new HotkeyListener(() -> 
            new net.runelite.client.config.Keybind(KeyEvent.VK_F5, 0)) {
        @Override
        public void hotkeyPressed() { cycleMode(); }
    };
    
    private final HotkeyListener resetHotkey = new HotkeyListener(() -> 
            new net.runelite.client.config.Keybind(KeyEvent.VK_F6, 0)) {
        @Override
        public void hotkeyPressed() { resetEpisode(); }
    };
    
    private final HotkeyListener autoExecuteHotkey = new HotkeyListener(() -> 
            new net.runelite.client.config.Keybind(KeyEvent.VK_F8, 0)) {
        @Override
        public void hotkeyPressed() { toggleAutoExecute(); }
    };

    @Override
    protected void startUp() {
        overlay = new MLOverlay(this, client);
        overlayManager.add(overlay);
        keyManager.registerKeyListener(modeToggleHotkey);
        keyManager.registerKeyListener(resetHotkey);
        keyManager.registerKeyListener(autoExecuteHotkey);
        
        try {
            robot = new Robot();
            robot.setAutoDelay(0);
        } catch (AWTException e) {
            System.out.println("[ML] Robot failed: " + e.getMessage());
        }
        
        scheduleNextBreak();
        System.out.println("[ML] Started - F5:Mode F6:Reset F8:Auto");
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        keyManager.unregisterKeyListener(modeToggleHotkey);
        keyManager.unregisterKeyListener(resetHotkey);
        keyManager.unregisterKeyListener(autoExecuteHotkey);
    }
    
    private java.awt.Point getCanvasOffset() {
        if (gameCanvas == null) gameCanvas = client.getCanvas();
        if (gameCanvas == null) return new java.awt.Point(0, 0);
        try { return gameCanvas.getLocationOnScreen(); } 
        catch (Exception e) { return new java.awt.Point(0, 0); }
    }
    
    private void moveMouse(int x, int y) {
        if (robot == null) return;
        java.awt.Point offset = getCanvasOffset();
        int targetX = offset.x + x;
        int targetY = offset.y + y;
        java.awt.Point current = MouseInfo.getPointerInfo().getLocation();
        
        int steps = 5 + ThreadLocalRandom.current().nextInt(5);
        for (int i = 1; i <= steps; i++) {
            double progress = (double) i / steps;
            progress = 1 - Math.pow(1 - progress, 2);
            int moveX = (int) (current.x + (targetX - current.x) * progress);
            int moveY = (int) (current.y + (targetY - current.y) * progress);
            robot.mouseMove(moveX, moveY);
            try { Thread.sleep(5 + ThreadLocalRandom.current().nextLong(5)); } catch (InterruptedException e) {}
        }
        robot.mouseMove(targetX, targetY);
    }
    
    private void click() {
        if (robot == null) return;
        try { Thread.sleep(30 + ThreadLocalRandom.current().nextLong(70)); } catch (InterruptedException e) {}
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        try { Thread.sleep(30 + ThreadLocalRandom.current().nextLong(40)); } catch (InterruptedException e) {}
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }
    
    private void clickAt(int x, int y) {
        moveMouse(x + ThreadLocalRandom.current().nextInt(-2, 3), 
                  y + ThreadLocalRandom.current().nextInt(-2, 3));
        click();
    }
    
    private void walkToTile(int worldX, int worldY) {
        Player local = client.getLocalPlayer();
        if (local == null) return;
        
        WorldPoint target = new WorldPoint(worldX, worldY, client.getPlane());
        LocalPoint localPoint = LocalPoint.fromWorld(client, target);
        
        if (localPoint != null) {
            net.runelite.api.Point canvasPoint = Perspective.localToCanvas(client, localPoint, client.getPlane());
            if (canvasPoint != null && canvasPoint.getX() > 5 && canvasPoint.getY() > 5 &&
                canvasPoint.getX() < client.getCanvasWidth() - 5 && canvasPoint.getY() < client.getCanvasHeight() - 5) {
                System.out.println("[ML] Walking to screen tile " + worldX + "," + worldY);
                clickAt(canvasPoint.getX(), canvasPoint.getY());
                return;
            }
        }
        clickMinimap(worldX, worldY);
    }
    
    private void clickMinimap(int worldX, int worldY) {
        Player local = client.getLocalPlayer();
        if (local == null) return;
        
        WorldPoint playerLoc = local.getWorldLocation();
        int dx = worldX - playerLoc.getX();
        int dy = worldY - playerLoc.getY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        // Limit click distance to 10 tiles for reliability
        int maxDist = 10;
        if (distance > maxDist) {
            double scale = maxDist / distance;
            dx = (int) Math.round(dx * scale);
            dy = (int) Math.round(dy * scale);
        }
        
        // Minimap center and scale (fixed mode)
        int minimapCenterX = 643;
        int minimapCenterY = 83;
        int pixelsPerTile = 4;
        
        int clickX = minimapCenterX + (dx * pixelsPerTile);
        int clickY = minimapCenterY - (dy * pixelsPerTile);
        
        // Clamp to minimap area
        clickX = Math.max(580, Math.min(705, clickX));
        clickY = Math.max(15, Math.min(155, clickY));
        
        System.out.println("[ML] Minimap: target=" + worldX + "," + worldY + " dist=" + (int)distance + " click=" + clickX + "," + clickY);
        clickAt(clickX, clickY);
    }
    
    private void scheduleNextBreak() {
        nextBreakTime = System.currentTimeMillis() + 300000 + ThreadLocalRandom.current().nextLong(180000);
    }
    
    private void checkBreaks() {
        long now = System.currentTimeMillis();
        if (onBreak) {
            if (now >= breakStartTime + breakDuration) {
                onBreak = false;
                scheduleNextBreak();
            }
        } else if (now >= nextBreakTime) {
            onBreak = true;
            breakStartTime = now;
            breakDuration = 15000 + ThreadLocalRandom.current().nextLong(30000);
            lastExecutedAction = "Break " + (breakDuration/1000) + "s";
        }
    }
    
    public boolean isOnBreak() { return onBreak; }
    
    private void cycleMode() {
        Mode[] modes = Mode.values();
        currentMode = modes[(currentMode.ordinal() + 1) % modes.length];
        if (client.getGameState() == GameState.LOGGED_IN) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[ML] Mode: " + currentMode.name(), null);
        }
    }
    
    private void toggleAutoExecute() {
        autoExecute = !autoExecute;
        if (client.getGameState() == GameState.LOGGED_IN) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[ML] Auto: " + (autoExecute ? "ON" : "OFF"), null);
        }
    }
    
    private void resetEpisode() {
        episodeSteps = 0;
        episodeReward = 0;
        resourcesGathered = 0;
        onBreak = false;
        scheduleNextBreak();
        lastExecutedAction = "Reset";
    }
    
    private void findAndCacheTarget(int[] action) {
        cachedClickBounds = null;
        cachedClickName = "";
        
        Player local = client.getLocalPlayer();
        if (local == null) return;
        
        int highLevel = action[0];
        int subAction = action[1];
        
        switch (highLevel) {
            case 0: lastExecutedAction = "Waiting"; return;
            case 1: // Skilling
                switch (subAction) {
                    case 0: findNearestObject("Mine", "Rocks", "Copper", "Tin", "Iron"); break;
                    case 1: findNearestNPC("Net", "Lure", "Bait", "Fishing spot"); break;
                    case 2: findNearestObject("Chop", "Tree", "Oak", "Willow"); break;
                    default: findNearestObject("Mine", "Rocks"); break;
                }
                break;
            case 2: findNearestAttackable(); break;
            case 3: findDialog(); break;
            case 4: 
                if (!findNearestObject("Bank", "Bank booth", "Bank chest")) {
                    findNearestNPC("Bank", "Banker");
                }
                break;
            case 5: lastExecutedAction = "Walking..."; return;
            case 6: findNearestObject("Open", "Gate", "Door"); break;
            case 8: findFood(); break;
            default: lastExecutedAction = "Idle"; return;
        }
    }
    
    private boolean findNearestObject(String action, String... names) {
        Player local = client.getLocalPlayer();
        if (local == null) return false;
        
        WorldPoint playerLoc = local.getWorldLocation();
        GameObject nearest = null;
        int nearestDist = Integer.MAX_VALUE;
        
        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        int plane = client.getPlane();
        
        for (int x = 0; x < 104; x++) {
            for (int y = 0; y < 104; y++) {
                Tile tile = tiles[plane][x][y];
                if (tile == null) continue;
                
                for (GameObject obj : tile.getGameObjects()) {
                    if (obj == null) continue;
                    ObjectComposition comp = client.getObjectDefinition(obj.getId());
                    if (comp == null) continue;
                    
                    String objName = comp.getName();
                    if (objName == null || objName.equals("null")) continue;
                    
                    boolean matches = false;
                    for (String name : names) {
                        if (objName.toLowerCase().contains(name.toLowerCase())) {
                            matches = true; break;
                        }
                    }
                    if (!matches) continue;
                    
                    String[] actions = comp.getActions();
                    boolean hasAction = false;
                    if (actions != null) {
                        for (String a : actions) {
                            if (a != null && a.toLowerCase().contains(action.toLowerCase())) {
                                hasAction = true; break;
                            }
                        }
                    }
                    if (!hasAction) continue;
                    
                    int dist = obj.getWorldLocation().distanceTo(playerLoc);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = obj;
                    }
                }
            }
        }
        
        if (nearest != null && nearestDist <= 15) {
            Shape shape = nearest.getConvexHull();
            if (shape != null) {
                Rectangle bounds = shape.getBounds();
                if (bounds.width > 0 && bounds.height > 0 && bounds.x > 5 && bounds.y > 5) {
                    cachedClickBounds = bounds;
                    cachedClickName = client.getObjectDefinition(nearest.getId()).getName();
                    lastExecutedAction = action + " " + cachedClickName;
                    return true;
                }
            }
        }
        lastExecutedAction = "No " + names[0];
        return false;
    }
    
    private boolean findNearestNPC(String action, String... names) {
        Player local = client.getLocalPlayer();
        if (local == null) return false;
        
        WorldPoint playerLoc = local.getWorldLocation();
        NPC nearest = null;
        int nearestDist = Integer.MAX_VALUE;
        
        for (NPC npc : client.getNpcs()) {
            if (npc == null) continue;
            String npcName = npc.getName();
            if (npcName == null) continue;
            
            boolean matches = names.length == 0 || names[0].isEmpty();
            for (String name : names) {
                if (!name.isEmpty() && npcName.toLowerCase().contains(name.toLowerCase())) {
                    matches = true; break;
                }
            }
            if (!matches) continue;
            
            NPCComposition comp = npc.getComposition();
            if (comp == null) continue;
            
            String[] actions = comp.getActions();
            boolean hasAction = false;
            if (actions != null) {
                for (String a : actions) {
                    if (a != null && a.toLowerCase().contains(action.toLowerCase())) {
                        hasAction = true; break;
                    }
                }
            }
            if (!hasAction) continue;
            
            int dist = npc.getWorldLocation().distanceTo(playerLoc);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = npc;
            }
        }
        
        if (nearest != null && nearestDist <= 15) {
            Shape shape = nearest.getConvexHull();
            if (shape != null) {
                Rectangle bounds = shape.getBounds();
                if (bounds.width > 0 && bounds.height > 0 && bounds.x > 0 && bounds.y > 0) {
                    cachedClickBounds = bounds;
                    cachedClickName = nearest.getName();
                    lastExecutedAction = action + " " + cachedClickName;
                    return true;
                }
            }
        }
        lastExecutedAction = "No NPC";
        return false;
    }
    
    private boolean findNearestAttackable() {
        Player local = client.getLocalPlayer();
        if (local == null) return false;
        
        if (local.getInteracting() != null) {
            lastExecutedAction = "In combat";
            return false;
        }
        
        WorldPoint playerLoc = local.getWorldLocation();
        NPC nearest = null;
        int nearestScore = Integer.MAX_VALUE;
        
        String[] preferredTargets = {"Chicken", "Cow", "Goblin", "Rat", "Spider", "Imp"};
        
        for (NPC npc : client.getNpcs()) {
            if (npc == null || npc.isDead()) continue;
            if (npc.getCombatLevel() <= 0 || npc.getCombatLevel() > 30) continue;
            
            NPCComposition comp = npc.getComposition();
            if (comp == null) continue;
            
            String[] actions = comp.getActions();
            boolean canAttack = false;
            if (actions != null) {
                for (String a : actions) {
                    if ("Attack".equalsIgnoreCase(a)) { canAttack = true; break; }
                }
            }
            if (!canAttack) continue;
            
            Shape shape = npc.getConvexHull();
            if (shape == null) continue;
            Rectangle bounds = shape.getBounds();
            if (bounds.width <= 0 || bounds.height <= 0 || bounds.x <= 0 || bounds.y <= 0) continue;
            
            int dist = npc.getWorldLocation().distanceTo(playerLoc);
            if (dist > 10) continue;
            
            int score = dist * 10;
            String npcName = npc.getName();
            if (npcName != null) {
                for (String target : preferredTargets) {
                    if (npcName.equalsIgnoreCase(target)) { score -= 50; break; }
                }
            }
            score += npc.getCombatLevel();
            
            if (score < nearestScore) {
                nearestScore = score;
                nearest = npc;
            }
        }
        
        if (nearest != null) {
            Shape shape = nearest.getConvexHull();
            Rectangle bounds = shape.getBounds();
            cachedClickBounds = bounds;
            cachedClickName = nearest.getName();
            lastExecutedAction = "Attack " + cachedClickName;
            return true;
        }
        lastExecutedAction = "No target";
        return false;
    }
    
    private void findDialog() {
        int[][] widgets = {{229, 2}, {217, 2}, {231, 2}, {219, 1}, {193, 2}};
        for (int[] w : widgets) {
            Widget widget = client.getWidget(w[0], w[1]);
            if (widget != null && !widget.isHidden()) {
                Rectangle bounds = widget.getBounds();
                if (bounds.width > 0 && bounds.height > 0) {
                    cachedClickBounds = bounds;
                    cachedClickName = "Continue";
                    lastExecutedAction = "Continue dialog";
                    return;
                }
            }
        }
        lastExecutedAction = "No dialog";
    }
    
    private void findFood() {
        Widget inventory = client.getWidget(WidgetInfo.INVENTORY);
        if (inventory == null || inventory.isHidden()) return;
        
        Widget[] items = inventory.getDynamicChildren();
        if (items == null) return;
        
        String[] foods = {"shrimp", "chicken", "meat", "bread", "trout", "salmon", "tuna", "lobster"};
        for (Widget item : items) {
            if (item == null || item.getItemId() == -1) continue;
            String name = client.getItemDefinition(item.getItemId()).getName().toLowerCase();
            for (String food : foods) {
                if (name.contains(food)) {
                    Rectangle bounds = item.getBounds();
                    if (bounds.width > 0 && bounds.height > 0) {
                        cachedClickBounds = bounds;
                        cachedClickName = name;
                        lastExecutedAction = "Eat " + name;
                        return;
                    }
                }
            }
        }
        lastExecutedAction = "No food";
    }
    
    private void executeClick() {
        if (cachedClickBounds == null) return;
        Rectangle bounds = cachedClickBounds;
        int x = bounds.x + bounds.width / 2 + ThreadLocalRandom.current().nextInt(-3, 4);
        int y = bounds.y + bounds.height / 2 + ThreadLocalRandom.current().nextInt(-3, 4);
        System.out.println("[ML] Clicking " + cachedClickName + " at " + x + "," + y);
        clickAt(x, y);
        cachedClickBounds = null;
    }
    
    public Map<String, Object> collectObservation() {
        Map<String, Object> obs = new HashMap<>();
        if (client.getGameState() != GameState.LOGGED_IN) return obs;
        
        Player local = client.getLocalPlayer();
        if (local == null) return obs;
        
        Map<String, Integer> skills = new HashMap<>();
        for (Skill skill : Skill.values()) {
            if (skill == Skill.OVERALL) continue;
            skills.put(skill.getName().toLowerCase(), client.getRealSkillLevel(skill));
        }
        obs.put("skills", skills);
        
        Map<String, Object> status = new HashMap<>();
        status.put("current_hp", client.getBoostedSkillLevel(Skill.HITPOINTS));
        status.put("max_hp", client.getRealSkillLevel(Skill.HITPOINTS));
        status.put("is_animating", local.getAnimation() != -1);
        status.put("in_combat", local.getInteracting() != null);
        obs.put("status", status);
        
        WorldPoint wp = local.getWorldLocation();
        obs.put("x", wp.getX());
        obs.put("y", wp.getY());
        obs.put("area", currentArea);
        obs.put("activity", currentActivity);
        obs.put("resources", resourcesGathered);
        
        return obs;
    }
    
    private Map<String, Object> requestRLAction(Map<String, Object> observation) {
        try (Socket socket = new Socket(RL_HOST, RL_PORT);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            socket.setSoTimeout(5000);
            
            Map<String, Object> request = new HashMap<>();
            request.put("type", "step");
            request.put("observation", observation);
            request.put("reward", lastReward);
            request.put("done", false);
            
            out.write(gson.toJson(request) + "\n");
            out.flush();
            
            String response = in.readLine();
            if (response != null) {
                return gson.fromJson(response, new TypeToken<Map<String, Object>>(){}.getType());
            }
        } catch (Exception e) {}
        return null;
    }
    
    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        
        tickCounter++;
        episodeSteps++;
        
        if (!initialized) {
            for (Skill skill : Skill.values()) {
                skillLevels.put(skill, client.getRealSkillLevel(skill));
            }
            initialized = true;
        }
        
        checkBreaks();
        updateLocation();
        
        if ((currentMode == Mode.RL_TRAINING || currentMode == Mode.RL_INFERENCE) && autoExecute) {
            long now = System.currentTimeMillis();
            if (now - lastActionTime < ACTION_COOLDOWN_MS) return;
            if (isOnBreak()) return;
            
            Player local = client.getLocalPlayer();
            if (local != null) {
                int anim = local.getAnimation();
                if (anim != -1 && anim != 808 && anim != 813) {
                    lastExecutedAction = "Busy...";
                    return;
                }
            }
            
            if (tickCounter % RL_REQUEST_INTERVAL == 0) {
                Map<String, Object> obs = collectObservation();
                Map<String, Object> response = requestRLAction(obs);
                
                if (response != null) {
                    if (response.containsKey("goal")) {
                        currentTask = response.get("goal").toString();
                    }
                    
                    stayHere = Boolean.TRUE.equals(response.get("stay_here"));
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> walkTo = (Map<String, Object>) response.get("walk_to");
                    if (walkTo != null) {
                        walkToX = ((Number) walkTo.get("x")).intValue();
                        walkToY = ((Number) walkTo.get("y")).intValue();
                        walkToName = (String) walkTo.get("name");
                    } else {
                        walkToX = null;
                        walkToY = null;
                        walkToName = null;
                    }
                    
                    @SuppressWarnings("unchecked")
                    List<Double> actionList = (List<Double>) response.get("action");
                    if (actionList != null) {
                        int[] action = new int[4];
                        for (int i = 0; i < Math.min(4, actionList.size()); i++) {
                            action[i] = actionList.get(i).intValue();
                        }
                        lastRLAction = action;
                        lastActionTime = now;
                        
                        // Walking
                        if (!stayHere && walkToX != null && walkToY != null) {
                            int dist = Math.abs(currentX - walkToX) + Math.abs(currentY - walkToY);
                            if (dist > 15) {
                                lastExecutedAction = "Walking to " + walkToName + " (" + dist + " tiles)";
                                final int wx = walkToX;
                                final int wy = walkToY;
                                new Thread(() -> walkToTile(wx, wy)).start();
                                return;
                            }
                        }
                        
                        findAndCacheTarget(action);
                        if (cachedClickBounds != null) {
                            new Thread(this::executeClick).start();
                        }
                    }
                }
            }
        }
    }
    
    private void updateLocation() {
        Player local = client.getLocalPlayer();
        if (local == null) return;
        WorldPoint wp = local.getWorldLocation();
        currentX = wp.getX();
        currentY = wp.getY();
        
        if (currentY > 9000) currentArea = "Underground";
        else if (currentX >= 3190 && currentX <= 3260 && currentY >= 3180 && currentY <= 3250) currentArea = "Lumbridge";
        else if (currentX >= 3220 && currentX <= 3260 && currentY >= 3280 && currentY <= 3320) currentArea = "Lumbridge Farm";
        else if (currentX >= 3170 && currentX <= 3300 && currentY >= 3380 && currentY <= 3510) currentArea = "Varrock";
        else currentArea = "(" + currentX + "," + currentY + ")";
    }
    
    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!initialized) return;
        String msg = event.getMessage().toLowerCase();
        ChatMessageType type = event.getType();
        
        if (type != ChatMessageType.SPAM && type != ChatMessageType.GAMEMESSAGE) return;
        
        double reward = 0;
        if (msg.contains("manage to mine") || msg.contains("get some ore")) {
            currentActivity = "Mining"; lastActivityTime = System.currentTimeMillis();
            resourcesGathered++; reward = 1.0; lastResource = "Ore";
        } else if (msg.contains("you catch")) {
            currentActivity = "Fishing"; lastActivityTime = System.currentTimeMillis();
            resourcesGathered++; reward = 1.0; lastResource = "Fish";
        } else if (msg.contains("get some") && msg.contains("log")) {
            currentActivity = "Woodcutting"; lastActivityTime = System.currentTimeMillis();
            resourcesGathered++; reward = 1.0; lastResource = "Logs";
        }
        
        lastReward = reward;
        episodeReward += reward;
    }
    
    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (!initialized) return;
        Skill skill = event.getSkill();
        int newLevel = client.getRealSkillLevel(skill);
        int oldLevel = skillLevels.getOrDefault(skill, 1);
        
        if (newLevel > oldLevel) {
            skillLevels.put(skill, newLevel);
            lastReward = 50.0;
            episodeReward += lastReward;
            System.out.println("[ML] Level up: " + skill.getName() + " -> " + newLevel);
        }
    }
    
    // Getters
    public Mode getMode() { return currentMode; }
    public String getCurrentTask() { return currentTask; }
    public String getCurrentArea() { return currentArea; }
    public String getCurrentActivity() { return currentActivity; }
    public int getResourcesGathered() { return resourcesGathered; }
    public boolean isStayHere() { return stayHere; }
    public int[] getLastRLAction() { return lastRLAction; }
    public double getEpisodeReward() { return episodeReward; }
    public int getEpisodeSteps() { return episodeSteps; }
    public int getCurrentX() { return currentX; }
    public int getCurrentY() { return currentY; }
    public boolean isAutoExecute() { return autoExecute; }
    public String getLastExecutedAction() { return lastExecutedAction; }
    public String getWalkToName() { return walkToName; }
    public Integer getWalkToX() { return walkToX; }
    public Integer getWalkToY() { return walkToY; }
    public long getBreakTimeRemaining() { return onBreak ? Math.max(0, (breakStartTime + breakDuration) - System.currentTimeMillis()) : 0; }
    public long getTimeUntilNextBreak() { return Math.max(0, nextBreakTime - System.currentTimeMillis()); }
    public String getLastResource() { return lastResource; }
    public Skill getTargetSkill() { return targetSkill; }
    public int getTargetLevel() { return targetLevel; }
}
