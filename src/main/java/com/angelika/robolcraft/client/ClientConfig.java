package com.angelika.robolcraft.client;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.angelika.robolcraft.RobolCraftMod;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-only audio prefs in {@code config/robolcraft-client.cfg}.
 * Each player's machine; never synced from the server; dedicated server never loads this.
 *
 * <p>
 * Volume and hear-distance for {@link com.angelika.robolcraft.sound.WorkerMovingSound}
 * and other client play paths. Server {@code robolcraft.cfg} holds enable flags + silence
 * gaps only.
 */
@SideOnly(Side.CLIENT)
public final class ClientConfig {

    public static final String CLIENT_CFG_NAME = "robolcraft-client.cfg";

    public static final String CATEGORY_CLIENT_AUDIO = "client_audio";

    /** Master multiplier for worker moving sounds. Default 1.25 (~+25%). Range 0.0–2.0. */
    public static float soundVolume = 1.25F;

    /**
     * Max hearing distance (blocks) for worker moving sounds. Default 16.
     * Clamped 4–64. Used with AttenuationType.NONE + distance fade in WorkerMovingSound.
     */
    public static int soundHearDistance = 16;

    private static Configuration configuration;
    private static File configFile;

    private ClientConfig() {}

    public static Configuration getConfiguration() {
        return configuration;
    }

    public static File getConfigFile() {
        return configFile;
    }

    /** Load once from disk (ClientProxy.preInit only). */
    public static void loadOrMigrate(File configDir) {
        load(new File(configDir, CLIENT_CFG_NAME));
    }

    /** Load once from disk (ClientProxy.preInit only). */
    public static void load(File file) {
        configFile = file;
        configuration = new Configuration(file);
        syncStaticFromConfig();
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static void save() {
        if (configuration != null) {
            configuration.save();
        }
    }

    public static void syncStaticFromConfig() {
        if (configuration == null) {
            return;
        }

        configuration.setCategoryComment(
            CATEGORY_CLIENT_AUDIO,
            "CLIENT-LOCAL ONLY — stored in robolcraft-client.cfg on this machine.\n"
                + "Not synced from the server. Each player sets their own volume and hear distance.\n"
                + "Server robolcraft.cfg controls sound ENABLE flags and silence gaps (world feel).");

        soundVolume = configuration.getFloat(
            "soundVolume",
            CATEGORY_CLIENT_AUDIO,
            soundVolume,
            0.0F,
            2.0F,
            "Master volume for all worker/supervisor moving sounds on THIS client. "
                + "Default 1.25. Range 0.0–2.0. Does not change the server config.");

        soundHearDistance = configuration.getInt(
            "soundHearDistance",
            CATEGORY_CLIENT_AUDIO,
            soundHearDistance,
            4,
            64,
            "Max hearing distance in blocks for worker moving sounds on THIS client. "
                + "Default 16. Uses custom distance fade (vanilla LINEAR ~16 is bypassed).");
    }

    /**
     * One-shot migration: if this client cfg is missing volume keys but the server
     * {@code robolcraft.cfg} still has legacy {@code soundVolume}/{@code soundHearDistance}
     * under sounds / sounds_server, copy those values here once, then remove them from the
     * server file so volume stays client-local going forward.
     */
    public static void migrateFromServerConfigIfNeeded(Configuration serverCfg) {
        if (configuration == null || serverCfg == null) {
            return;
        }
        boolean clientHasVol = configuration.hasKey(CATEGORY_CLIENT_AUDIO, "soundVolume");
        boolean clientHasHear = configuration.hasKey(CATEGORY_CLIENT_AUDIO, "soundHearDistance");

        float migratedVol = soundVolume;
        int migratedHear = soundHearDistance;
        boolean copied = false;

        String[] cats = new String[] { "sounds_server", "sounds" };
        for (String cat : cats) {
            if (!serverCfg.hasCategory(cat)) {
                continue;
            }
            if (!clientHasVol && serverCfg.hasKey(cat, "soundVolume")) {
                Property p = serverCfg.get(cat, "soundVolume", 1.0D);
                migratedVol = (float) p.getDouble(1.0D);
                copied = true;
            }
            if (!clientHasHear && serverCfg.hasKey(cat, "soundHearDistance")) {
                Property p = serverCfg.get(cat, "soundHearDistance", 16);
                migratedHear = p.getInt(16);
                copied = true;
            }
        }

        if (copied) {
            Property volProp = configuration.get(
                CATEGORY_CLIENT_AUDIO,
                "soundVolume",
                migratedVol,
                "Master volume for all worker/supervisor moving sounds on THIS client. "
                    + "Default 1.25. Range 0.0–2.0. Does not change the server config.");
            volProp.set(migratedVol);
            Property hearProp = configuration.get(
                CATEGORY_CLIENT_AUDIO,
                "soundHearDistance",
                migratedHear,
                "Max hearing distance in blocks for worker moving sounds on THIS client. "
                    + "Default 16. Uses custom distance fade (vanilla LINEAR ~16 is bypassed).");
            hearProp.set(migratedHear);
            soundVolume = migratedVol;
            soundHearDistance = migratedHear;
            configuration.save();
            RobolCraftMod.LOG.info(
                "Migrated soundVolume=" + soundVolume
                    + " soundHearDistance="
                    + soundHearDistance
                    + " from server cfg into robolcraft-client.cfg");
        }

        // Strip volume keys from server cfg (any legacy category)
        boolean serverChanged = false;
        for (String cat : cats) {
            if (serverCfg.hasCategory(cat)) {
                if (serverCfg.hasKey(cat, "soundVolume")) {
                    serverCfg.getCategory(cat)
                        .remove("soundVolume");
                    serverChanged = true;
                }
                if (serverCfg.hasKey(cat, "soundHearDistance")) {
                    serverCfg.getCategory(cat)
                        .remove("soundHearDistance");
                    serverChanged = true;
                }
            }
        }
        if (serverChanged) {
            serverCfg.save();
        }
    }

    /** After GuiConfig Done: persist then refresh statics (same instance). */
    public static void reload() {
        save();
        syncStaticFromConfig();
    }
}
