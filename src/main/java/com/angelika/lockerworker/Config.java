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

    public static final String CATEGORY_SOUNDS = "sounds";
    public static final String CATEGORY_COMBAT = "combat";

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

    // --- Sounds (v3) ---

    /** Play free-roaming ambient clips during ORBIT / IDLE_WANDER. */
    public static boolean soundFreeRoamingEnabled = true;

    /** Min silence ticks between free-roaming clips. */
    public static int freeRoamingMinSilenceTicks = 80;

    /** Max silence ticks between free-roaming clips. */
    public static int freeRoamingMaxSilenceTicks = 400;

    /** Play working set continuously while APPROACH_CLOSE / INSPECT_PAUSE. */
    public static boolean soundWorkingEnabled = true;

    /** Minimal silence between working clips while cycling (ticks). */
    public static int workingSilenceTicks = 2;

    /** Play interaction clip on normal (non-shift) right-click. */
    public static boolean soundInteractionEnabled = true;

    /** Cooldown between interaction sounds (ticks). */
    public static int interactionSoundCooldownTicks = 20;

    // --- Combat / aggressive (v3) ---

    /**
     * When false, locker right-click cannot enable aggressive mode (forced peaceful).
     * Default true.
     */
    public static boolean aggressiveModeAllowed = true;

    /** Melee damage dealt to hostile mobs in aggressive mode. Never applied to players. */
    public static float aggressiveAttackDamage = 1.0F;

    /** Horizontal range for acquiring hostile targets when aggressive. */
    public static float aggressiveTargetRange = 16.0F;

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

        // Sounds
        soundFreeRoamingEnabled = configuration.getBoolean(
            "soundFreeRoamingEnabled",
            CATEGORY_SOUNDS,
            soundFreeRoamingEnabled,
            "Play random free_roaming/*.ogg during daytime ORBIT / IDLE_WANDER.");

        freeRoamingMinSilenceTicks = configuration.getInt(
            "freeRoamingMinSilenceTicks",
            CATEGORY_SOUNDS,
            freeRoamingMinSilenceTicks,
            0,
            6000,
            "Minimum silence ticks between free-roaming ambient sounds.");

        freeRoamingMaxSilenceTicks = configuration.getInt(
            "freeRoamingMaxSilenceTicks",
            CATEGORY_SOUNDS,
            freeRoamingMaxSilenceTicks,
            0,
            12000,
            "Maximum silence ticks between free-roaming ambient sounds.");

        if (freeRoamingMaxSilenceTicks < freeRoamingMinSilenceTicks) {
            freeRoamingMaxSilenceTicks = freeRoamingMinSilenceTicks;
        }

        soundWorkingEnabled = configuration.getBoolean(
            "soundWorkingEnabled",
            CATEGORY_SOUNDS,
            soundWorkingEnabled,
            "Play working/*.ogg continuously while APPROACH_CLOSE / INSPECT_PAUSE.");

        workingSilenceTicks = configuration.getInt(
            "workingSilenceTicks",
            CATEGORY_SOUNDS,
            workingSilenceTicks,
            0,
            100,
            "Minimal silence ticks between working sound clips while cycling.");

        soundInteractionEnabled = configuration.getBoolean(
            "soundInteractionEnabled",
            CATEGORY_SOUNDS,
            soundInteractionEnabled,
            "Play interaction/*.ogg on normal right-click of the worker.");

        interactionSoundCooldownTicks = configuration.getInt(
            "interactionSoundCooldownTicks",
            CATEGORY_SOUNDS,
            interactionSoundCooldownTicks,
            0,
            200,
            "Cooldown ticks between interaction sounds.");

        // Combat
        aggressiveModeAllowed = configuration.getBoolean(
            "aggressiveModeAllowed",
            CATEGORY_COMBAT,
            aggressiveModeAllowed,
            "If false, locker right-click cannot enable aggressive mode (forced peaceful).");

        aggressiveAttackDamage = configuration.getFloat(
            "aggressiveAttackDamage",
            CATEGORY_COMBAT,
            aggressiveAttackDamage,
            0.0F,
            40.0F,
            "Damage dealt to hostile mobs (IMob/EntityMob) when aggressive. Never hits players. Default 1.0.");

        aggressiveTargetRange = configuration.getFloat(
            "aggressiveTargetRange",
            CATEGORY_COMBAT,
            aggressiveTargetRange,
            4.0F,
            48.0F,
            "Horizontal range to acquire hostile targets when aggressive.");

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
