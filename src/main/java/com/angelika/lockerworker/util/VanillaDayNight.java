package com.angelika.lockerworker.util;

import net.minecraft.world.World;

/**
 * Explicit Overworld tick-window day/night gate for locker-worker AI.
 *
 * <p>
 * <b>Chosen check:</b> {@code world.getWorldTime() % 24000} — NOT
 * {@link World#isDaytime()} / skylight. Vanilla {@code isDaytime()} follows
 * {@code skylightSubtracted < 4}, which can lag or disagree with
 * {@code /time set} (e.g. {@code /time set 20000} still looked like day to
 * skylight while players expected night). Worker AI must react to the
 * commanded tick immediately and forever every cycle.
 *
 * <p>
 * <b>Chosen Overworld window</b> ({@code t = world.getWorldTime() % 24000}):
 * <ul>
 * <li><b>Night</b> when {@code t >= 12000 && t < 23000} — covers 13000, 18000,
 * 20000, 22000 continuously (sunset band through late night)</li>
 * <li><b>Day</b> otherwise: {@code t < 12000 || t >= 23000} — sunrise band
 * 23000–23999 and morning/afternoon 0–11999</li>
 * </ul>
 *
 * <p>
 * Do <b>not</b> call {@code world.isDaytime()} from worker AI.
 *
 * @see com.angelika.lockerworker.entity.ai.EntityAIWanderNearMachines
 * @see com.angelika.lockerworker.entity.ai.EntityAIReturnToLocker
 */
public final class VanillaDayNight {

    /** Inclusive start of night in ticks-of-day ({@code worldTime % 24000}). */
    public static final int NIGHT_START = 12000;

    /** Exclusive end of night / start of sunrise band. */
    public static final int NIGHT_END = 23000;

    private VanillaDayNight() {}

    /**
     * Day = morning/afternoon + sunrise band: {@code t < 12000 || t >= 23000}.
     *
     * @return {@code true} during day; {@code false} at night
     */
    public static boolean isDaytime(World world) {
        if (world == null) {
            return false;
        }
        long t = world.getWorldTime() % 24000L;
        return t < NIGHT_START || t >= NIGHT_END;
    }

    /**
     * Night = {@code t >= 12000 && t < 23000} (e.g. {@code /time set 20000}).
     *
     * @return {@code true} during night; {@code false} during day
     */
    public static boolean isNighttime(World world) {
        if (world == null) {
            return false;
        }
        long t = world.getWorldTime() % 24000L;
        return t >= NIGHT_START && t < NIGHT_END;
    }
}
