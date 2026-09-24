package com.angelika.lockerworker.event;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.angelika.lockerworker.CommonProxy;
import com.angelika.lockerworker.block.BlockLocker;
import com.angelika.lockerworker.block.BlockSupervisorLocker;
import com.angelika.lockerworker.tileentity.TileEntityLocker;
import com.angelika.lockerworker.tileentity.TileEntitySupervisorLocker;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Temporary aggressive toggle binding (v16): sneak + left-click on worker or
 * supervisor locker. Cancels the dig so the locker is not broken while toggling.
 * Replace when Angelika confirms the final binding.
 */
public final class LockerClickHandler {

    public static final LockerClickHandler INSTANCE = new LockerClickHandler();

    private boolean registered;

    private LockerClickHandler() {}

    public static void register() {
        INSTANCE.ensureRegistered();
    }

    private void ensureRegistered() {
        if (registered) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        registered = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            return;
        }
        EntityPlayer player = event.entityPlayer;
        if (player == null || !player.isSneaking()) {
            return;
        }
        World world = event.world;
        if (world == null) {
            return;
        }
        int x = event.x;
        int y = event.y;
        int z = event.z;
        Block block = world.getBlock(x, y, z);
        if (block == CommonProxy.blockLocker) {
            event.setCanceled(true);
            if (world.isRemote) {
                return;
            }
            int meta = world.getBlockMetadata(x, y, z);
            TileEntityLocker te = BlockLocker.getLockerTE(world, x, y, z, meta);
            if (te != null) {
                te.toggleAggressive(player);
            }
        } else if (block == CommonProxy.blockSupervisorLocker) {
            event.setCanceled(true);
            if (world.isRemote) {
                return;
            }
            int meta = world.getBlockMetadata(x, y, z);
            TileEntitySupervisorLocker te = BlockSupervisorLocker.getLockerTE(world, x, y, z, meta);
            if (te != null) {
                te.toggleAggressive(player);
            }
        }
    }
}
