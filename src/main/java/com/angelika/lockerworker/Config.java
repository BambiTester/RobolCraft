package com.angelika.lockerworker;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * Forge config loaded from {@code config/lockerworker.cfg} (modid-based suggested file).
 *
 * <p>
 * Load once via {@link #load(File)} (keeps a single {@link Configuration} instance). In-game
 * Mods → Config (GuiConfig) edits Properties on that instance; on Done, {@link #save()} must
 * persist first, then {@link #syncStaticFromConfig()} refreshes static fields — never
 * {@code new Configuration(file)} before save, or disk overwrites Gui edits.
 *
 * <p>
 * Already-spawned workers keep attribute base until respawn / world reload. Prefer a restart
 * after changing {@link #walkingSpeed}.
 */
public class Config {

    public static final String CATEGORY_SOUNDS = "sounds";
    public static final String CATEGORY_COMBAT = "combat";

    public static String greeting = "Locker Worker ready";

    /**
     * Radius (blocks) used when scanning for nearby GregTech machines from the worker.
     * Clamped 4–256. Default 100.
     */
    public static int machineScanRadius = 100;

    /** How often (ticks) the worker re-scans for GT machines. Throttle for TPS. */
    public static int machineScanIntervalTicks = 40;

    /**
     * Farthest the worker may roam from the home locker (Chebyshev/horizontal blocks).
     * Beyond this, day AI paths back toward the locker and ignores farther machines.
     * Default 150. Clamped 0–256. {@code 0} = unlimited day roam (night/forced return
     * never uses this leash).
     */
    public static int maxDistanceFromLocker = 150;

    /**
     * Radius (blocks) from the worker used when searching for a trashcan during break.
     * Prefer nearest trashcan to the worker within this radius. Default 150. Clamped 8–256.
     */
    public static int trashcanSearchRadius = 150;

    /**
     * Minimum ticks between random machine switches while attending a machine.
     * Default 400 (~20s). Clamped ≥40; never above {@link #machineSwitchMaxTicks}.
     */
    public static int machineSwitchMinTicks = 400;

    /**
     * Maximum ticks between random machine switches. Default 1800 (~90s).
     * Clamped ≥ {@link #machineSwitchMinTicks}.
     */
    public static int machineSwitchMaxTicks = 1800;

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

    /**
     * Fallback clip length (ticks) when 1.7.10 cannot report ogg duration.
     * Client stops/advances after this many ticks even if still marked playing.
     * Default 40 (= 2 seconds). Used for exclusive sequential playback.
     */
    public static int defaultClipLengthTicks = 40;

    /**
     * Master multiplier for worker moving sounds (all ambient + one-shot modes including break/day/smoking).
     * Default 1.0. Range 0.0–2.0.
     */
    public static float soundVolume = 1.0F;

    /**
     * Max hearing distance (blocks) for worker moving sounds. Default 16.
     * With AttenuationType.NONE + distance fade in WorkerMovingSound.
     * Clamped 4–64.
     */
    public static int soundHearDistance = 16;

    // --- Combat / aggressive (v3) ---

    /**
     * When false, locker right-click cannot enable aggressive mode (forced peaceful).
     * Default true.
     */
    public static boolean aggressiveModeAllowed = true;

    /** Melee damage dealt to hostile mobs in aggressive mode. Never applied to workers. */
    public static float aggressiveAttackDamage = 1.0F;

    /**
     * How far (blocks) an aggressive worker senses hostiles for target selection.
     * Replaces legacy {@code aggressiveTargetRange}. Default 10.
     */
    public static float hostileDetectRadius = 10.0F;

    /**
     * When true, aggressive workers may also target players. Default false —
     * players are never attacked unless this is enabled.
     */
    public static boolean attackPlayers = false;

    /**
     * Radius (blocks) for pack aggro: when one aggressive worker targets a hostile,
     * nearby aggressive workers within this range also adopt that target.
     * Defaults to the same value as {@link #hostileDetectRadius} (10).
     */
    public static float packAggroRadius = 10.0F;

    public static final String CATEGORY_SUPERVISOR = "supervisor";

    /**
     * Ticks between supervisor machine switches after an inspection.
     * Shorter default than worker machineSwitchMinTicks. Default 200 (~10s).
     */
    public static int supervisorMachineSwitchInterval = 200;

    /**
     * How far (blocks) the supervisor seeks a player to auto-deliver reports. Default 100.
     */
    public static int reportPlayerRadius = 100;

    /**
     * How long (ticks) the supervisor follows the player after delivering a report burst.
     * Default 60 (~3s).
     */
    public static int reportFollowTicks = 60;

    private static Configuration configuration;
    private static File configFile;

    public static Configuration getConfiguration() {
        return configuration;
    }

    public static File getConfigFile() {
        return configFile;
    }

    /** Navigator speed for tryMoveToXYZ — walkingSpeed * {@link #PATH_SPEED_FACTOR}. */
    public static double getPathSpeed() {
        return walkingSpeed * PATH_SPEED_FACTOR;
    }

    /** Effective pack-aggro radius (never below a tiny epsilon). */
    public static float getPackAggroRadius() {
        return packAggroRadius > 0.0F ? packAggroRadius : hostileDetectRadius;
    }

    /**
     * Load config once from disk and keep the {@link Configuration} instance for GuiConfig.
     * Call from preInit only.
     */
    public static void load(File file) {
        configFile = file;
        configuration = new Configuration(file);
        syncStaticFromConfig();
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    /**
     * Persist the current {@link Configuration} (including in-memory GuiConfig Property
     * edits) to disk. Must run before any re-read from file after Gui Done.
     */
    public static void save() {
        if (configuration != null) {
            configuration.save();
        }
    }

    /**
     * Read property values from the existing {@link Configuration} into static fields.
     * Does not create a new Configuration or re-read disk.
     */
    public static void syncStaticFromConfig() {
        if (configuration == null) {
            return;
        }

        greeting = configuration
            .getString("greeting", Configuration.CATEGORY_GENERAL, greeting, "Startup log greeting");

        machineScanRadius = configuration.getInt(
            "machineScanRadius",
            Configuration.CATEGORY_GENERAL,
            machineScanRadius,
            4,
            256,
            "How far from the worker GT processing machines can be detected (blocks). Default 100.");

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
            0,
            256,
            "Farthest the worker may roam from the home locker (blocks). "
                + "If beyond, day AI paths back toward the locker and ignores farther machines. "
                + "0 = unlimited day roam (does not gate night/forced return). Default 150.");

        trashcanSearchRadius = configuration.getInt(
            "trashcanSearchRadius",
            Configuration.CATEGORY_GENERAL,
            trashcanSearchRadius,
            8,
            256,
            "How far from the worker to search for a trashcan during break (blocks). "
                + "Nearest trashcan to the worker within this radius is preferred. Default 150.");

        machineSwitchMinTicks = configuration.getInt(
            "machineSwitchMinTicks",
            Configuration.CATEGORY_GENERAL,
            machineSwitchMinTicks,
            40,
            12000,
            "Minimum ticks between random machine switches (day AI). Default 400 (~20s).");

        machineSwitchMaxTicks = configuration.getInt(
            "machineSwitchMaxTicks",
            Configuration.CATEGORY_GENERAL,
            machineSwitchMaxTicks,
            40,
            24000,
            "Maximum ticks between random machine switches (day AI). Default 1800 (~90s). "
                + "Must be >= machineSwitchMinTicks.");

        if (machineSwitchMaxTicks < machineSwitchMinTicks) {
            machineSwitchMaxTicks = machineSwitchMinTicks;
        }

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

        defaultClipLengthTicks = configuration.getInt(
            "defaultClipLengthTicks",
            CATEGORY_SOUNDS,
            defaultClipLengthTicks,
            5,
            6000,
            "Fallback max ticks for one worker sound clip (1.7.10 cannot query ogg length). "
                + "Client exclusive playback waits for real end via SoundHandler, else this estimate. "
                + "Default 40 (2s). Prevents overlapping clips on the same worker.");

        soundVolume = configuration.getFloat(
            "soundVolume",
            CATEGORY_SOUNDS,
            soundVolume,
            0.0F,
            2.0F,
            "Master volume multiplier for all worker sounds (incl. breaktime/day/smoking). "
                + "Default 1.0. Range 0.0–2.0.");

        soundHearDistance = configuration.getInt(
            "soundHearDistance",
            CATEGORY_SOUNDS,
            soundHearDistance,
            4,
            64,
            "Max hearing distance in blocks for worker moving sounds. Default 16. "
                + "Uses custom distance fade (vanilla LINEAR ~16 is bypassed).");

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
            "Damage dealt to hostile mobs (IMob/EntityMob) when aggressive. "
                + "Never hits workers. Players only if attackPlayers=true. Default 1.0.");

        // Prefer single key hostileDetectRadius (default 10). Migrate legacy key if present.
        float legacyRangeDefault = 10.0F;
        if (configuration.hasKey(CATEGORY_COMBAT, "aggressiveTargetRange")
            && !configuration.hasKey(CATEGORY_COMBAT, "hostileDetectRadius")) {
            Property legacy = configuration
                .get(CATEGORY_COMBAT, "aggressiveTargetRange", 16.0D, "DEPRECATED — use hostileDetectRadius");
            legacyRangeDefault = (float) legacy.getDouble(16.0D);
            configuration.getCategory(CATEGORY_COMBAT)
                .remove("aggressiveTargetRange");
        } else if (configuration.hasKey(CATEGORY_COMBAT, "aggressiveTargetRange")) {
            configuration.getCategory(CATEGORY_COMBAT)
                .remove("aggressiveTargetRange");
        }

        hostileDetectRadius = configuration.getFloat(
            "hostileDetectRadius",
            CATEGORY_COMBAT,
            legacyRangeDefault,
            4.0F,
            48.0F,
            "How far (blocks) an aggressive worker senses hostiles for attack target selection. Default 10.");

        attackPlayers = configuration.getBoolean(
            "attackPlayers",
            CATEGORY_COMBAT,
            attackPlayers,
            "If true, aggressive workers may target players. Default false (never attack players).");

        packAggroRadius = configuration.getFloat(
            "packAggroRadius",
            CATEGORY_COMBAT,
            packAggroRadius,
            0.0F,
            64.0F,
            "When one aggressive worker targets a hostile, nearby aggressive workers within this "
                + "radius (blocks) also set that entity as attack target (wolf-like pack aggro). "
                + "Same locker not required. 0 = use hostileDetectRadius. Default 10.");
        // Supervisor
        supervisorMachineSwitchInterval = configuration.getInt(
            "supervisorMachineSwitchInterval",
            CATEGORY_SUPERVISOR,
            supervisorMachineSwitchInterval,
            40,
            12000,
            "Ticks between Shift Supervisor machine switches after inspection. "
                + "Shorter than worker defaults. Default 200 (~10s).");

        reportPlayerRadius = configuration.getInt(
            "reportPlayerRadius",
            CATEGORY_SUPERVISOR,
            reportPlayerRadius,
            8,
            256,
            "How far (blocks) the supervisor seeks a player to auto-deliver reports. Default 100.");

        reportFollowTicks = configuration.getInt(
            "reportFollowTicks",
            CATEGORY_SUPERVISOR,
            reportFollowTicks,
            10,
            600,
            "Ticks the supervisor follows the player after a report burst. Default 60 (~3s).");

    }

    /**
     * @deprecated Use {@link #load(File)} at preInit. Kept for callers that still pass the
     *             suggested config file.
     */
    public static void synchronizeConfiguration(File file) {
        load(file);
    }

    /**
     * After GuiConfig Done: persist in-memory Property edits, then refresh statics from the
     * same Configuration instance (do not {@code new Configuration(file)}).
     */
    public static void reload() {
        save();
        syncStaticFromConfig();
    }
}
