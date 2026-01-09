package com.osrsml;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.*;

public class MLOverlay extends Overlay {
    
    private final OSRSMLPlugin plugin;
    private final Client client;
    
    public MLOverlay(OSRSMLPlugin plugin, Client client) {
        this.plugin = plugin;
        this.client = client;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }
    
    @Override
    public Dimension render(Graphics2D g) {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return new Dimension(0, 0);
        }
        
        int y = 15;
        int width = 280;
        
        // Header
        g.setColor(new Color(0, 200, 100));
        g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
        g.drawString("=== OSRS ML AI Player ===", 10, y);
        y += 16;
        
        // Mode
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
        OSRSMLPlugin.Mode mode = plugin.getMode();
        switch (mode) {
            case RL_TRAINING:
                g.setColor(Color.ORANGE);
                g.drawString("Mode: RL TRAINING (GPT-Guided)", 10, y);
                break;
            case RL_INFERENCE:
                g.setColor(Color.CYAN);
                g.drawString("Mode: RL INFERENCE", 10, y);
                break;
            case GPT_ADVISOR:
                g.setColor(Color.YELLOW);
                g.drawString("Mode: GPT ADVISOR (no actions)", 10, y);
                break;
            case MANUAL:
                g.setColor(Color.GRAY);
                g.drawString("Mode: MANUAL", 10, y);
                break;
        }
        y += 14;
        
        // Auto-execute + Break
        if (plugin.isAutoExecute()) {
            if (plugin.isOnBreak()) {
                g.setColor(Color.MAGENTA);
                g.drawString("ON BREAK: " + (plugin.getBreakTimeRemaining()/1000) + "s", 10, y);
            } else {
                g.setColor(Color.GREEN);
                g.drawString("Auto: ON (break in " + (plugin.getTimeUntilNextBreak()/1000) + "s)", 10, y);
            }
        } else {
            g.setColor(Color.RED);
            g.drawString("Auto: OFF (press F8)", 10, y);
        }
        y += 15;
        
        // Location
        g.setColor(Color.CYAN);
        g.drawString("@ " + plugin.getCurrentArea(), 10, y);
        y += 12;
        g.setColor(Color.GRAY);
        g.drawString("(" + plugin.getCurrentX() + ", " + plugin.getCurrentY() + ")", 10, y);
        y += 14;
        
        // GPT Goal section
        g.setColor(new Color(255, 215, 0)); // Gold
        g.drawString("--- GPT Goal ---", 10, y);
        y += 13;
        
        g.setColor(Color.WHITE);
        String task = plugin.getCurrentTask();
        // Word wrap
        int maxChars = 40;
        int lines = 0;
        while (task.length() > 0 && lines < 3) {
            int len = Math.min(maxChars, task.length());
            // Try to break at space
            if (len < task.length()) {
                int spaceIdx = task.lastIndexOf(' ', len);
                if (spaceIdx > 10) len = spaceIdx;
            }
            g.drawString(task.substring(0, len).trim(), 10, y);
            task = task.substring(len).trim();
            y += 12;
            lines++;
        }
        y += 4;
        
        // Activity
        g.setColor(new Color(100, 200, 255));
        g.drawString("--- Activity ---", 10, y);
        y += 13;
        
        String activity = plugin.getCurrentActivity();
        g.setColor(activity.equals("Idle") ? Color.GRAY : Color.GREEN);
        String actStr = activity;
        if (!plugin.getLastResource().isEmpty() && !activity.equals("Idle")) {
            actStr += ": " + plugin.getLastResource();
        }
        if (plugin.getResourcesGathered() > 0) {
            actStr += " (x" + plugin.getResourcesGathered() + ")";
        }
        g.drawString(actStr, 10, y);
        y += 13;
        
        // Last action
        g.setColor(new Color(255, 180, 100));
        String lastAction = plugin.getLastExecutedAction();
        if (lastAction.length() > 38) lastAction = lastAction.substring(0, 38);
        g.drawString("Action: " + lastAction, 10, y);
        y += 14;
        
        // Target progress
        Skill targetSkill = plugin.getTargetSkill();
        if (targetSkill != null) {
            int curr = client.getRealSkillLevel(targetSkill);
            int target = plugin.getTargetLevel();
            g.setColor(Color.YELLOW);
            g.drawString("Target: " + targetSkill.getName() + " " + curr + "/" + target, 10, y);
            y += 11;
            
            int barW = 150;
            double pct = Math.min(1.0, (double) curr / target);
            g.setColor(Color.DARK_GRAY);
            g.fillRect(10, y, barW, 8);
            g.setColor(curr >= target ? Color.GREEN : new Color(255, 165, 0));
            g.fillRect(10, y, (int)(pct * barW), 8);
            y += 13;
        }
        
        // RL Stats
        if (mode == OSRSMLPlugin.Mode.RL_TRAINING || mode == OSRSMLPlugin.Mode.RL_INFERENCE) {
            g.setColor(Color.MAGENTA);
            g.drawString("--- RL Stats ---", 10, y);
            y += 12;
            g.setColor(Color.WHITE);
            g.drawString("Steps: " + plugin.getEpisodeSteps() + " | Reward: " + 
                        String.format("%.1f", plugin.getEpisodeReward()), 10, y);
            y += 11;
            int[] action = plugin.getLastRLAction();
            g.drawString("RL: [" + action[0] + "," + action[1] + "," + action[2] + "," + action[3] + "]", 10, y);
            y += 12;
        }
        
        // Hotkeys
        y += 3;
        g.setColor(new Color(80, 80, 80));
        g.setFont(g.getFont().deriveFont(Font.ITALIC, 9f));
        g.drawString("F5:Mode  F6:Reset  F7:Task  F8:Auto", 10, y);
        
        return new Dimension(width, y + 10);
    }
}
