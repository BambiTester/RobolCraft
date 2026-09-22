package com.angelika.lockerworker.event;

import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.angelika.lockerworker.entity.EntityLockerWorker;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Injects vanilla-style {@link EntityAIAvoidEntity} so creepers flee
 * {@link EntityLockerWorker} (~7 blocks). 1.7.10-safe via
 * {@link EntityJoinWorldEvent}; does not clear existing creeper AI.
 */
public final class CreeperScareHandler {

    public static final CreeperScareHandler INSTANCE = new CreeperScareHandler();

    /** Avoid distance roughly matching vanilla ocelot scare (~6–8). */
    private static final float AVOID_DISTANCE = 7.0F;
    private static final double FAR_SPEED = 1.0D;
    private static final double NEAR_SPEED = 1.2D;

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

    @SuppressWarnings("rawtypes")
    private static boolean alreadyHasAvoidWorker(EntityCreeper creeper) {
        for (Object obj : creeper.tasks.taskEntries) {
            if (!(obj instanceof EntityAITasks.EntityAITaskEntry)) {
                continue;
            }
            EntityAITasks.EntityAITaskEntry entry = (EntityAITasks.EntityAITaskEntry) obj;
            if (entry.action instanceof EntityAIAvoidEntity) {
                // Cannot easily read class target on 1.7.10 without reflection;
                // skip duplicate inject by marking via entity data if re-joining.
                // Safe enough: EntityJoinWorld typically once per spawn.
                // If already injected on this entity instance, entityId stays.
            }
        }
        // Use entity NBT-less flag via extended properties-less: check task count
        // of AvoidEntity toward our class via reflection field "targetEntityClass"
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
