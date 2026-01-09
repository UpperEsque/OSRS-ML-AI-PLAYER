package com.osrsml;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

/**
 * Helper class for walking/navigation
 */
public class WalkingHelper {
    
    // Minimap settings for fixed mode
    private static final int MINIMAP_CENTER_X = 643;
    private static final int MINIMAP_CENTER_Y = 83;
    private static final int PIXELS_PER_TILE = 4;
    private static final int MAX_MINIMAP_TILES = 12;
    
    /**
     * Calculate minimap click position for a target world coordinate
     * Returns int[2] = {screenX, screenY} or null if can't calculate
     */
    public static int[] getMinimapClickPos(Client client, int targetX, int targetY) {
        Player local = client.getLocalPlayer();
        if (local == null) return null;
        
        WorldPoint playerLoc = local.getWorldLocation();
        int playerX = playerLoc.getX();
        int playerY = playerLoc.getY();
        
        // Calculate delta
        int dx = targetX - playerX;
        int dy = targetY - playerY;
        
        // Calculate distance
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        // If too far, scale down to max minimap distance
        if (distance > MAX_MINIMAP_TILES) {
            double scale = MAX_MINIMAP_TILES / distance;
            dx = (int) Math.round(dx * scale);
            dy = (int) Math.round(dy * scale);
        }
        
        // Convert to screen coordinates
        // Note: minimap Y is inverted (north is up on minimap, but lower Y in game coords)
        int screenX = MINIMAP_CENTER_X + (dx * PIXELS_PER_TILE);
        int screenY = MINIMAP_CENTER_Y - (dy * PIXELS_PER_TILE);
        
        // Clamp to minimap bounds (approximate)
        screenX = Math.max(575, Math.min(715, screenX));
        screenY = Math.max(10, Math.min(160, screenY));
        
        return new int[]{screenX, screenY};
    }
    
    /**
     * Get distance between two world points
     */
    public static int getDistance(int x1, int y1, int x2, int y2) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        return dx + dy;  // Manhattan distance
    }
    
    /**
     * Check if player is near target
     */
    public static boolean isNearTarget(Client client, int targetX, int targetY, int threshold) {
        Player local = client.getLocalPlayer();
        if (local == null) return false;
        
        WorldPoint loc = local.getWorldLocation();
        return getDistance(loc.getX(), loc.getY(), targetX, targetY) <= threshold;
    }
}
