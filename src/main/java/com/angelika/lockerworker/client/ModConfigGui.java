package com.angelika.lockerworker.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.LockerWorkerMod;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * In-game config screen for robolcraft.cfg + robolcraft-client.cfg.
 * Categories: general | sounds_server | combat | supervisor | client_audio.
 */
@SideOnly(Side.CLIENT)
public class ModConfigGui extends GuiConfig {

    public ModConfigGui(GuiScreen parent) {
        super(
            parent,
            getConfigElements(),
            LockerWorkerMod.MODID,
            false,
            false,
            GuiConfig.getAbridgedConfigPath(getConfigTitlePath()));
    }

    private static String getConfigTitlePath() {
        Configuration cfg = Config.getConfiguration();
        if (cfg != null && cfg.getConfigFile() != null) {
            return cfg.getConfigFile()
                .getAbsolutePath() + " + robolcraft-client.cfg";
        }
        if (Config.getConfigFile() != null) {
            return Config.getConfigFile()
                .getAbsolutePath() + " + robolcraft-client.cfg";
        }
        return "robolcraft.cfg + robolcraft-client.cfg";
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> list = new ArrayList<IConfigElement>();
        Configuration cfg = Config.getConfiguration();
        if (cfg != null) {
            list.add(new ConfigElement(cfg.getCategory(Configuration.CATEGORY_GENERAL)));
            list.add(new ConfigElement(cfg.getCategory(Config.CATEGORY_SOUNDS)));
            list.add(new ConfigElement(cfg.getCategory(Config.CATEGORY_COMBAT)));
            list.add(new ConfigElement(cfg.getCategory(Config.CATEGORY_SUPERVISOR)));
        }
        Configuration clientCfg = ClientConfig.getConfiguration();
        if (clientCfg != null) {
            list.add(new ConfigElement(clientCfg.getCategory(ClientConfig.CATEGORY_CLIENT_AUDIO)));
        }
        return list;
    }
}
