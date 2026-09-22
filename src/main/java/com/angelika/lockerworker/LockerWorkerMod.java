package com.angelika.lockerworker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = LockerWorkerMod.MODID,
    version = Tags.VERSION,
    name = "Locker Worker",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:gregtech",
    guiFactory = "com.angelika.lockerworker.client.GuiFactory")
public class LockerWorkerMod {

    public static final String MODID = "lockerworker";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static LockerWorkerMod instance;

    @SidedProxy(
        clientSide = "com.angelika.lockerworker.ClientProxy",
        serverSide = "com.angelika.lockerworker.CommonProxy")
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
