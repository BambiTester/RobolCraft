package com.angelika.robolcraft.entity.ai;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.MathHelper;

import com.angelika.robolcraft.compat.MalisisDoorsCompat;

/**
 * Opens MalisisDoors (doors, trapdoors, fence gates, garage) via soft reflection.
 * Same path/collision triggers as wooden trapdoor AI. Digicode / redstone-only skipped.
 */
public class EntityAIOpenMalisisDoor extends EntityAIBase {

    private final EntityLiving entity;
    private final boolean closeAfter;
    private int doorX;
    private int doorY;
    private int doorZ;
    private boolean hasTarget;
    private int closeDelay;
    private boolean hasStopped;
    private float enterDX;
    private float enterDZ;

    public EntityAIOpenMalisisDoor(EntityLiving entity, boolean closeAfter) {
        this.entity = entity;
        this.closeAfter = closeAfter;
    }

    @Override
    public boolean shouldExecute() {
        if (!MalisisDoorsCompat.isAvailable()) {
            return false;
        }
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
        if (!MalisisDoorsCompat.canOpen(entity.worldObj, x, y, z)) {
            return false;
        }
        doorX = x;
        doorY = y;
        doorZ = z;
        hasTarget = true;
        return true;
    }

    @Override
    public boolean continueExecuting() {
        return closeAfter && closeDelay > 0 && !hasStopped && hasTarget;
    }

    @Override
    public void startExecuting() {
        closeDelay = 20;
        hasStopped = false;
        enterDX = (float) ((doorX + 0.5F) - entity.posX);
        enterDZ = (float) ((doorZ + 0.5F) - entity.posZ);
        MalisisDoorsCompat.tryOpen(entity.worldObj, doorX, doorY, doorZ);
    }

    @Override
    public void resetTask() {
        if (closeAfter && hasTarget) {
            MalisisDoorsCompat.tryClose(entity.worldObj, doorX, doorY, doorZ);
        }
        hasTarget = false;
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
