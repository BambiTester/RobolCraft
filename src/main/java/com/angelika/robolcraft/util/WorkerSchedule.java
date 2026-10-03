package com.angelika.robolcraft.util;

import net.minecraft.world.World;

import com.angelika.robolcraft.entity.EntityRobolCraft;

/**
 * Explicit Overworld tick-window schedule for locker-worker AI (v7).
 *
 * <p>
 * Uses {@code t = world.getWorldTime() % 24000} — NOT {@link World#isDaytime()} /
 * skylight. Phases:
 * <ul>
 * <li>{@link Phase#LOCKER} — {@code t in [12000, 23999]} (full night through sunrise
 * band; was exclusive of 23000–23999 in v6)</li>
 * <li>{@link Phase#BREAK} — {@code t in [6000, 8000]} inclusive</li>
 * <li>{@link Phase#WORK} — {@code t in [0, 5999] OR [8001, 11999]}</li>
 * </ul>
 */
public final class WorkerSchedule {

    public enum Phase {
        LOCKER,
        WORK,
        BREAK
    }

    /** Inclusive start of locker / night stay. */
    public static final int LOCKER_START = 12000;

    /** Inclusive start of break. */
    public static final int BREAK_START = 6000;

    /** Inclusive end of break. */
    public static final int BREAK_END = 8000;

    private WorkerSchedule() {}

    /** Tick-of-day in {@code [0, 23999]}. */
    public static long tickOfDay(World world) {
        if (world == null) {
            return 0L;
        }
        long t = world.getWorldTime() % 24000L;
        if (t < 0L) {
            t += 24000L;
        }
        return t;
    }

    public static Phase phase(World world) {
        long t = tickOfDay(world);
        if (t >= LOCKER_START) {
            return Phase.LOCKER;
        }
        if (t >= BREAK_START && t <= BREAK_END) {
            return Phase.BREAK;
        }
        return Phase.WORK;
    }

    public static Phase phase(EntityRobolCraft entity) {
        if (entity == null || entity.worldObj == null) {
            return Phase.WORK;
        }
        long t = tickOfDay(entity.worldObj);
        int[] locker = com.angelika.robolcraft.npc.NpcWorldFile.scheduleWindow(entity, "locker");
        int lockerStart = locker == null ? LOCKER_START : locker[0];
        int lockerEnd = locker == null ? 23999 : locker[1];
        if (t >= lockerStart && t <= lockerEnd) {
            return skipOr(entity, "locker", Phase.LOCKER);
        }
        int[] brk = com.angelika.robolcraft.npc.NpcWorldFile.scheduleWindow(entity, "break");
        int breakStart = brk == null ? BREAK_START : brk[0];
        int breakEnd = brk == null ? BREAK_END : brk[1];
        if (t >= breakStart && t <= breakEnd) {
            return skipOr(entity, "break", Phase.BREAK);
        }
        return skipOr(entity, "work", Phase.WORK);
    }

    private static Phase skipOr(EntityRobolCraft entity, String name, Phase phase) {
        if (com.angelika.robolcraft.npc.NpcWorldFile.scheduleSkips(entity, name)) {
            return Phase.WORK;
        }
        return phase;
    }

    public static boolean isLocker(EntityRobolCraft entity) {
        return phase(entity) == Phase.LOCKER;
    }

    public static boolean isWork(EntityRobolCraft entity) {
        return phase(entity) == Phase.WORK;
    }

    public static boolean isBreak(EntityRobolCraft entity) {
        return phase(entity) == Phase.BREAK;
    }

    public static boolean isShift(EntityRobolCraft entity) {
        return phase(entity) != Phase.LOCKER;
    }

    public static boolean isLocker(World world) {
        return phase(world) == Phase.LOCKER;
    }

    public static boolean isWork(World world) {
        return phase(world) == Phase.WORK;
    }

    public static boolean isBreak(World world) {
        return phase(world) == Phase.BREAK;
    }

    /**
     * Shift reporting window: morning leave locker through tick 12000
     * (WORK + BREAK). Overnight afterwork/bed is not shift.
     */
    public static boolean isShift(World world) {
        return !isLocker(world);
    }
}
