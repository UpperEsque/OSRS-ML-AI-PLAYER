package com.osrsml;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("osrsml")
public interface OSRSMLConfig extends Config
{
    @ConfigItem(
        keyName = "showOverlay",
        name = "Show Overlay",
        description = "Display the AI status overlay"
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "aiEnabled",
        name = "AI Enabled",
        description = "Enable AI decision making"
    )
    default boolean aiEnabled()
    {
        return false;
    }

    @ConfigItem(
        keyName = "debugMode",
        name = "Debug Mode",
        description = "Show detailed debug information"
    )
    default boolean debugMode()
    {
        return false;
    }
}
