package com.angelika.lockerworker.util;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.angelika.lockerworker.compat.MalisisDoorsCompat;

/**
 * Same-floor stand cells beside a target block (locker / machine / trashcan / bed).
 *
 * <p>
 * Prefers the nearest free cardinal face of the bottom block, same Y as that block,
 * with solid floor underfoot and two blocks of headroom. Diagonals only if all four
 * faces are blocked. Falls back to a ground-snapped offset if nothing is free.
 * Never treats GregTech pipes/cables as standable footing ({@link GtHazardBlocks}).
 */
public final class StandPoints {

    private static final int[][] CARDINAL = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
    private static final int[][] DIAGONAL = { { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };

    private StandPoints() {}

    /**
     * Nearest free stand cell beside {@code (bx, by, bz)} relative to {@code from}.
     * {@code by} is the target block's Y (locker bottom / machine / trashcan). Worker
     * stands on the floor under that Y (feet at roughly {@code by}).
     *
     * @return {@code [x, y, z]} entity feet position
     */
    public static double[] nearestBeside(World world, int bx, int by, int bz, Entity from) {
        if (world == null) {
            return new double[] { bx + 0.5D, by, bz + 0.5D };
        }
        int floorY = by - 1;
        double bestDist = Double.MAX_VALUE;
        double[] best = null;

        for (int[] o : CARDINAL) {
            double[] cell = tryCell(world, bx + o[0], floorY, bz + o[1], from);
            if (cell == null) {
                continue;
            }
            double d = distSq(from, cell[0], cell[2]);
            if (d < bestDist) {
                bestDist = d;
                best = cell;
            }
        }
        if (best != null) {
            return best;
        }

        bestDist = Double.MAX_VALUE;
        for (int[] o : DIAGONAL) {
            double[] cell = tryCell(world, bx + o[0], floorY, bz + o[1], from);
            if (cell == null) {
                continue;
            }
            double d = distSq(from, cell[0], cell[2]);
            if (d < bestDist) {
                bestDist = d;
                best = cell;
            }
        }
        if (best != null) {
            return best;
        }

        int ox = from == null ? 1 : (from.posX >= bx + 0.5D ? 1 : -1);
        double gy = sampleGroundY(world, bx + ox + 0.5D, by, by, bz + 0.5D);
        return new double[] { bx + ox + 0.5D, gy, bz + 0.5D };
    }

    /**
     * Snap a roam / orbit point onto standable ground preferring the machine floor.
     * {@code floorY} is the machine block Y — feet land on the surface under/at that band.
     */
    public static double[] snapRoam(World world, double x, double floorY, double z) {
        double gy = sampleGroundY(world, x, floorY, floorY, z);
        return new double[] { x, gy, z };
    }

    private static double[] tryCell(World world, int x, int y, int z, Entity from) {
        if (!isStandable(world, x, y, z)) {
            return null;
        }
        // Occupied by another living entity roughly in this cell
        if (from != null && isCellOccupied(world, x, y, z, from)) {
            return null;
        }
        return new double[] { x + 0.5D, y + 1.0D, z + 0.5D };
    }

    /**
     * Floor at (x,y,z) is solid and the two blocks above are passable.
     * {@code y} is the floor block Y (entity stands at y+1).
     */
    public static boolean isStandable(World world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        if (!isSolidFloor(world, x, y, z)) {
            return false;
        }
        return isPassable(world, x, y + 1, z) && isPassable(world, x, y + 2, z);
    }

    private static boolean isCellOccupied(World world, int x, int y, int z, Entity self) {
        double cx = x + 0.5D;
        double cz = z + 0.5D;
        @SuppressWarnings("unchecked")
        java.util.List<Entity> list = world.getEntitiesWithinAABB(
            Entity.class,
            net.minecraft.util.AxisAlignedBB
                .getBoundingBox(cx - 0.4D, y + 0.1D, cz - 0.4D, cx + 0.4D, y + 1.8D, cz + 0.4D));
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (Entity e : list) {
            if (e == null || e == self || !e.isEntityAlive()) {
                continue;
            }
            if (e instanceof net.minecraft.entity.EntityLivingBase) {
                return true;
            }
        }
        return false;
    }

    private static double distSq(Entity from, double x, double z) {
        if (from == null) {
            return 0.0D;
        }
        double dx = from.posX - x;
        double dz = from.posZ - z;
        return dx * dx + dz * dz;
    }

    /**
     * Standable ground Y at (x,z), preferring the destination floor band.
     * Never prefers a floor above {@code destY}.
     */
    public static double sampleGroundY(World world, double x, double refY, double destY, double z) {
        if (world == null) {
            return destY;
        }
        int bx = MathHelper.floor_double(x);
        int bz = MathHelper.floor_double(z);
        int destBlockY = MathHelper.floor_double(destY);
        int preferStart = destBlockY + 1;
        int minY = Math.max(1, MathHelper.floor_double(Math.min(refY, destY) - 8.0D));

        for (int y = preferStart; y >= minY; y--) {
            if (isSolidFloor(world, bx, y, bz) && isPassable(world, bx, y + 1, bz)) {
                return y + 1.0D;
            }
        }

        int maxY = MathHelper.floor_double(Math.max(refY, destY) + 2.0D);
        for (int y = preferStart + 1; y <= maxY; y++) {
            if (isSolidFloor(world, bx, y, bz) && isPassable(world, bx, y + 1, bz)) {
                return y + 1.0D;
            }
        }
        return destY;
    }

    private static boolean isSolidFloor(World world, int x, int y, int z) {
        if (GtHazardBlocks.isPipeOrCable(world, x, y, z)) {
            return false; // never stand on GT pipes / exposed cables
        }
        Block block = world.getBlock(x, y, z);
        if (block == null || block.getMaterial() == Material.air) {
            return false;
        }
        return block.getMaterial()
            .blocksMovement();
    }

    private static boolean isPassable(World world, int x, int y, int z) {
        if (GtHazardBlocks.isPipeOrCable(world, x, y, z)) {
            return false; // do not path through steam pipes / live cables
        }
        Block block = world.getBlock(x, y, z);
        if (block == null || block.getMaterial() == Material.air) {
            return true;
        }
        // MalisisDoors — treat as passable for stand checks (workers open them via AI)
        if (MalisisDoorsCompat.isMalisisDoorBlock(block)) {
            return true;
        }
        // Doors / trapdoors / ladders / rails / signs — treat as passable for stand checks
        if (!block.getMaterial()
            .blocksMovement()) {
            return true;
        }
        // Partial blocks with small collision (non-GT; GT pipes rejected above)
        try {
            net.minecraft.util.AxisAlignedBB bb = block.getCollisionBoundingBoxFromPool(world, x, y, z);
            if (bb == null) {
                return true;
            }
            double h = bb.maxY - bb.minY;
            if (h < 0.6D) {
                return true;
            }
        } catch (Throwable ignored) {
            // some blocks throw if TE missing
        }
        return false;
    }
}
