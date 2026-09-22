package com.angelika.lockerworker.util;

import net.minecraft.world.World;

/**
 * Legacy day/night helpers — prefer {@link WorkerSchedule#phase(World)} (v7).
 *
 * <p>
 * Still uses {@code world.getWorldTime() % 24000}, NOT {@link World#isDaytime()} /
 * skylight. As of v7, "night" / locker stay is {@code t in [12000, 23999]}
 * (sunrise band included). "Day" is everything else (WORK + BREAK).
 *
 * @see WorkerSchedule
 */
public final class VanillaDayNight {

    /** Inclusive start of locker / night ({@code worldTime % 24000}). */
    public static final int NIGHT_START = WorkerSchedule.LOCKER_START;

    /**
     * Exclusive end of night for documentation of the old v6 window. v7 locker
     * phase runs through 23999; prefer {@link WorkerSchedule}.
     */
    public static final int NIGHT_END = 24000;

    private VanillaDayNight() {}

    /**
     * Day = WORK or BREAK (not LOCKER): {@code t < 12000}.
     */
    public static boolean isDaytime(World world) {
        return !isNighttime(world);
    }

    /**
     * Night / locker stay: {@code t >= 12000} (through 23999 inclusive).
     */
    public static boolean isNighttime(World world) {
        return WorkerSchedule.isLocker(world);
    }
}
