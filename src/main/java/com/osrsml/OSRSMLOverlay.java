package com.osrsml;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class OSRSMLOverlay extends Overlay
{
    private final Client client;
    private final OSRSMLConfig config;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public OSRSMLOverlay(Client client, OSRSMLConfig config)
    {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        panelComponent.getChildren().clear();

        // Title
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("OSRS ML AI")
            .color(Color.GREEN)
            .build());

        // Check if logged in
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right("Not logged in")
                .rightColor(Color.RED)
                .build());
            return panelComponent.render(graphics);
        }

        // AI Status
        panelComponent.getChildren().add(LineComponent.builder()
            .left("AI Status:")
            .right(config.aiEnabled() ? "ACTIVE" : "DISABLED")
            .rightColor(config.aiEnabled() ? Color.GREEN : Color.YELLOW)
            .build());

        // Combat stats
        int attack = client.getBoostedSkillLevel(Skill.ATTACK);
        int strength = client.getBoostedSkillLevel(Skill.STRENGTH);
        int defence = client.getBoostedSkillLevel(Skill.DEFENCE);
        int hitpoints = client.getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

        panelComponent.getChildren().add(LineComponent.builder()
            .left("HP:")
            .right(hitpoints + "/" + maxHp)
            .rightColor(getHealthColor(hitpoints, maxHp))
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Combat:")
            .right(attack + "/" + strength + "/" + defence)
            .build());

        // Prayer
        int prayer = client.getBoostedSkillLevel(Skill.PRAYER);
        int maxPrayer = client.getRealSkillLevel(Skill.PRAYER);
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Prayer:")
            .right(prayer + "/" + maxPrayer)
            .rightColor(getPrayerColor(prayer, maxPrayer))
            .build());

        if (config.debugMode())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Debug:")
                .right("ON")
                .rightColor(Color.CYAN)
                .build());
        }

        return panelComponent.render(graphics);
    }

    private Color getHealthColor(int current, int max)
    {
        double ratio = (double) current / max;
        if (ratio > 0.5) return Color.GREEN;
        if (ratio > 0.25) return Color.YELLOW;
        return Color.RED;
    }

    private Color getPrayerColor(int current, int max)
    {
        double ratio = (double) current / max;
        if (ratio > 0.5) return Color.CYAN;
        if (ratio > 0.25) return Color.YELLOW;
        return Color.RED;
    }
}
