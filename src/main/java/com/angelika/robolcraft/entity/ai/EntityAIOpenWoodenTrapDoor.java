package com.angelika.robolcraft.entity.ai;

import net.minecraft.block.Block;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.MathHelper;

import com.angelika.robolcraft.compat.MalisisDoorsCompat;

/**
 * Opens wooden trapdoors so workers/supervisors can pass hatches and wall
 * trapdoors. Iron-material trapdoors are ignored. Triggered on horizontal
 * collision or when the active path points at a flat (closed) wooden trapdoor —
 * does not flip floor panels the entity is merely standing on.
 * Malisis trapdoors use TE {@code openOrCloseDoor} instead of {@code func_150120_a}.
 */
public class EntityAIOpenWoodenTrapDoor extends EntityAIBase {

    private final EntityLiving entity;
    private final boolean closeAfter;
    private int doorX;
    private int doorY;
    private int doorZ;
    private BlockTrapDoor trapDoor;
    private int closeDelay;
    private boolean hasStopped;
    private float enterDX;
    private float enterDZ;

    public EntityAIOpenWoodenTrapDoor(EntityLiving entity, boolean closeAfter) {
        this.entity = entity;
        this.closeAfter = closeAfter;
    }

    @Override
    public boolean shouldExecute() {
        PathNavigate nav = entity.getNavigator();
        PathEntity path = nav.getPath();
        if (path != null && !path.isFinished() && nav.getCanBreakDoors()) {
            int lim = Math.min(path.getCurrentPathIndex() + 2, path.getCurrentPathLength());
            for (int i = path.getCurrentPathIndex(); i < lim; i++) {
                PathPoint pt = path.getPathPointFromIndex(i);
                if (entity.getDistanceSq(pt.xCoord + 0.5D, entity.posY, pt.zCoord + 0.5D) > 2.25D) {
                    continue;
                }
                if (trySetTarget(pt.xCoord, pt.yCoord, pt.zCoord) || trySetTarget(pt.xCoord, pt.yCoord + 1, pt.zCoord)
                    || trySetTarget(pt.xCoord, pt.yCoord - 1, pt.zCoord)) {
                    return true;
                }
            }
        }
        if (!entity.isCollidedHorizontally) {
            return false;
        }
        int bx = MathHelper.floor_double(entity.posX);
        int by = MathHelper.floor_double(entity.posY);
        int bz = MathHelper.floor_double(entity.posZ);
        int[][] offs = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 0, 0 } };
        for (int[] o : offs) {
            for (int dy = 0; dy <= 1; dy++) {
                if (trySetTarget(bx + o[0], by + dy, bz + o[1])) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean trySetTarget(int x, int y, int z) {
        // Malisis trapdoors: TE path (func_150120_a only sets powered)
        if (MalisisDoorsCompat.canOpen(entity.worldObj, x, y, z)) {
            doorX = x;
            doorY = y;
            doorZ = z;
            trapDoor = null;
            return true;
        }
        BlockTrapDoor td = findWoodenTrapDoor(x, y, z);
        if (td == null || !isFlatClosed(x, y, z)) {
            return false;
        }
        doorX = x;
        doorY = y;
        doorZ = z;
        trapDoor = td;
        return true;
    }

    private BlockTrapDoor findWoodenTrapDoor(int x, int y, int z) {
        Block block = entity.worldObj.getBlock(x, y, z);
        if (!(block instanceof BlockTrapDoor) && block != Blocks.trapdoor) {
            return null;
        }
        if (!(block instanceof BlockTrapDoor)) {
            return null;
        }
        BlockTrapDoor td = (BlockTrapDoor) block;
        if (td.getMaterial() == Material.iron) {
            return null;
        }
        return td;
    }

    /** Flat state (bit 4 clear) — swing open so the entity can pass the opening. */
    private boolean isFlatClosed(int x, int y, int z) {
        int meta = entity.worldObj.getBlockMetadata(x, y, z);
        return !BlockTrapDoor.func_150118_d(meta);
    }

    @Override
    public boolean continueExecuting() {
        return closeAfter && closeDelay > 0 && !hasStopped;
    }

    @Override
    public void startExecuting() {
        closeDelay = 20;
        hasStopped = false;
        enterDX = (float) ((doorX + 0.5F) - entity.posX);
        enterDZ = (float) ((doorZ + 0.5F) - entity.posZ);
        if (MalisisDoorsCompat.tryOpen(entity.worldObj, doorX, doorY, doorZ)) {
            return;
        }
        if (trapDoor != null) {
            trapDoor.func_150120_a(entity.worldObj, doorX, doorY, doorZ, true);
        }
    }

    @Override
    public void resetTask() {
        if (closeAfter) {
            if (MalisisDoorsCompat.tryClose(entity.worldObj, doorX, doorY, doorZ)) {
                return;
            }
            if (trapDoor != null) {
                trapDoor.func_150120_a(entity.worldObj, doorX, doorY, doorZ, false);
            }
        }
    }

    @Override
    public void updateTask() {
        --closeDelay;
        float f = (float) ((doorX + 0.5F) - entity.posX);
        float f1 = (float) ((doorZ + 0.5F) - entity.posZ);
        if (enterDX * f + enterDZ * f1 < 0.0F) {
            hasStopped = true;
        }
    }
}
