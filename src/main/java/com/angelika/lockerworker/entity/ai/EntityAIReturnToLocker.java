package com.angelika.lockerworker.entity.ai;

import net.minecraft.entity.ai.EntityAIBase;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * Return / enter-locker AI: {@link WorkerSchedule.Phase#LOCKER}
 * ({@code t in [12000, 23999]}) <b>or</b> {@code forcedStayAtLocker}.
 *
 * <p>
 * During LOCKER phase, when within 1 block of the home locker the worker
 * <b>enters</b> (despawns into the locker) — does not stand outside all night.
 * Forced-stay outside LOCKER still stands at the locker until toggled off.
 */
public class EntityAIReturnToLocker extends EntityAIBase {

    private final EntityLockerWorker worker;
    private int repathCooldown;

    public EntityAIReturnToLocker(EntityLockerWorker worker) {
        this.worker = worker;
        setMutexBits(1); // move — shared with wander / break
    }

    @Override
    public boolean shouldExecute() {
        if (!WorkerSchedule.isLocker(worker.worldObj) && !worker.isForcedStayAtLocker()) {
            return false;
        }
        return worker.hasHomeLocker() && worker.worldObj.provider.dimensionId == worker.getHomeDim();
    }

    /** True when close enough to stand in front of the locker (forced-stay day). */
    public boolean isStandingAtLocker() {
        if (!worker.hasHomeLocker()) {
            return false;
        }
        double standX = worker.getHomeX() + 0.5;
        double standZ = worker.getHomeZ() + 0.5;
        double dx = worker.posX - standX;
        double dz = worker.posZ - standZ;
        return dx * dx + dz * dz < 2.25;
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void resetTask() {
        worker.getNavigator()
            .clearPathEntity();
        repathCooldown = 0;
    }

    @Override
    public void updateTask() {
        double lockerX = worker.getHomeX() + 0.5;
        double lockerY = worker.getHomeY();
        double lockerZ = worker.getHomeZ() + 0.5;

        double dx = worker.posX - lockerX;
        double dz = worker.posZ - lockerZ;
        double distSq = dx * dx + dz * dz;

        // LOCKER phase: within 1 block → enter/despawn into locker (default night vanish)
        if (WorkerSchedule.isLocker(worker.worldObj) && distSq <= 1.0D) {
            worker.enterLockerForNight();
            return;
        }

        if (distSq < 2.25) {
            // Forced-stay (or approaching): stand and face locker
            worker.getNavigator()
                .clearPathEntity();
            worker.getLookHelper()
                .setLookPosition(
                    worker.getHomeX() + 0.5,
                    worker.getHomeY() + 1.0,
                    worker.getHomeZ() + 0.5,
                    30.0F,
                    30.0F);
            return;
        }

        if (repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        repathCooldown = 20;
        worker.getNavigator()
            .tryMoveToXYZ(lockerX, lockerY, lockerZ, Config.getPathSpeed());
    }
}
