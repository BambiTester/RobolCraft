package com.angelika.lockerworker;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static String greeting = "Locker Worker ready";

    /** Radius (blocks) used when scanning for nearby GregTech machines. */
    public static int machineScanRadius = 16;

    /** How often (ticks) the worker re-scans for GT machines. Throttle for TPS. */
    public static int machineScanIntervalTicks = 40;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        greeting = configuration
            .getString("greeting", Configuration.CATEGORY_GENERAL, greeting, "Startup log greeting");
        machineScanRadius = configuration.getInt(
            "machineScanRadius",
            Configuration.CATEGORY_GENERAL,
            machineScanRadius,
            4,
            64,
            "Search radius for GregTech machines during day AI");
        machineScanIntervalTicks = configuration.getInt(
            "machineScanIntervalTicks",
            Configuration.CATEGORY_GENERAL,
            machineScanIntervalTicks,
            10,
            200,
            "Ticks between GT machine scans (higher = less TPS cost)");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
