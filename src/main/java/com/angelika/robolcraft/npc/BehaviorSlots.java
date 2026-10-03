package com.angelika.robolcraft.npc;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIOpenDoor;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;

import com.angelika.robolcraft.api.BehaviorFactory;
import com.angelika.robolcraft.entity.EntityRobolCraft;
import com.angelika.robolcraft.entity.EntityShiftSupervisor;
import com.angelika.robolcraft.entity.ai.EntityAIAttackHostile;
import com.angelika.robolcraft.entity.ai.EntityAIDeliverReport;
import com.angelika.robolcraft.entity.ai.EntityAIOpenMalisisDoor;
import com.angelika.robolcraft.entity.ai.EntityAIOpenWoodenTrapDoor;
import com.angelika.robolcraft.entity.ai.EntityAISeekMedkit;
import com.angelika.robolcraft.entity.ai.EntityAISuperviseMachines;
import com.angelika.robolcraft.entity.ai.EntityAIWorkerPanic;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Named AI slots. Defaults match the worker and supervisor task lists. The world JSON can turn a
 * slot off or select another registered behavior.
 */
public final class BehaviorSlots {

    public static final String SWIM = "swim";
    public static final String DOORS = "doors";
    public static final String PANIC = "panic";
    public static final String HEAL = "heal";
    public static final String COMBAT = "combat";
    public static final String NIGHT = "night";
    public static final String RETURN_LOCKER = "return_locker";
    public static final String BREAKTIME = "breaktime";
    public static final String DAY_WORK = "day_work";
    public static final String LOOK = "look";
    public static final String DELIVER_REPORT = "deliver_report";

    private static final String[] WORKER_SLOTS = { SWIM, DOORS, PANIC, HEAL, COMBAT, NIGHT, RETURN_LOCKER, BREAKTIME,
        DAY_WORK, LOOK };
    private static final String[] SUPERVISOR_SLOTS = { SWIM, DOORS, PANIC, HEAL, COMBAT, NIGHT, RETURN_LOCKER,
        DELIVER_REPORT, BREAKTIME, DAY_WORK, LOOK };

    private static final Map<String, BehaviorFactory> BEHAVIORS = new HashMap<String, BehaviorFactory>();
    private static boolean defaultsRegistered;

    private BehaviorSlots() {}

