package com.angelika.lockerworker.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.LockerWorkerMod;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * In-game config screen for {@code config/lockerworker.cfg}.
 * Edits take effect for AI on save (ConfigChangedEvent → reload); walkingSpeed
 * attribute on existing entities may need a restart / respawn.
 */
@SideOnly(Side.CLIENT)
public class ModConfigGui extends GuiConfig {

    public ModConfigGui(GuiScreen parent) {
        super(
            parent,
            new ConfigElement(Config.getConfiguration().getCategory(Configuration.CATEGORY_GENERAL)).getChildElements(),
            LockerWorkerMod.MODID,
            false,
            false,
            GuiConfig.getAbridgedConfigPath(Config.getConfiguration().toString()));
    }
}
