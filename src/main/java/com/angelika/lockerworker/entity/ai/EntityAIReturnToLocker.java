package com.angelika.lockerworker.entity.ai;

import net.minecraft.entity.ai.EntityAIBase;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.util.PathToward;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * Return / enter-locker AI: {@link WorkerSchedule.Phase#LOCKER}
 * ({@code t in [12000, 23999]}) <b>or</b> {@code forcedStayAtLocker}.
 *
 * <p>
 * During LOCKER phase, when the worker has arrived near the home locker
 * ({@code distSq <= 2.25} / ~1.5 blocks — same as {@link #isStandingAtLocker},
 * covering the stand-in-front pad) the worker <b>enters</b> (despawns into the
 * locker). Forced-stay outside LOCKER still stands at the locker until toggled off.
 *
 * <p>
 * Long-range home pathing uses {@link PathToward} (waypoint steps). Night / forced
 * return is <b>never</b> gated by {@code maxDistanceFromLocker}. Keep trying until
 * arrived / entered.
 */
public class EntityAIReturnToLocker extends EntityAIBase {

    /** Same horizontal radius as {@link #isStandingAtLocker} (~1.5 blocks). */
    public static final double ENTER_RANGE_SQ = 2.25D;

    private final EntityLockerWorker worker;
    private final PathToward.Tracker pathToward = new PathToward.Tracker();

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
        return dx * dx + dz * dz < ENTER_RANGE_SQ;
    }

    /**
     * Near enough to enter overnight: horizontal distSq ≤ 2.25 (~1.5 blocks) or
     * Chebyshev ≤ 1 from the locker column (includes the stand pad in front).
     */
    public boolean isInEnterRange() {
        if (!worker.hasHomeLocker()) {
            return false;
        }
        double lockerX = worker.getHomeX() + 0.5;
        double lockerZ = worker.getHomeZ() + 0.5;
        double dx = worker.posX - lockerX;
        double dz = worker.posZ - lockerZ;
        if (dx * dx + dz * dz <= ENTER_RANGE_SQ) {
            return true;
        }
        int bx = net.minecraft.util.MathHelper.floor_double(worker.posX);
        int bz = net.minecraft.util.MathHelper.floor_double(worker.posZ);
        int cheb = Math.max(Math.abs(bx - worker.getHomeX()), Math.abs(bz - worker.getHomeZ()));
        return cheb <= 1;
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        pathToward.reset();
    }

    @Override
    public void resetTask() {
        worker.getNavigator()
            .clearPathEntity();
        pathToward.reset();
    }

    @Override
    public void updateTask() {
        double lockerX = worker.getHomeX() + 0.5;
        double lockerY = worker.getHomeY();
        double lockerZ = worker.getHomeZ() + 0.5;

        double dx = worker.posX - lockerX;
        double dz = worker.posZ - lockerZ;
        double distSq = dx * dx + dz * dz;

        // LOCKER phase: arrived near home locker → always enter/despawn (never stand overnight)
        if (WorkerSchedule.isLocker(worker.worldObj) && isInEnterRange()) {
            // Drop combat so attack AI cannot keep the worker outside
            if (worker.getAttackTarget() != null) {
                worker.setAttackTarget(null);
            }
            worker.getNavigator()
                .clearPathEntity();
            worker.enterLockerForNight();
            return;
        }

        // Forced-stay outside LOCKER: stand and face locker when close
        if (distSq < ENTER_RANGE_SQ) {
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

        // Unlimited return — no maxDistanceFromLocker gate; waypoint path forever
        PathToward.tryMoveToward(worker, pathToward, lockerX, lockerY, lockerZ, Config.getPathSpeed());
        worker.getLookHelper()
            .setLookPosition(lockerX, lockerY + 1.0, lockerZ, 30.0F, 30.0F);
    }
}