    public static void registerDefaults() {
        if (defaultsRegistered) {
            return;
        }
        defaultsRegistered = true;
        register("robolcraft:swim", new BehaviorFactory() {

            @Override
            public EntityAIBase create(EntityRobolCraft entity, JsonObject parameters) {
                return new net.minecraft.entity.ai.EntityAISwimming(entity);
            }
        });
        register("robolcraft:doors", new BehaviorFactory() {

            @Override
            public EntityAIBase create(EntityRobolCraft entity, JsonObject parameters) {
                return null;
            }
        });
        register("robolcraft:panic", new BehaviorFactory() {

            @Override
            public EntityAIBase create(EntityRobolCraft entity, JsonObject parameters) {
                float speed = NpcWorldFile.numberParam(entity, PANIC, "speed", 1.25F);
                return new EntityAIWorkerPanic(entity, speed);
            }
        });
        register("robolcraft:heal", new BehaviorFactory() {

            @Override
            public EntityAIBase create(EntityRobolCraft entity, JsonObject parameters) {
                return new EntityAISeekMedkit(entity);
            }
        });
        register("robolcraft:combat", new BehaviorFactory() {

            @Override
            public EntityAIBase create(EntityRobolCraft entity, JsonObject parameters) {
                return new EntityAIAttackHostile(entity);
            }
        });
        register("robolcraft:night", new BehaviorFactory() {

            @Override
            public EntityAIBase create(EntityRobolCraft entity, JsonObject parameters) {
                return entity.nightRoutine();
            }
        });
        register("robolcraft:return_locker", new BehaviorFactory() {

            @Override
            public EntityAIBase create(EntityRobolCraft entity, JsonObject parameters) {
                return entity.returnToLocker();
            }
        });
        register("robolcraft:breaktime", new BehaviorFactory() {

            @Override
            public EntityAIBase create(EntityRobolCraft entity, JsonObject parameters) {
                return entity.breakTime();
            }
        });
        register("robolcraft:wander", new BehaviorFactory() {

            @Override
            public EntityAIBase create(EntityRobolCraft entity, JsonObject parameters) {
                return entity.dayWander();
            }
        });
        register("robolcraft:supervise", new BehaviorFactory() {

            @Override
            public EntityAIBase create(EntityRobolCraft entity, JsonObject parameters) {
                if (!(entity instanceof EntityShiftSupervisor)) {
                    return null;
                }
                EntityAISuperviseMachines ai = new EntityAISuperviseMachines((EntityShiftSupervisor) entity);
                ((EntityShiftSupervisor) entity).attachSupervise(ai);
                return ai;
            }
        });
        register("robolcraft:deliver_report", new BehaviorFactory() {

            @Override
            public EntityAIBase create(EntityRobolCraft entity, JsonObject parameters) {
                if (!(entity instanceof EntityShiftSupervisor)) {
                    return null;
                }
                EntityAIDeliverReport ai = new EntityAIDeliverReport((EntityShiftSupervisor) entity);
                ((EntityShiftSupervisor) entity).attachDeliver(ai);
                return ai;
            }
        });
        register("robolcraft:look", new BehaviorFactory() {

            @Override
            public EntityAIBase create(EntityRobolCraft entity, JsonObject parameters) {
                float range = NpcWorldFile.numberParam(entity, LOOK, "range", 6.0F);
                return new EntityAIWatchClosest(entity, EntityPlayer.class, range);
            }
        });
    }

    public static boolean register(String name, BehaviorFactory factory) {
        if (name == null || factory == null || name.length() == 0) {
            return false;
        }
        BEHAVIORS.put(name, factory);
        return true;
    }

    public static boolean hasBehavior(String name) {
        registerDefaults();
        return name != null && BEHAVIORS.containsKey(name);
    }

    public static boolean knownSlot(String role, String slot) {
        if ("global".equals(role)) {
            return slotOf(WORKER_SLOTS, slot) || slotOf(SUPERVISOR_SLOTS, slot)
                || "sounds".equals(slot)
                || "schedule".equals(slot);
        }
        return slotOf(slotsFor(role), slot);
    }

    public static void install(EntityRobolCraft entity) {
        registerDefaults();
        entity.clearInstalledTasks();
        String role = entity.getNpcRole();
        String[] slots = slotsFor(role);
        for (int i = 0; i < slots.length; i++) {
            String slot = slots[i];
            if (!NpcWorldFile.slotEnabled(entity, slot)) {
                continue;
            }
            String behavior = NpcWorldFile.slotBehavior(entity, slot, defaultBehavior(role, slot));
            JsonObject params = NpcWorldFile.slotParameters(entity, slot);
            logUnknownParams(slot, behavior, params);
            int priority = priority(role, slot);
            if ("robolcraft:doors".equals(behavior)) {
                add(entity, priority, new EntityAIOpenDoor(entity, true));
                add(entity, priority, new EntityAIOpenWoodenTrapDoor(entity, false));
                add(entity, priority, new EntityAIOpenMalisisDoor(entity, true));
                continue;
            }
            BehaviorFactory factory = BEHAVIORS.get(behavior);
            if (factory == null) {
                com.angelika.robolcraft.RobolCraftMod.LOG.warn("No factory for behavior {}.", behavior);
                continue;
            }
            EntityAIBase ai = factory.create(entity, params);
            if (ai != null) {
                add(entity, priority, ai);
            }
        }
    }

