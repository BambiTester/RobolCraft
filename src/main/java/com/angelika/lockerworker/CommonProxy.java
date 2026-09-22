package com.angelika.lockerworker;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.angelika.lockerworker.block.BlockLocker;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.tileentity.TileEntityLocker;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    public static BlockLocker blockLocker;

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        LockerWorkerMod.LOG.info(Config.greeting);
        LockerWorkerMod.LOG.info("Locker Worker at version " + Tags.VERSION);

        blockLocker = new BlockLocker();
        GameRegistry.registerBlock(blockLocker, "locker");
        GameRegistry.registerTileEntity(TileEntityLocker.class, LockerWorkerMod.MODID + ":locker");

        int entityId = EntityRegistry.findGlobalUniqueEntityId();
        EntityRegistry.registerGlobalEntityID(EntityLockerWorker.class, "LockerWorker", entityId, 0x808080, 0x404040);
        EntityRegistry
            .registerModEntity(EntityLockerWorker.class, "LockerWorker", 0, LockerWorkerMod.instance, 64, 3, true);
    }

    public void init(FMLInitializationEvent event) {
        // 2x2 shaped: iron bars | rotten flesh / iron bars | dirt → 1 locker
        GameRegistry.addRecipe(
            new ItemStack(blockLocker),
            "IR",
            "ID",
            'I',
            Blocks.iron_bars,
            'R',
            Items.rotten_flesh,
            'D',
            Blocks.dirt);
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}
}
