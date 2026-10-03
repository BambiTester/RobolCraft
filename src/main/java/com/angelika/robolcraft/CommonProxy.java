package com.angelika.robolcraft;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.angelika.robolcraft.api.RobolCraftAPI;
import com.angelika.robolcraft.block.BlockLocker;
import com.angelika.robolcraft.block.BlockMedkit;
import com.angelika.robolcraft.block.BlockSupervisorLocker;
import com.angelika.robolcraft.block.BlockTrashcan;
import com.angelika.robolcraft.block.BlockWorkerBed;
import com.angelika.robolcraft.entity.EntityRobolCraft;
import com.angelika.robolcraft.entity.EntityShiftSupervisor;
import com.angelika.robolcraft.event.CreeperScareHandler;
import com.angelika.robolcraft.inventory.GuiHandler;
import com.angelika.robolcraft.item.ItemWorkerBed;
import com.angelika.robolcraft.network.PacketHandler;
import com.angelika.robolcraft.npc.BehaviorSlots;
import com.angelika.robolcraft.npc.NpcWorldFile;
import com.angelika.robolcraft.sound.ModSounds;
import com.angelika.robolcraft.tileentity.TileEntityLocker;
import com.angelika.robolcraft.tileentity.TileEntitySupervisorLocker;
import com.angelika.robolcraft.tileentity.TileEntityWorkerBed;
import com.angelika.robolcraft.util.MedkitRegistry;
import com.angelika.robolcraft.util.TrashcanRegistry;

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
        // Explicit filename: config/robolcraft.cfg
        java.io.File cfgDir = event.getModConfigurationDirectory();
        Config.loadOrMigrate(cfgDir);
        PacketHandler.init();

        RobolCraftMod.LOG.info("RobolCraft at version " + Tags.VERSION);
        RobolCraftMod.LOG.info(
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
        GameRegistry.registerTileEntity(TileEntityLocker.class, RobolCraftMod.MODID + ":locker");

        blockSupervisorLocker = new BlockSupervisorLocker();
        GameRegistry.registerBlock(blockSupervisorLocker, "supervisor_locker");
        GameRegistry.registerTileEntity(TileEntitySupervisorLocker.class, RobolCraftMod.MODID + ":supervisor_locker");

        blockTrashcan = new BlockTrashcan();
        GameRegistry.registerBlock(blockTrashcan, "trashcan");
        RobolCraftAPI.registerTrashcan(blockTrashcan);

        blockMedkit = new BlockMedkit();
        GameRegistry.registerBlock(blockMedkit, "medkit");
        RobolCraftAPI.registerMedkit(blockMedkit);

        blockWorkerBed = new BlockWorkerBed();
        // null ItemBlock — placed via ItemWorkerBed like vanilla bed
        GameRegistry.registerBlock(blockWorkerBed, null, "worker_bed");
        GameRegistry.registerTileEntity(TileEntityWorkerBed.class, RobolCraftMod.MODID + ":worker_bed");
        itemWorkerBed = new ItemWorkerBed();
        GameRegistry.registerItem(itemWorkerBed, "worker_bed");

        int entityId = EntityRegistry.findGlobalUniqueEntityId();
        EntityRegistry.registerGlobalEntityID(EntityRobolCraft.class, "RobolCraft", entityId, 0x808080, 0x404040);
        EntityRegistry.registerModEntity(EntityRobolCraft.class, "RobolCraft", 0, RobolCraftMod.instance, 64, 3, true);

        int supervisorEntityId = EntityRegistry.findGlobalUniqueEntityId();
        EntityRegistry.registerGlobalEntityID(
            EntityShiftSupervisor.class,
            "ShiftSupervisor",
            supervisorEntityId,
            0xAA3333,
            0xEEEEEE);
        EntityRegistry
            .registerModEntity(EntityShiftSupervisor.class, "ShiftSupervisor", 1, RobolCraftMod.instance, 64, 3, true);

        CreeperScareHandler.register();
        TrashcanRegistry.registerChunkHandler();
        MedkitRegistry.registerChunkHandler();
        BehaviorSlots.registerDefaults();
        NpcWorldFile.registerEvents();
        com.angelika.robolcraft.compat.MalisisDoorsCompat.probe();
        ModSounds.discover();
    }

    protected GuiHandler createGuiHandler() {
        return new GuiHandler();
    }

    public void init(FMLInitializationEvent event) {
        RobolCraftAPI.closeHandshake();
        NetworkRegistry.INSTANCE.registerGuiHandler(RobolCraftMod.instance, createGuiHandler());
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

    public void postInit(FMLPostInitializationEvent event) {
        RobolCraftAPI.freezeContributions();
        ModSounds.discover();
        armAddonSounds();
    }

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new com.angelika.robolcraft.command.CommandRobolCraft());
    }

    /** Client only: exclusive worker sound tick. No-op on dedicated server. */
    public void tickWorkerClientSounds(EntityRobolCraft worker) {}

    /** Client only: stop/remove exclusive sound for entity id. */
    public void stopWorkerClientSounds(int entityId) {}

    /** Client only: register addon oggs on the next tick, after contributions freeze. */
    public void armAddonSounds() {}

    /** Client only: exclusive one-shot for a play name chosen by {@code RobolCraftAPI.sounds().play}. */
    public void queueWorkerClip(int entityId, String playName, float volume, float pitch) {}
}
