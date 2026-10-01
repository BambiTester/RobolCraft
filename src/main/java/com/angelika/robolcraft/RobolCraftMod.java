package com.angelika.robolcraft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = RobolCraftMod.MODID,
    version = Tags.VERSION,
    name = "RobolCraft",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:gregtech",
    guiFactory = "com.angelika.robolcraft.client.GuiFactory")
public class RobolCraftMod {

    public static final String MODID = "robolcraft";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static RobolCraftMod instance;

    @SidedProxy(clientSide = "com.angelika.robolcraft.ClientProxy", serverSide = "com.angelika.robolcraft.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
