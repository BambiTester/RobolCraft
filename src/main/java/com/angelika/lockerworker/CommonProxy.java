package com.angelika.lockerworker;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.angelika.lockerworker.block.BlockLocker;
import com.angelika.lockerworker.block.BlockTrashcan;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.event.CreeperScareHandler;
import com.angelika.lockerworker.sound.ModSounds;
import com.angelika.lockerworker.tileentity.TileEntityLocker;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    public static BlockLocker blockLocker;
    public static BlockTrashcan blockTrashcan;

    public void preInit(FMLPreInitializationEvent event) {
        Config.load(event.getSuggestedConfigurationFile());

        LockerWorkerMod.LOG.info(Config.greeting);
        LockerWorkerMod.LOG.info("Locker Worker at version " + Tags.VERSION);
        LockerWorkerMod.LOG.info(
            "Config: machineScanRadius=" + Config.machineScanRadius
                + ", maxDistanceFromLocker="
                + Config.maxDistanceFromLocker
                + ", walkingSpeed="
                + Config.walkingSpeed
                + " (path="
                + Config.getPathSpeed()
                + "), aggressiveModeAllowed="
                + Config.aggressiveModeAllowed);

        blockLocker = new BlockLocker();
        GameRegistry.registerBlock(blockLocker, "locker");
        GameRegistry.registerTileEntity(TileEntityLocker.class, LockerWorkerMod.MODID + ":locker");

        blockTrashcan = new BlockTrashcan();
        GameRegistry.registerBlock(blockTrashcan, "trashcan");

        int entityId = EntityRegistry.findGlobalUniqueEntityId();
        EntityRegistry.registerGlobalEntityID(EntityLockerWorker.class, "LockerWorker", entityId, 0x808080, 0x404040);
        EntityRegistry
            .registerModEntity(EntityLockerWorker.class, "LockerWorker", 0, LockerWorkerMod.instance, 64, 3, true);

        CreeperScareHandler.register();
        ModSounds.discover();
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

        // Trashcan: 5 iron bars in U shape
        // B . B
        // B . B
        // . B .
        GameRegistry.addRecipe(new ItemStack(blockTrashcan), "B B", "B B", " B ", 'B', Blocks.iron_bars);
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {}

    /** Client only: exclusive worker sound tick. No-op on dedicated server. */
    public void tickWorkerClientSounds(EntityLockerWorker worker) {}

    /** Client only: stop/remove exclusive sound for entity id. */
    public void stopWorkerClientSounds(int entityId) {}
}
