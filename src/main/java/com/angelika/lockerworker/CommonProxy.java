package com.angelika.lockerworker;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.angelika.lockerworker.block.BlockLocker;
import com.angelika.lockerworker.block.BlockMedkit;
import com.angelika.lockerworker.block.BlockSupervisorLocker;
import com.angelika.lockerworker.block.BlockTrashcan;
import com.angelika.lockerworker.block.BlockWorkerBed;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.entity.EntityShiftSupervisor;
import com.angelika.lockerworker.event.CreeperScareHandler;
import com.angelika.lockerworker.inventory.GuiHandler;
import com.angelika.lockerworker.item.ItemWorkerBed;
import com.angelika.lockerworker.network.PacketHandler;
import com.angelika.lockerworker.sound.ModSounds;
import com.angelika.lockerworker.tileentity.TileEntityLocker;
import com.angelika.lockerworker.tileentity.TileEntitySupervisorLocker;
import com.angelika.lockerworker.tileentity.TileEntityWorkerBed;
import com.angelika.lockerworker.util.MedkitRegistry;
import com.angelika.lockerworker.util.TrashcanRegistry;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    public static BlockLocker blockLocker;
    public static BlockSupervisorLocker blockSupervisorLocker;
    public static BlockTrashcan blockTrashcan;
    public static BlockMedkit blockMedkit;
    public static BlockWorkerBed blockWorkerBed;
    public static ItemWorkerBed itemWorkerBed;

    public void preInit(FMLPreInitializationEvent event) {
        // Explicit filenames (not Forge suggested lockerworker.cfg)
        java.io.File cfgDir = event.getModConfigurationDirectory();
        Config.loadOrMigrate(cfgDir);
        PacketHandler.init();

        LockerWorkerMod.LOG.info("RobolCraft at version " + Tags.VERSION);
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

        blockSupervisorLocker = new BlockSupervisorLocker();
        GameRegistry.registerBlock(blockSupervisorLocker, "supervisor_locker");
        GameRegistry.registerTileEntity(TileEntitySupervisorLocker.class, LockerWorkerMod.MODID + ":supervisor_locker");

        blockTrashcan = new BlockTrashcan();
        GameRegistry.registerBlock(blockTrashcan, "trashcan");

        blockMedkit = new BlockMedkit();
        GameRegistry.registerBlock(blockMedkit, "medkit");

        blockWorkerBed = new BlockWorkerBed();
        // null ItemBlock — placed via ItemWorkerBed like vanilla bed
        GameRegistry.registerBlock(blockWorkerBed, null, "worker_bed");
        GameRegistry.registerTileEntity(TileEntityWorkerBed.class, LockerWorkerMod.MODID + ":worker_bed");
        itemWorkerBed = new ItemWorkerBed();
        GameRegistry.registerItem(itemWorkerBed, "worker_bed");

        int entityId = EntityRegistry.findGlobalUniqueEntityId();
        EntityRegistry.registerGlobalEntityID(EntityLockerWorker.class, "LockerWorker", entityId, 0x808080, 0x404040);
        EntityRegistry
            .registerModEntity(EntityLockerWorker.class, "LockerWorker", 0, LockerWorkerMod.instance, 64, 3, true);

        int supervisorEntityId = EntityRegistry.findGlobalUniqueEntityId();
        EntityRegistry.registerGlobalEntityID(
            EntityShiftSupervisor.class,
            "ShiftSupervisor",
            supervisorEntityId,
            0xAA3333,
            0xEEEEEE);
        EntityRegistry.registerModEntity(
            EntityShiftSupervisor.class,
            "ShiftSupervisor",
            1,
            LockerWorkerMod.instance,
            64,
            3,
            true);

        CreeperScareHandler.register();
        TrashcanRegistry.registerChunkHandler();
        MedkitRegistry.registerChunkHandler();
        com.angelika.lockerworker.compat.MalisisDoorsCompat.probe();
        ModSounds.discover();
    }

    protected GuiHandler createGuiHandler() {
        return new GuiHandler();
    }

    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(LockerWorkerMod.instance, createGuiHandler());
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

        // Supervisor locker: same shape, gold ingot instead of rotten flesh
        GameRegistry.addRecipe(
            new ItemStack(blockSupervisorLocker),
            "IG",
            "ID",
            'I',
            Blocks.iron_bars,
            'G',
            Items.gold_ingot,
            'D',
            Blocks.dirt);

        // Trashcan: 5 iron bars in U shape
        // B . B
        // B . B
        // . B .
        GameRegistry.addRecipe(new ItemStack(blockTrashcan), "B B", "B B", " B ", 'B', Blocks.iron_bars);

        // Medkit: 8 iron bars + white wool center
        GameRegistry.addRecipe(
            new ItemStack(blockMedkit),
            "BBB",
            "BWB",
            "BBB",
            'B',
            Blocks.iron_bars,
            'W',
            new ItemStack(Blocks.wool, 1, 0));
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new com.angelika.lockerworker.command.CommandRobolCraft());
    }

    /** Client only: exclusive worker sound tick. No-op on dedicated server. */
    public void tickWorkerClientSounds(EntityLockerWorker worker) {}

    /** Client only: stop/remove exclusive sound for entity id. */
    public void stopWorkerClientSounds(int entityId) {}
}
