package com.angelika.lockerworker.util;

import net.minecraft.world.World;

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