    private static void add(EntityRobolCraft entity, int priority, EntityAIBase ai) {
        entity.tasks.addTask(priority, ai);
        entity.trackInstalledTask(ai);
    }

    private static void logUnknownParams(String slot, String behavior, JsonObject params) {
        if (params == null || !behavior.startsWith("robolcraft:")) {
            return;
        }
        for (Map.Entry<String, JsonElement> field : params.entrySet()) {
            if (!knownParam(slot, field.getKey())) {
                NpcWorldFile.logUnknown(slot, behavior, field.getKey());
            }
        }
    }

    private static boolean knownParam(String slot, String key) {
        if ("enabled".equals(key) || "behavior".equals(key) || "by".equals(key) || "v".equals(key)) {
            return true;
        }
        if (PANIC.equals(slot) && "speed".equals(key)) {
            return true;
        }
        if (HEAL.equals(slot) && "threshold".equals(key)) {
            return true;
        }
        if (LOOK.equals(slot) && "range".equals(key)) {
            return true;
        }
        return false;
    }

    private static String defaultBehavior(String role, String slot) {
        if (DAY_WORK.equals(slot) && "supervisor".equals(role)) {
            return "robolcraft:supervise";
        }
        if (DELIVER_REPORT.equals(slot)) {
            return "robolcraft:deliver_report";
        }
        if (SWIM.equals(slot)) {
            return "robolcraft:swim";
        }
        if (DOORS.equals(slot)) {
            return "robolcraft:doors";
        }
        if (PANIC.equals(slot)) {
            return "robolcraft:panic";
        }
        if (HEAL.equals(slot)) {
            return "robolcraft:heal";
        }
        if (COMBAT.equals(slot)) {
            return "robolcraft:combat";
        }
        if (NIGHT.equals(slot)) {
            return "robolcraft:night";
        }
        if (RETURN_LOCKER.equals(slot)) {
            return "robolcraft:return_locker";
        }
        if (BREAKTIME.equals(slot)) {
            return "robolcraft:breaktime";
        }
        if (DAY_WORK.equals(slot)) {
            return "robolcraft:wander";
        }
        if (LOOK.equals(slot)) {
            return "robolcraft:look";
        }
        return "robolcraft:" + slot;
    }

    private static int priority(String role, String slot) {
        if ("supervisor".equals(role)) {
            if (DELIVER_REPORT.equals(slot)) {
                return 6;
            }
            if (BREAKTIME.equals(slot)) {
                return 7;
            }
            if (DAY_WORK.equals(slot)) {
                return 8;
            }
        }
        if (SWIM.equals(slot)) {
            return 0;
        }
        if (DOORS.equals(slot) || PANIC.equals(slot)) {
            return 1;
        }
        if (HEAL.equals(slot)) {
            return 2;
        }
        if (COMBAT.equals(slot)) {
            return 3;
        }
        if (NIGHT.equals(slot)) {
            return 4;
        }
        if (RETURN_LOCKER.equals(slot)) {
            return 5;
        }
        if (BREAKTIME.equals(slot)) {
            return 6;
        }
        if (DAY_WORK.equals(slot)) {
            return 7;
        }
        return 8;
    }

    private static String[] slotsFor(String role) {
        if ("supervisor".equals(role)) {
            return SUPERVISOR_SLOTS;
        }
        if (role != null && !ROLE_SLOTS.isEmpty() && ROLE_SLOTS.containsKey(role)) {
            return ROLE_SLOTS.get(role);
        }
        return WORKER_SLOTS;
    }

    private static final Map<String, String[]> ROLE_SLOTS = new HashMap<String, String[]>();

    public static void registerRoleSlots(String role, String[] slots) {
        if (role != null && slots != null) {
            ROLE_SLOTS.put(role, slots);
        }
    }

    private static boolean slotOf(String[] slots, String slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].equals(slot)) {
                return true;
            }
        }
        return false;
    }
}
