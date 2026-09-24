package com.angelika.lockerworker.entity.ai;

import net.minecraft.entity.ai.EntityAIBase;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.util.PathToward;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * Forced-stay return AI (day/break): path to locker and stand.
 *
 * <p>
 * LOCKER-phase night/bed/morning is handled by {@link EntityAINightRoutine}.
 * Long-range home pathing uses {@link PathToward} (never gated by
 * {@code maxDistanceFromLocker}).
 */
public class EntityAIReturnToLocker extends EntityAIBase {

    /** Same horizontal radius as stand pad (~1.5 blocks). */
    public static final double ENTER_RANGE_SQ = 2.25D;

    private final EntityLockerWorker worker;
    private final PathToward.Tracker pathToward = new PathToward.Tracker();

    public EntityAIReturnToLocker(EntityLockerWorker worker) {
        this.worker = worker;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        // Night routine owns LOCKER phase
        if (WorkerSchedule.isLocker(worker.worldObj)) {
            return false;
        }
        if (worker.isLyingInBed()) {
            return false;
        }
        if (!worker.isForcedStayAtLocker()) {
            return false;
        }
        return worker.hasHomeLocker() && worker.worldObj.provider.dimensionId == worker.getHomeDim();
    }

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

    public boolean isInEnterRange() {
        return isStandingAtLocker();
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

        PathToward.tryMoveToward(worker, pathToward, lockerX, lockerY, lockerZ, Config.getPathSpeed());
        worker.getLookHelper()
            .setLookPosition(lockerX, lockerY + 1.0, lockerZ, 30.0F, 30.0F);
    }
}
