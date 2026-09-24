package com.angelika.lockerworker.event;

import java.util.HashMap;
import java.util.Map;

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
 * Final aggressive toggle binding (v16): left-click / punch on worker or
 * supervisor locker toggles aggressive/passive + skull. Dig is not canceled
 * (hold to break); a short per-player cooldown prevents flip-flop while mining.
 */
public final class LockerClickHandler {

    public static final LockerClickHandler INSTANCE = new LockerClickHandler();

    /** Min ticks between aggressive toggles per player. */
    private static final int TOGGLE_COOLDOWN_TICKS = 10;

    private final Map<Integer, Long> lastToggleTick = new HashMap<Integer, Long>();
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
        if (player == null) {
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
        boolean isWorkerLocker = block == CommonProxy.blockLocker;
        boolean isSupervisorLocker = block == CommonProxy.blockSupervisorLocker;
        if (!isWorkerLocker && !isSupervisorLocker) {
            return;
        }
        if (world.isRemote) {
            return;
        }
        long now = world.getTotalWorldTime();
        Integer key = Integer.valueOf(player.getEntityId());
        Long prev = lastToggleTick.get(key);
        if (prev != null && now - prev.longValue() < TOGGLE_COOLDOWN_TICKS) {
            return;
        }
        lastToggleTick.put(key, Long.valueOf(now));

        int meta = world.getBlockMetadata(x, y, z);
        if (isWorkerLocker) {
            TileEntityLocker te = BlockLocker.getLockerTE(world, x, y, z, meta);
            if (te != null) {
                te.toggleAggressive(player);
            }
        } else {
            TileEntitySupervisorLocker te = BlockSupervisorLocker.getLockerTE(world, x, y, z, meta);
            if (te != null) {
                te.toggleAggressive(player);
            }
        }
    }
}
