package com.angelika.lockerworker;

import java.io.File;
import java.util.Map;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * Server/gameplay Forge config: {@code config/robolcraft.cfg}.
 *
 * <p>
 * Holds scan radius, leash, combat, supervisor timings, sound ENABLE flags, and silence
 * tick defaults (world feel). Volume and hear distance live in
 * {@code config/robolcraft-client.cfg} (client-local; see ClientConfig).
 *
 * <p>
 * Load once via {@link #loadOrMigrate(File)} (keeps a single {@link Configuration} instance). In-game
 * Mods → Config (GuiConfig) edits Properties on that instance; on Done, {@link #save()} must
 * persist first, then {@link #syncStaticFromConfig()} refreshes static fields — never
 * {@code new Configuration(file)} before save, or disk overwrites Gui edits.
 *
 * <p>
 * Already-spawned workers keep attribute base until respawn / world reload unless
 * {@link #applyToLivingEntities()} runs after config Done. Prefer a restart after changing
 * {@link #walkingSpeed} on dedicated servers if entities are unloaded.
 */
public class Config {

    /** Server-side sound enables + silence gaps (not volume). */
    public static final String CATEGORY_SOUNDS = "sounds_server";

    public static final String CATEGORY_COMBAT = "combat";

    public static final String CATEGORY_SUPERVISOR = "supervisor";

    private static final String LEGACY_CATEGORY_SOUNDS = "sounds";

    /** Pre-Angelika-patch free-roam silence defaults (1.0.0 ship) — migrate only when exact match. */
    private static final int OLD_FREE_ROAM_MIN = 600;
    private static final int OLD_FREE_ROAM_MAX = 2400;
    /** Pre-Angelika-patch breaktime silence defaults — migrate only when exact match. */
    private static final int OLD_BREAKTIME_MIN = 200;
    private static final int OLD_BREAKTIME_MAX = 600;
    /** Pre-Angelika / early walkingSpeed default (villager-ish 0.3) — migrate only when exact match. */
    private static final float OLD_WALKING_SPEED = 0.3F;
    /** 1.0.0 "make it 1" walkingSpeed default — migrate only when exact match. */
    private static final float OLD_WALKING_SPEED_V100 = 1.0F;
    /** 1.0.1 / 1.0.2 player-walk default — migrate only when exact match. */
    private static final float OLD_WALKING_SPEED_V102 = 0.1F;
    /** Pre-v26 working silence — migrate only when file still has this exact value. */
    private static final int OLD_WORKING_SILENCE = 2;
    /** Pre-1.0.0 working silence single-value default — migrate only when exact match. */
    private static final int OLD_WORKING_SILENCE_V26 = 40;
    /** Pre-1.0.0 aggressive damage default — migrate only when exact match. */
    private static final float OLD_AGGRESSIVE_DAMAGE = 1.0F;
    /** 1.0.0–1.0.4 working silence defaults — migrate exact pair to 40/80 once. */
    private static final int OLD_WORKING_MIN_V104 = 100;
    private static final int OLD_WORKING_MAX_V104 = 200;

    /** Pre-v28 trashcan search default — migrate only when file still has this exact value. */
    private static final int OLD_TRASHCAN_SEARCH = 150;

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
     * Prefer nearest trashcan to the worker within this radius. Default 1000.
     * Clamped 0–9999; {@code 0} = unlimited (any registered can in the dimension).
     * Search uses {@link com.angelika.lockerworker.util.TrashcanRegistry} (not a cube scan).
     */
    public static int trashcanSearchRadius = 1000;

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
     * Worker / supervisor movement speed (attribute base).
     * <ul>
     * <li>Units: Forge {@code SharedMonsterAttributes.movementSpeed} base value.
     * Default {@code 0.32}. Prior defaults: 0.1 (1.0.1–1.0.2 player walk), 1.0 (1.0.0),
     * 0.3 (early). Not sprint.</li>
     * <li>Pathfinding: {@code EntityMoveHelper} sets
     * {@code AIMoveSpeed = getPathSpeed() * attribute}. {@link #getPathSpeed()} returns
     * {@link #PATH_SPEED_FACTOR} (1.0 = 100% of attribute), so default effective move
     * is {@code 1.0 * 0.32 = 0.32}.</li>
     * </ul>
     * Range 0.05–1.0. Changing mid-game updates new path calls; attribute base on
     * already-spawned entities refreshes on next apply / respawn — restart recommended.
     */
    public static double walkingSpeed = 0.32D;

    /**
     * Navigator speed argument for {@code tryMoveToXYZ} / {@code tryMoveToEntityLiving}.
     * Vanilla MoveHelper multiplies this by the movementSpeed attribute. {@code 1.0} =
     * full attribute speed (default attribute 0.32).
     */
    public static final double PATH_SPEED_FACTOR = 1.0D;

    // --- Sounds server (enables + silence; volume is client-local) ---

    /** Play free-roaming ambient clips during ORBIT / IDLE_WANDER. */
    public static boolean soundFreeRoamingEnabled = true;

    /**
     * Min silence ticks between free-roaming / afterwork / waiting_for_bed ambient clips.
     * Default 300 (~15s). Was 600 (~30s) before Angelika density patch.
     */
    public static int freeRoamingMinSilenceTicks = 300;

    /**
     * Max silence ticks between free-roaming / afterwork / waiting_for_bed ambient clips.
     * Default 1200 (~60s). Was 2400 (~2 min) before Angelika density patch.
     */
    public static int freeRoamingMaxSilenceTicks = 1200;

    /**
     * Min silence ticks between breaktime ambient ("talk") clips.
     * Separate from free-roam — default 100 (~5s). Was 200 (~10s). 20 ticks = 1 second.
     */
    public static int breaktimeMinSilenceTicks = 100;

    /**
     * Max silence ticks between breaktime ambient ("talk") clips.
     * Separate from free-roam — default 300 (~15s). Was 600 (~30s). 20 ticks = 1 second.
     */
    public static int breaktimeMaxSilenceTicks = 300;

    /** Play working set continuously while APPROACH_CLOSE / INSPECT_PAUSE. */
    public static boolean soundWorkingEnabled = true;

    /**
     * Min silence ticks between working clips while at machine.
     * Default 40 (~2s). Was 100 (~5s) in 1.0.0–1.0.4.
     */
    public static int workingMinSilenceTicks = 40;

    /**
     * Max silence ticks between working clips while at machine.
     * Default 80 (~4s). Was 200 (~10s) in 1.0.0–1.0.4. Random gap rolled in
     * [{@link #workingMinSilenceTicks}, this].
     */
    public static int workingMaxSilenceTicks = 80;

    /** Play interaction clip on normal (non-shift) right-click. */
    public static boolean soundInteractionEnabled = true;

    /** Cooldown between interaction sounds (ticks). Default 20 (~1s). */
    public static int interactionSoundCooldownTicks = 20;

    // --- Combat / aggressive ---

    /**
     * When false, locker right-click cannot enable aggressive mode (forced peaceful).
     * Default true.
     */
    public static boolean aggressiveModeAllowed = true;

    /** Melee damage dealt to hostile mobs in aggressive mode. Never applied to workers. */
    public static float aggressiveAttackDamage = 3.0F;

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

    /**
     * How far (blocks) workers/supervisors search for a medkit when under 50% health.
     * Default 200.
     */
    public static int medkitSearchRadius = 200;

    /**
     * Max distance (blocks) from medkit center to regenerate. Default 2.
     */
    public static double medkitHealRange = 2.0D;

    private static Configuration configuration;
    private static File configFile;

    /** One-shot legacy default rewrites already applied for this cfg file. */
    private static boolean migratedDefaultsDone;

    public static final String SERVER_CFG_NAME = "robolcraft.cfg";
    public static final String LEGACY_SERVER_CFG_NAME = "lockerworker.cfg";

    public static Configuration getConfiguration() {
        return configuration;
    }

    public static File getConfigFile() {
        return configFile;
    }

    /**
     * Navigator speed for tryMoveToXYZ / tryMoveToEntityLiving.
     * Returns {@link #PATH_SPEED_FACTOR} (not {@code walkingSpeed * factor}): MoveHelper
     * already multiplies by the movementSpeed attribute ({@link #walkingSpeed}).
     */
    public static double getPathSpeed() {
        return PATH_SPEED_FACTOR;
    }

    /** Effective pack-aggro radius (never below a tiny epsilon). */
    public static float getPackAggroRadius() {
        return packAggroRadius > 0.0F ? packAggroRadius : hostileDetectRadius;
    }

    /**
     * Fixed broadcast volume for server {@code playSoundAtEntity} / {@code playSoundEffect}.
     * Slightly above 1.0 (~+25%) so one-shots read clearer; per-player loudness is still
     * {@code ClientConfig.soundVolume} on the client moving-sound path.
     */
    public static float getBroadcastSoundVolume() {
        return 1.25F;
    }

    /**
     * Load {@code robolcraft.cfg}; if missing, copy once from legacy {@code lockerworker.cfg}.
     */
    public static void loadOrMigrate(File configDir) {
        File neu = new File(configDir, SERVER_CFG_NAME);
        File legacy = new File(configDir, LEGACY_SERVER_CFG_NAME);
        if (!neu.exists() && legacy.exists()) {
            try {
                java.nio.file.Files.copy(legacy.toPath(), neu.toPath());
                LockerWorkerMod.LOG.info("Migrated {} → {}", LEGACY_SERVER_CFG_NAME, SERVER_CFG_NAME);
            } catch (Throwable t) {
                LockerWorkerMod.LOG.warn("Could not copy legacy cfg: {}", t.toString());
            }
        }
        load(neu);
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

        migrateLegacySoundsCategory();

        Property migratedFlag = configuration.get(
            Configuration.CATEGORY_GENERAL,
            "migratedDefaultsV102",
            false,
            "INTERNAL — set true after one-shot legacy default rewrites. Do not edit.");
        migratedDefaultsDone = migratedFlag.getBoolean(false);

        configuration.setCategoryComment(
            Configuration.CATEGORY_GENERAL,
            "Gameplay: machine scan, leash, trashcan search, machine-switch pacing, walk speed.\n"
                + "These are SERVER settings (shared world feel). Audio volume is NOT here —\n"
                + "see robolcraft-client.cfg / client_audio.");

        configuration.setCategoryComment(
            CATEGORY_SOUNDS,
            "SERVER sound enables and silence gaps (world feel).\n"
                + "Clips play fully to natural end (OpenAL / SoundHandler).\n"
                + "Volume and hear distance are CLIENT-LOCAL in robolcraft-client.cfg.\n"
                + "One-shots (day start/end, break, smoking, clothes) are event-driven — not gated by these gaps.\n"
                + "20 ticks = 1 second.");

        configuration.setCategoryComment(CATEGORY_COMBAT, "Aggressive mode and pack-aggro combat settings (SERVER).");

        configuration.setCategoryComment(
            CATEGORY_SUPERVISOR,
            "Shift Supervisor timings: machine switch interval, report seek radius, follow duration (SERVER).");

        // --- general (logical order) ---
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
            "Ticks between GT machine scans (higher = less TPS cost). Default 40 (~2s).");

        maxDistanceFromLocker = configuration.getInt(
            "maxDistanceFromLocker",
            Configuration.CATEGORY_GENERAL,
            maxDistanceFromLocker,
            0,
            256,
            "Default day work leash for newly placed lockers (blocks). "
                + "Each locker stores its own MaxWorkDistance (GUI); this config is the place default / "
                + "migration fallback. 0 = unlimited day roam (does not gate night/forced return / break). "
                + "Default 150.");

        Property trashRadiusProp = configuration.get(
            Configuration.CATEGORY_GENERAL,
            "trashcanSearchRadius",
            trashcanSearchRadius,
            "How far from the worker to search for a trashcan during break (blocks). "
                + "Nearest registered trashcan to the worker within this radius is preferred. "
                + "0 = unlimited (any can in the dimension). Default 1000. Was 150 before v28.",
            0,
            9999);
        if (trashRadiusProp.getInt() == OLD_TRASHCAN_SEARCH && !migratedDefaultsDone) {
            trashRadiusProp.set(1000);
        }
        trashcanSearchRadius = trashRadiusProp.getInt();

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

        Property walkProp = configuration.get(
            Configuration.CATEGORY_GENERAL,
            "walkingSpeed",
            0.32D,
            "Movement speed while working/returning. " + "Units: SharedMonsterAttributes.movementSpeed base. "
                + "Default 0.32. Was 0.1 in 1.0.1–1.0.2, 1.0 in 1.0.0, 0.3 earlier. "
                + "Navigator uses PATH_SPEED_FACTOR 1.0 × attribute (effective 0.32). "
                + "Range 0.05–1.0. Restart recommended after changing.",
            0.05D,
            1.0D);
        float walkVal = (float) walkProp.getDouble(0.32D);
        // Migrate exact old defaults only once — leave intentional custom values alone.
        if (!migratedDefaultsDone && (walkVal == OLD_WALKING_SPEED || walkVal == OLD_WALKING_SPEED_V100)) {
            walkProp.set(0.32D);
            walkVal = 0.32F;
        }
        // 1.0.3: exact 0.1 (1.0.1/1.0.2 default) → 0.32 once, even if V102 migrations already ran.
        Property walk032Flag = configuration.get(
            Configuration.CATEGORY_GENERAL,
            "migratedWalkSpeedV103",
            false,
            "INTERNAL — set true after migrating walkingSpeed 0.1 → 0.32. Do not edit.");
        if (!walk032Flag.getBoolean(false) && walkVal == OLD_WALKING_SPEED_V102) {
            walkProp.set(0.32D);
            walkVal = 0.32F;
            walk032Flag.set(true);
        } else if (!walk032Flag.getBoolean(false)) {
            walk032Flag.set(true);
        }
        if (walkVal < 0.05F) {
            walkVal = 0.05F;
        }
        if (walkVal > 1.0F) {
            walkVal = 1.0F;
        }
        walkingSpeed = walkVal;

        // 1.0.6: strip deprecated greeting key if present
        if (configuration.hasKey(Configuration.CATEGORY_GENERAL, "greeting")) {
            configuration.getCategory(Configuration.CATEGORY_GENERAL)
                .remove("greeting");
        }

        // --- sounds_server ---
        soundFreeRoamingEnabled = configuration.getBoolean(
            "soundFreeRoamingEnabled",
            CATEGORY_SOUNDS,
            soundFreeRoamingEnabled,
            "Play random free_roaming/*.ogg during daytime ORBIT / IDLE_WANDER.");

        Property freeMinProp = configuration.get(
            CATEGORY_SOUNDS,
            "freeRoamingMinSilenceTicks",
            freeRoamingMinSilenceTicks,
            "Silence between free-roaming / afterwork / waiting_for_bed ambient clips — MINIMUM "
                + "(ticks; 20 ticks = 1 second). Default 300 (~15s). Was 600 (~30s).");
        if (freeMinProp.getInt() == OLD_FREE_ROAM_MIN && !migratedDefaultsDone) {
            freeMinProp.set(300);
        }
        freeRoamingMinSilenceTicks = freeMinProp.getInt();

        Property freeMaxProp = configuration.get(
            CATEGORY_SOUNDS,
            "freeRoamingMaxSilenceTicks",
            freeRoamingMaxSilenceTicks,
            "Silence between free-roaming / afterwork / waiting_for_bed ambient clips — MAXIMUM "
                + "(ticks; 20 ticks = 1 second). Default 1200 (~60s). Was 2400 (~2 min).");
        if (freeMaxProp.getInt() == OLD_FREE_ROAM_MAX && !migratedDefaultsDone) {
            freeMaxProp.set(1200);
        }
        freeRoamingMaxSilenceTicks = freeMaxProp.getInt();

        if (freeRoamingMaxSilenceTicks < freeRoamingMinSilenceTicks) {
            freeRoamingMaxSilenceTicks = freeRoamingMinSilenceTicks;
        }

        Property breakMinProp = configuration.get(
            CATEGORY_SOUNDS,
            "breaktimeMinSilenceTicks",
            breaktimeMinSilenceTicks,
            "Silence between breaktime ambient talk clips — MINIMUM "
                + "(ticks; 20 ticks = 1 second). Default 100 (~5s). Was 200 (~10s). "
                + "Do not reuse free-roam gap.");
        if (breakMinProp.getInt() == OLD_BREAKTIME_MIN && !migratedDefaultsDone) {
            breakMinProp.set(100);
        }
        breaktimeMinSilenceTicks = breakMinProp.getInt();

        Property breakMaxProp = configuration.get(
            CATEGORY_SOUNDS,
            "breaktimeMaxSilenceTicks",
            breaktimeMaxSilenceTicks,
            "Silence between breaktime ambient talk clips — MAXIMUM "
                + "(ticks; 20 ticks = 1 second). Default 300 (~15s). Was 600 (~30s). "
                + "Do not reuse free-roam gap.");
        if (breakMaxProp.getInt() == OLD_BREAKTIME_MAX && !migratedDefaultsDone) {
            breakMaxProp.set(300);
        }
        breaktimeMaxSilenceTicks = breakMaxProp.getInt();

        if (breaktimeMaxSilenceTicks < breaktimeMinSilenceTicks) {
            breaktimeMaxSilenceTicks = breaktimeMinSilenceTicks;
        }

        soundWorkingEnabled = configuration.getBoolean(
            "soundWorkingEnabled",
            CATEGORY_SOUNDS,
            soundWorkingEnabled,
            "Play working/*.ogg while standing at a machine (INSPECT), not while walking toward it.");

        // workingMin/MaxSilenceTicks. Migrate legacy workingSilenceTicks once, then remove key.
        boolean hasNewWorkMin = configuration.hasKey(CATEGORY_SOUNDS, "workingMinSilenceTicks");
        boolean hasNewWorkMax = configuration.hasKey(CATEGORY_SOUNDS, "workingMaxSilenceTicks");
        int legacyWorkSilence = -1;
        if (configuration.hasKey(CATEGORY_SOUNDS, "workingSilenceTicks")) {
            Property legacy = configuration.get(
                CATEGORY_SOUNDS,
                "workingSilenceTicks",
                40,
                "REMOVED in 1.0.6 — migrated to workingMin/MaxSilenceTicks.");
            legacyWorkSilence = legacy.getInt();
            if (legacyWorkSilence == OLD_WORKING_SILENCE) {
                legacyWorkSilence = 40;
            }
            configuration.getCategory(CATEGORY_SOUNDS)
                .remove("workingSilenceTicks");
        }

        int workMinDefault = 40;
        int workMaxDefault = 80;
        if (!hasNewWorkMin && !hasNewWorkMax && legacyWorkSilence >= 0) {
            if (legacyWorkSilence == OLD_WORKING_SILENCE_V26 || legacyWorkSilence == OLD_WORKING_SILENCE) {
                workMinDefault = 40;
                workMaxDefault = 80;
            } else {
                workMinDefault = legacyWorkSilence;
                workMaxDefault = legacyWorkSilence;
            }
        }

        Property workMinProp = configuration.get(
            CATEGORY_SOUNDS,
            "workingMinSilenceTicks",
            workMinDefault,
            "Silence between working clips while at machine — MINIMUM "
                + "(ticks; 20 ticks = 1 second). Default 40 (~2s). Was 100 (~5s) in 1.0.0–1.0.4.",
            0,
            6000);
        workingMinSilenceTicks = workMinProp.getInt();

        Property workMaxProp = configuration.get(
            CATEGORY_SOUNDS,
            "workingMaxSilenceTicks",
            workMaxDefault,
            "Silence between working clips while at machine — MAXIMUM "
                + "(ticks; 20 ticks = 1 second). Default 80 (~4s). Was 200 (~10s) in 1.0.0–1.0.4.",
            0,
            6000);
        workingMaxSilenceTicks = workMaxProp.getInt();

        // 1.0.5: exact old pair 100/200 → 40/80 once (even if V102 migrations already ran).
        Property workSilenceMigrated = configuration.get(
            CATEGORY_SOUNDS,
            "migratedWorkingSilenceV105",
            false,
            "INTERNAL — set true after migrating working silence 100/200 → 40/80. Do not edit.");
        if (!workSilenceMigrated.getBoolean(false) && workingMinSilenceTicks == OLD_WORKING_MIN_V104
            && workingMaxSilenceTicks == OLD_WORKING_MAX_V104) {
            workingMinSilenceTicks = 40;
            workingMaxSilenceTicks = 80;
            workMinProp.set(40);
            workMaxProp.set(80);
            workSilenceMigrated.set(true);
        } else if (!workSilenceMigrated.getBoolean(false)) {
            workSilenceMigrated.set(true);
        }

        if (workingMaxSilenceTicks < workingMinSilenceTicks) {
            workingMaxSilenceTicks = workingMinSilenceTicks;
        }

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
            "Cooldown between interaction sounds (ticks). Default 20 (~1s).");

        // 1.0.6: strip deprecated defaultClipLengthTicks (hardcoded client safety 6000)
        if (configuration.hasKey(CATEGORY_SOUNDS, "defaultClipLengthTicks")) {
            configuration.getCategory(CATEGORY_SOUNDS)
                .remove("defaultClipLengthTicks");
        }

        // --- combat ---
        aggressiveModeAllowed = configuration.getBoolean(
            "aggressiveModeAllowed",
            CATEGORY_COMBAT,
            aggressiveModeAllowed,
            "If false, locker right-click cannot enable aggressive mode (forced peaceful).");

        Property dmgProp = configuration.get(
            CATEGORY_COMBAT,
            "aggressiveAttackDamage",
            3.0D,
            "Damage dealt to hostile mobs (IMob/EntityMob) when aggressive. "
                + "Never hits workers. Players only if attackPlayers=true. Default 3.0 (was 1.0).");
        float dmgVal = (float) dmgProp.getDouble(3.0D);
        if (dmgVal == OLD_AGGRESSIVE_DAMAGE && !migratedDefaultsDone) {
            dmgProp.set(3.0D);
            dmgVal = 3.0F;
        }
        if (dmgVal < 0.0F) {
            dmgVal = 0.0F;
        }
        if (dmgVal > 40.0F) {
            dmgVal = 40.0F;
        }
        aggressiveAttackDamage = dmgVal;

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

        // --- supervisor ---
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

        medkitSearchRadius = configuration.getInt(
            "medkitSearchRadius",
            Configuration.CATEGORY_GENERAL,
            medkitSearchRadius,
            8,
            512,
            "How far (blocks) injured workers/supervisors search for a medkit. Default 200.");

        medkitHealRange = configuration.getFloat(
            "medkitHealRange",
            Configuration.CATEGORY_GENERAL,
            (float) medkitHealRange,
            0.5F,
            8.0F,
            "Max distance from medkit center to regenerate (blocks). Default 2.");

        if (!migratedDefaultsDone) {
            migratedFlag.set(true);
            migratedDefaultsDone = true;
        }
    }

    /** Move legacy category {@code sounds} → {@code sounds_server} once. */
    private static void migrateLegacySoundsCategory() {
        if (!configuration.hasCategory(LEGACY_CATEGORY_SOUNDS)) {
            return;
        }
        ConfigCategory old = configuration.getCategory(LEGACY_CATEGORY_SOUNDS);
        ConfigCategory neu = configuration.getCategory(CATEGORY_SOUNDS);
        for (Map.Entry<String, Property> e : old.getValues()
            .entrySet()) {
            if (!neu.containsKey(e.getKey())) {
                neu.put(e.getKey(), e.getValue());
            }
        }
        configuration.removeCategory(old);
    }

    /**
     * Push {@link #walkingSpeed} / attack damage onto loaded workers and supervisors.
     */
    public static void applyToLivingEntities() {
        try {
            net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
            if (server == null) {
                return;
            }
            for (net.minecraft.world.WorldServer world : server.worldServers) {
                if (world == null) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                java.util.List<net.minecraft.entity.Entity> list = world.loadedEntityList;
                if (list == null) {
                    continue;
                }
                for (net.minecraft.entity.Entity e : list) {
                    if (e instanceof com.angelika.lockerworker.entity.EntityLockerWorker) {
                        ((com.angelika.lockerworker.entity.EntityLockerWorker) e).refreshMovementSpeedFromConfig();
                    }
                }
            }
        } catch (Throwable t) {
            LockerWorkerMod.LOG.warn("applyToLivingEntities failed: {}", t.toString());
        }
    }
}
