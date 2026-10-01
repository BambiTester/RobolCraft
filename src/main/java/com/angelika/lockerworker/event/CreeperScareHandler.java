package com.angelika.lockerworker.event;

import java.util.List;

import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

import com.angelika.lockerworker.entity.EntityLockerWorker;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Injects vanilla-style {@link EntityAIAvoidEntity} so creepers flee
 * {@link EntityLockerWorker} (~7 blocks), including {@link com.angelika.lockerworker.entity.EntityShiftSupervisor}
 * (subclass — {@code EntityLockerWorker.class} avoid + AABB checks cover both).
 * Also cancels creeper fuse/explosion while a worker/supervisor is in proximity
 * (ocelot-like: they do not blow up at workers or supervisors).
 * 1.0.3: workers may also melee-attack creepers; flee + defuse stay active.
 * 1.0.6: defuse AABB only while ignited, else every {@link #DEFUSE_IDLE_INTERVAL} ticks.
 * 1.7.10-safe via {@link EntityJoinWorldEvent} + {@link LivingEvent.LivingUpdateEvent}.
 */
public final class CreeperScareHandler {

    public static final CreeperScareHandler INSTANCE = new CreeperScareHandler();

    /** Avoid distance roughly matching vanilla ocelot scare (~6–8). */
    private static final float AVOID_DISTANCE = 7.0F;
    private static final double FAR_SPEED = 1.0D;
    private static final double NEAR_SPEED = 1.2D;
    /** When not ignited, run proximity scan every N ticks (still catches start of fuse). */
    private static final int DEFUSE_IDLE_INTERVAL = 5;

    private boolean registered;

    private CreeperScareHandler() {}

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

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.world == null || event.world.isRemote) {
            return;
        }
        if (!(event.entity instanceof EntityCreeper)) {
            return;
        }
        EntityCreeper creeper = (EntityCreeper) event.entity;
        if (alreadyHasAvoidWorker(creeper)) {
            return;
        }
        // Priority 1 — same band as vanilla ocelot avoid on creepers
        creeper.tasks.addTask(
            1,
            new EntityAIAvoidEntity(creeper, EntityLockerWorker.class, AVOID_DISTANCE, FAR_SPEED, NEAR_SPEED));
    }

    /**
     * Ocelot-like: while a locker worker is within avoid range, force creeper state idle
     * so fuse winds down and they never explode on workers.
     */
    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event.entityLiving == null || event.entityLiving.worldObj == null) {
            return;
        }
        if (event.entityLiving.worldObj.isRemote) {
            return;
        }
        if (!(event.entityLiving instanceof EntityCreeper)) {
            return;
        }
        EntityCreeper creeper = (EntityCreeper) event.entityLiving;
        // 1.0.6: full scan every tick while fused; otherwise every DEFUSE_IDLE_INTERVAL
        boolean ignited = creeper.getCreeperState() > 0;
        if (!ignited && (creeper.ticksExisted % DEFUSE_IDLE_INTERVAL) != 0) {
            return;
        }
        AxisAlignedBB box = creeper.boundingBox.expand(AVOID_DISTANCE, AVOID_DISTANCE * 0.5D, AVOID_DISTANCE);
        @SuppressWarnings("unchecked")
        List<EntityLockerWorker> near = creeper.worldObj.getEntitiesWithinAABB(EntityLockerWorker.class, box);
        if (near == null || near.isEmpty()) {
            return;
        }
        boolean anyAlive = false;
        for (EntityLockerWorker w : near) {
            if (w != null && !w.isDead) {
                anyAlive = true;
                break;
            }
        }
        if (!anyAlive) {
            return;
        }
        // Defuse: setCreeperState(-1) decrements timeSinceIgnited each tick
        creeper.setCreeperState(-1);
    }

    @SuppressWarnings("rawtypes")
    private static boolean alreadyHasAvoidWorker(EntityCreeper creeper) {
        for (Object obj : creeper.tasks.taskEntries) {
            if (!(obj instanceof EntityAITasks.EntityAITaskEntry)) {
                continue;
            }
            EntityAITasks.EntityAITaskEntry entry = (EntityAITasks.EntityAITaskEntry) obj;
            if (!(entry.action instanceof EntityAIAvoidEntity)) {
                continue;
            }
            try {
                java.lang.reflect.Field f = EntityAIAvoidEntity.class.getDeclaredField("targetEntityClass");
                f.setAccessible(true);
                Object cls = f.get(entry.action);
                if (cls == EntityLockerWorker.class) {
                    return true;
                }
            } catch (Throwable t) {
                try {
                    java.lang.reflect.Field f = EntityAIAvoidEntity.class.getDeclaredField("field_75381_a");
                    f.setAccessible(true);
                    Object cls = f.get(entry.action);
                    if (cls == EntityLockerWorker.class) {
                        return true;
                    }
                } catch (Throwable ignored) {
                    // fall through — allow one inject
                }
            }
        }
        return false;
    }
}
