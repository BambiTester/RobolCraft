package com.angelika.lockerworker.util;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * Long-range ground pathing for vanilla 1.7.10 {@code PathNavigate}.
 *
 * <p>
 * Direct {@code tryMoveToXYZ} fails beyond ~followRange (~16–32 blocks). When the
 * horizontal distance to the destination exceeds {@link #DIRECT_RANGE}, this helper
 * paths to an intermediate waypoint ~{@link #WAYPOINT_STEP} blocks along the vector
 * toward the target, repaths regularly, and applies a stuck failsafe (closer waypoint,
 * then soft ground step as last resort).
 *
 * <p>
 * Used by locker return, BREAK trashcan walks, and WORK machine / leash pathing.
 */
public final class PathToward {

    /** Path directly to dest when closer than this (horizontal blocks). */
    public static final double DIRECT_RANGE = 26.0D;

    /**
     * Horizontal distance treated as "far" for callers that only want a one-shot
     * waypoint decision (~32 as requested). Same behavior as {@link #DIRECT_RANGE}
     * for stepping; documented alias for the problem statement.
     */
    public static final double LONG_RANGE_THRESHOLD = 32.0D;

    /** Intermediate waypoint step toward dest when beyond {@link #DIRECT_RANGE}. */
    public static final double WAYPOINT_STEP = 24.0D;

    /** Ticks between repath attempts while moving. */
    public static final int REPATH_INTERVAL = 20;

    /** No meaningful horizontal progress for this many ticks → stuck failsafe. */
    public static final int STUCK_TICKS = 100; // 5s

    /** Squared move threshold for "barely moved" (~0.5 blocks). */
    public static final double STUCK_MOVE_EPS_SQ = 0.25D;

    /** Arrived when horizontal distSq to dest is below this (~1.5 blocks). */
    public static final double ARRIVE_RANGE_SQ = 2.25D;

    /** Last-resort soft teleport step toward dest (blocks). Prefer path first. */
    public static final double SOFT_TELEPORT_STEP = 4.0D;

    private PathToward() {}

    /** Per-caller progress / stuck state (one instance per AI using this helper). */
    public static final class Tracker {

        public int repathCooldown;
        public int stuckTicks;
        public double lastX;
        public double lastZ;
        public boolean hasLast;

        public void reset() {
            repathCooldown = 0;
            stuckTicks = 0;
            hasLast = false;
        }
    }

    /**
     * Path toward {@code (destX, destY, destZ)}. Call every tick (or on repath)
     * while traveling.
     *
     * @return {@code false} if arrived (within {@link #ARRIVE_RANGE_SQ});
     *         {@code true} if still traveling / path or soft-step attempted
     */
    public static boolean tryMoveToward(EntityLiving entity, Tracker tracker, double destX, double destY, double destZ,
        double speed) {
        if (entity == null || tracker == null || entity.worldObj == null) {
            return false;
        }

        double dx = destX - entity.posX;
        double dz = destZ - entity.posZ;
        double distSq = dx * dx + dz * dz;

        if (distSq <= ARRIVE_RANGE_SQ) {
            entity.getNavigator()
                .clearPathEntity();
            tracker.stuckTicks = 0;
            tracker.hasLast = false;
            tracker.repathCooldown = 0;
            return false; // arrived
        }

        double dist = Math.sqrt(distSq);
        updateStuck(entity, tracker);
        boolean forceRepath = tracker.stuckTicks >= STUCK_TICKS;

        if (tracker.repathCooldown > 0 && !forceRepath) {
            tracker.repathCooldown--;
            return true;
        }

        double targetX;
        double targetY;
        double targetZ;
        if (dist > DIRECT_RANGE) {
            double step = WAYPOINT_STEP;
            if (forceRepath) {
                step = Math.min(WAYPOINT_STEP, Math.max(12.0D, WAYPOINT_STEP * 0.5D));
            }
            step = Math.min(step, dist);
            double inv = 1.0D / dist;
            targetX = entity.posX + dx * inv * step;
            targetZ = entity.posZ + dz * inv * step;
            targetY = sampleGroundY(entity.worldObj, targetX, entity.posY, destY, targetZ);
        } else {
            targetX = destX;
            targetY = destY;
            targetZ = destZ;
        }

        boolean ok = entity.getNavigator()
            .tryMoveToXYZ(targetX, targetY, targetZ, speed);
        tracker.repathCooldown = REPATH_INTERVAL;

        if (forceRepath) {
            tracker.stuckTicks = 0;
            tracker.lastX = entity.posX;
            tracker.lastZ = entity.posZ;
            tracker.hasLast = true;
            if (!ok) {
                softStepToward(entity, destX, destY, destZ);
            }
        }

        return true;
    }

    /** Horizontal distance from entity to (x, z). */
    public static double horizontalDist(EntityLiving entity, double x, double z) {
        double dx = x - entity.posX;
        double dz = z - entity.posZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Last resort: nudge entity a few blocks toward dest onto solid ground.
     * Prefer waypoint pathing; only when navigator cannot find a path.
     */
    public static void softStepToward(EntityLiving entity, double destX, double destY, double destZ) {
        double dx = destX - entity.posX;
        double dz = destZ - entity.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.05D) {
            return;
        }
        double step = Math.min(SOFT_TELEPORT_STEP, dist);
        double inv = 1.0D / dist;
        double nx = entity.posX + dx * inv * step;
        double nz = entity.posZ + dz * inv * step;
        double ny = sampleGroundY(entity.worldObj, nx, entity.posY, destY, nz);
        if (ny < destY - 8.0D) {
            ny = destY;
        }
        entity.getNavigator()
            .clearPathEntity();
        entity.setPosition(nx, ny, nz);
        entity.motionX = 0.0D;
        entity.motionZ = 0.0D;
        entity.fallDistance = 0.0F;
    }

    private static void updateStuck(EntityLiving entity, Tracker tracker) {
        if (!tracker.hasLast) {
            tracker.lastX = entity.posX;
            tracker.lastZ = entity.posZ;
            tracker.hasLast = true;
            tracker.stuckTicks = 0;
            return;
        }
        double movedX = entity.posX - tracker.lastX;
        double movedZ = entity.posZ - tracker.lastZ;
        if (movedX * movedX + movedZ * movedZ < STUCK_MOVE_EPS_SQ) {
            tracker.stuckTicks++;
        } else {
            tracker.stuckTicks = 0;
            tracker.lastX = entity.posX;
            tracker.lastZ = entity.posZ;
        }
    }

    private static double sampleGroundY(World world, double x, double refY, double destY, double z) {
        int bx = MathHelper.floor_double(x);
        int bz = MathHelper.floor_double(z);
        int startY = MathHelper.floor_double(Math.max(refY, destY) + 2.0D);
        int minY = Math.max(1, MathHelper.floor_double(Math.min(refY, destY) - 8.0D));
        for (int y = startY; y >= minY; y--) {
            Block block = world.getBlock(bx, y, bz);
            if (block == null || block.getMaterial() == Material.air) {
                continue;
            }
            if (!block.getMaterial()
                .blocksMovement()) {
                continue;
            }
            return y + 1.0D;
        }
        return refY;
    }
}
