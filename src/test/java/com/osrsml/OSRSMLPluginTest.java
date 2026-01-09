package com.osrsml;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class OSRSMLPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(OSRSMLPlugin.class);
        RuneLite.main(args);
    }
}
