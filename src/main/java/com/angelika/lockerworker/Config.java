package com.angelika.lockerworker;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * Forge config loaded from {@code config/lockerworker.cfg} (modid-based suggested file).
 *
 * <p>
 * Most values are read once at preInit. In-game Mods → Config (GuiFactory) can edit the
 * file; {@link #synchronizeConfiguration} is re-run on config-changed so static fields
 * update, but already-spawned workers keep their attribute base until respawn / world
 * reload. Prefer a restart after changing {@link #walkingSpeed}.
 */
public class Config {

    public static String greeting = "Locker Worker ready";

    /**
     * Radius (blocks) used when scanning for nearby GregTech machines from the worker.
     * Clamped 4–64. Default 16.
     */
    public static int machineScanRadius = 16;

    /** How often (ticks) the worker re-scans for GT machines. Throttle for TPS. */
    public static int machineScanIntervalTicks = 40;

    /**
     * Farthest the worker may roam from the home locker (Chebyshev/horizontal blocks).
     * Beyond this, day AI paths back toward the locker and ignores farther machines.
     * Default 48 — roughly free-roam feel with scan radius 16 and machine hopping.
     * Clamped 8–128.
     */
    public static int maxDistanceFromLocker = 48;

    /**
     * Worker movement speed.
     * <ul>
     * <li>Units: Forge {@code SharedMonsterAttributes.movementSpeed} base value
     * (vanilla villager-ish ~0.3).</li>
     * <li>Pathfinding: {@code tryMoveToXYZ(..., getPathSpeed())} uses
     * {@code walkingSpeed * 2.0} (so default path speed 0.6, matching prior day AI).
     * Night return uses the same path speed.</li>
     * </ul>
     * Range 0.05–1.0. Changing mid-game updates new path calls; attribute base on
     * already-spawned entities refreshes on next apply / respawn — restart recommended.
     */
    public static double walkingSpeed = 0.3D;

    /** Path speed multiplier applied to {@link #walkingSpeed} for navigator moves. */
    public static final double PATH_SPEED_FACTOR = 2.0D;

    private static Configuration configuration;
    private static File configFile;

    public static Configuration getConfiguration() {
        return configuration;
    }

    /** Navigator speed for tryMoveToXYZ — walkingSpeed * {@link #PATH_SPEED_FACTOR}. */
    public static double getPathSpeed() {
        return walkingSpeed * PATH_SPEED_FACTOR;
    }

    public static void synchronizeConfiguration(File file) {
        configFile = file;
        configuration = new Configuration(file);

        greeting = configuration
            .getString("greeting", Configuration.CATEGORY_GENERAL, greeting, "Startup log greeting");

        machineScanRadius = configuration.getInt(
            "machineScanRadius",
            Configuration.CATEGORY_GENERAL,
            machineScanRadius,
            4,
            64,
            "How far from the worker GT processing machines can be detected (blocks). Default 16.");

        machineScanIntervalTicks = configuration.getInt(
            "machineScanIntervalTicks",
            Configuration.CATEGORY_GENERAL,
            machineScanIntervalTicks,
            10,
            200,
            "Ticks between GT machine scans (higher = less TPS cost)");

        maxDistanceFromLocker = configuration.getInt(
            "maxDistanceFromLocker",
            Configuration.CATEGORY_GENERAL,
            maxDistanceFromLocker,
            8,
            128,
            "Farthest the worker may roam from the home locker (blocks). "
                + "If beyond, day AI paths back toward the locker and ignores farther machines. "
                + "Default 48 (free-roam feel with scan radius 16).");

        walkingSpeed = configuration.getFloat(
            "walkingSpeed",
            Configuration.CATEGORY_GENERAL,
            (float) walkingSpeed,
            0.05F,
            1.0F,
            "Movement speed while working/returning. "
                + "Units: SharedMonsterAttributes.movementSpeed base (default 0.3). "
                + "Navigator tryMoveToXYZ uses walkingSpeed * 2.0 (default path 0.6). "
                + "Restart recommended after changing.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    /** Re-read after in-game GuiConfig save (same file as preInit). */
    public static void reload() {
        if (configFile != null) {
            synchronizeConfiguration(configFile);
        }
    }
}
