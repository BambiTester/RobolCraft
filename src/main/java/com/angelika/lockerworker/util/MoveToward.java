package com.angelika.lockerworker.util;

import net.minecraft.entity.EntityLiving;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * Long-range ground pathing for vanilla 1.7.10 {@code PathNavigate} (RobolCraft 1.0.2).
 *
 * <p>
 * Paths via short waypoints when beyond
 * {@link #DIRECT_RANGE}, prefers destination-floor ground Y, hops when pressed against
 * a partial block (GT pipes), and teleports onto the final aim only after
 * {@link #TELEPORT_TICKS} with no real progress toward that aim (chunk must be loaded).
 *
 * <p>
 * Call {@link #tryMoveToward} every tick while traveling. Navigator speed should be
 * {@link com.angelika.lockerworker.Config#getPathSpeed()} (1.0) with attribute base
 * {@code walkingSpeed} (default 0.32) — never multiply twice.
 */
public final class MoveToward {

    /** Path directly to dest when closer than this (horizontal blocks). */
    public static final double DIRECT_RANGE = 20.0D;

    /** Intermediate waypoint step toward dest when beyond {@link #DIRECT_RANGE}. */
    public static final double WAYPOINT_STEP = 16.0D;

    /** Alias for callers deciding "far". */
    public static final double LONG_RANGE_THRESHOLD = 32.0D;

    /** Ticks between repath attempts while moving. */
    public static final int REPATH_INTERVAL = 20;

    /** No meaningful progress toward final aim for this many ticks → hop once. */
    public static final int HOP_TICKS = 40; // 2s

    /**
     * No meaningful progress toward final aim for this many ticks → teleport (last resort).
     * ~30 seconds at 20 tps.
     */
    public static final int TELEPORT_TICKS = 600;

    /** Squared move threshold for "barely moved" (~0.5 blocks). */
    public static final double STUCK_MOVE_EPS_SQ = 0.25D;

    /** Arrived when horizontal distSq to dest is below this (~1.5 blocks). */
    public static final double ARRIVE_RANGE_SQ = 2.25D;

    /** Same-floor Y slack when checking arrival with Y. */
    public static final double SAME_FLOOR_Y_SLACK = 1.5D;

    private MoveToward() {}

    /** Per-caller progress / stuck state (one instance per AI using this helper). */
    public static final class Tracker {

        public int repathCooldown;
        public int noProgressTicks;
        public double lastX;
        public double lastZ;
        public boolean hasLast;
        public double destX, destY, destZ;
        public boolean hasDest;
        public double cachedTargetX, cachedTargetY, cachedTargetZ;
        public boolean hasCachedTarget;
        public boolean hoppedThisStuck;

        /** Compat alias used by older AI stuck checks. */
        public int stuckTicks;

        public void reset() {
            repathCooldown = 0;
            noProgressTicks = 0;
            stuckTicks = 0;
            hasLast = false;
            hasDest = false;
            hasCachedTarget = false;
            hoppedThisStuck = false;
        }
    }

    /**
     * Path toward {@code (destX, destY, destZ)}.
     *
     * @return {@code false} if arrived; {@code true} if still traveling
     */
    public static boolean tryMoveToward(EntityLiving entity, Tracker tracker, double destX, double destY, double destZ,
        double speed) {
        if (entity == null || tracker == null || entity.worldObj == null) {
            return false;
        }

        double dx = destX - entity.posX;
        double dz = destZ - entity.posZ;
        double distSq = dx * dx + dz * dz;

        if (distSq <= ARRIVE_RANGE_SQ && Math.abs(entity.posY - destY) <= SAME_FLOOR_Y_SLACK) {
            entity.getNavigator()
                .clearPathEntity();
            tracker.noProgressTicks = 0;
            tracker.stuckTicks = 0;
            tracker.hasLast = false;
            tracker.repathCooldown = 0;
            tracker.hoppedThisStuck = false;
            return false;
        }
        // Horizontal arrive only if already on same floor band (XZ-only arrival was wrong-floor bug)
        if (distSq <= ARRIVE_RANGE_SQ) {
            // Wrong floor — keep pathing / allow teleport later
        }

        double dist = Math.sqrt(distSq);
        updateProgress(entity, tracker, destX, destZ);

        // Last resort teleport to final aim
        if (tracker.noProgressTicks >= TELEPORT_TICKS) {
            if (teleportToAim(entity, destX, destY, destZ)) {
                tracker.reset();
                return false; // treat as arrived after teleport
            }
        }

        // Hop when pressed / no progress for a few seconds
        if (tracker.noProgressTicks >= HOP_TICKS && !tracker.hoppedThisStuck && entity.onGround) {
            hop(entity);
            tracker.hoppedThisStuck = true;
        }

        boolean forceRepath = tracker.noProgressTicks >= HOP_TICKS && (tracker.noProgressTicks % REPATH_INTERVAL == 0);

        if (tracker.repathCooldown > 0 && !forceRepath) {
            tracker.repathCooldown--;
            return true;
        }

        boolean destUnchanged = tracker.hasDest && almostEq(tracker.destX, destX)
            && almostEq(tracker.destY, destY)
            && almostEq(tracker.destZ, destZ);
        if (!forceRepath && destUnchanged
            && entity.getNavigator()
                .getPath() != null
            && !entity.getNavigator()
                .noPath()) {
            tracker.repathCooldown = REPATH_INTERVAL;
            return true;
        }

        double targetX;
        double targetY;
        double targetZ;
        if (dist > DIRECT_RANGE) {
            double step = Math.min(WAYPOINT_STEP, dist);
            if (forceRepath) {
                step = Math.min(step, Math.max(8.0D, WAYPOINT_STEP * 0.5D));
            }
            double inv = 1.0D / dist;
            targetX = entity.posX + dx * inv * step;
            targetZ = entity.posZ + dz * inv * step;
            int bx = MathHelper.floor_double(targetX);
            int bz = MathHelper.floor_double(targetZ);
            if (!forceRepath && tracker.hasCachedTarget
                && MathHelper.floor_double(tracker.cachedTargetX) == bx
                && MathHelper.floor_double(tracker.cachedTargetZ) == bz) {
                targetY = tracker.cachedTargetY;
            } else {
                targetY = StandPoints.sampleGroundY(entity.worldObj, targetX, entity.posY, destY, targetZ);
            }
        } else {
            targetX = destX;
            targetY = destY;
            targetZ = destZ;
        }

        boolean ok = entity.getNavigator()
            .tryMoveToXYZ(targetX, targetY, targetZ, speed);
        tracker.repathCooldown = REPATH_INTERVAL;
        tracker.destX = destX;
        tracker.destY = destY;
        tracker.destZ = destZ;
        tracker.hasDest = true;
        tracker.cachedTargetX = targetX;
        tracker.cachedTargetY = targetY;
        tracker.cachedTargetZ = targetZ;
        tracker.hasCachedTarget = true;

        if (forceRepath && !ok && entity.onGround) {
            hop(entity);
        }

        return true;
    }

    /** Horizontal distance from entity to (x, z). */
    public static double horizontalDist(EntityLiving entity, double x, double z) {
        double dx = x - entity.posX;
        double dz = z - entity.posZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean almostEq(double a, double b) {
        return Math.abs(a - b) < 0.05D;
    }

    private static void updateProgress(EntityLiving entity, Tracker tracker, double destX, double destZ) {
        if (!tracker.hasLast) {
            tracker.lastX = entity.posX;
            tracker.lastZ = entity.posZ;
            tracker.hasLast = true;
            tracker.noProgressTicks = 0;
            tracker.stuckTicks = 0;
            return;
        }
        double movedX = entity.posX - tracker.lastX;
        double movedZ = entity.posZ - tracker.lastZ;
        double movedSq = movedX * movedX + movedZ * movedZ;

        // Progress = getting closer to final aim OR moving meaningfully
        double oldDist = horiz(tracker.lastX, tracker.lastZ, destX, destZ);
        double newDist = horiz(entity.posX, entity.posZ, destX, destZ);
        boolean closer = newDist + 0.15D < oldDist;

        if (movedSq < STUCK_MOVE_EPS_SQ && !closer) {
            tracker.noProgressTicks++;
            tracker.stuckTicks = tracker.noProgressTicks;
        } else {
            tracker.noProgressTicks = 0;
            tracker.stuckTicks = 0;
            tracker.hoppedThisStuck = false;
            tracker.lastX = entity.posX;
            tracker.lastZ = entity.posZ;
        }
    }

    private static double horiz(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void hop(EntityLiving entity) {
        entity.motionY = 0.42D;
        entity.isAirBorne = true;
    }

    /**
     * Soft teleport onto standable ground at the final aim. Chunk must be loaded.
     *
     * @return true if teleport applied
     */
    public static boolean teleportToAim(EntityLiving entity, double destX, double destY, double destZ) {
        World world = entity.worldObj;
        if (world == null) {
            return false;
        }
        int cx = MathHelper.floor_double(destX) >> 4;
        int cz = MathHelper.floor_double(destZ) >> 4;
        if (!world.getChunkProvider()
            .chunkExists(cx, cz)) {
            return false;
        }
        double ny = StandPoints.sampleGroundY(world, destX, destY, destY, destZ);
        entity.getNavigator()
            .clearPathEntity();
        entity.setPosition(destX, ny, destZ);
        entity.motionX = 0.0D;
        entity.motionY = 0.0D;
        entity.motionZ = 0.0D;
        entity.fallDistance = 0.0F;
        return true;
    }
}
