package com.angelika.robolcraft.entity.ai;

import net.minecraft.entity.ai.EntityAIBase;

import com.angelika.robolcraft.Config;
import com.angelika.robolcraft.entity.EntityRobolCraft;
import com.angelika.robolcraft.util.MoveToward;
import com.angelika.robolcraft.util.StandPoints;
import com.angelika.robolcraft.util.WorkerSchedule;

/**
 * Forced-stay return AI (day/break): path to locker stand cell and stand.
 *
 * <p>
 * LOCKER-phase night/bed/morning is handled by {@link EntityAINightRoutine}.
 * Long-range home pathing uses {@link MoveToward} (never gated by
 * {@code maxDistanceFromLocker}).
 */
public class EntityAIReturnToLocker extends EntityAIBase {

    /** Same horizontal radius as stand pad (~1.5 blocks). */
    public static final double ENTER_RANGE_SQ = MoveToward.ARRIVE_RANGE_SQ;

    /**
     * Max |ΔY| from locker block Y to count as same floor (stand beside locker,
     * not on a floor above looking down).
     */
    public static final double SAME_FLOOR_Y_SLACK = MoveToward.SAME_FLOOR_Y_SLACK;

    private final EntityRobolCraft worker;
    private final MoveToward.Tracker pathToward = new MoveToward.Tracker();

    private double standX;
    private double standY;
    private double standZ;
    private boolean hasStand;

    public EntityAIReturnToLocker(EntityRobolCraft worker) {
        this.worker = worker;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (worker.isChangingClothes()) {
            return false;
        }
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
        ensureStand();
        double dx = worker.posX - standX;
        double dz = worker.posZ - standZ;
        if (dx * dx + dz * dz >= ENTER_RANGE_SQ) {
            return false;
        }
        return Math.abs(worker.posY - standY) <= SAME_FLOOR_Y_SLACK;
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
        hasStand = false;
        ensureStand();
    }

    @Override
    public void resetTask() {
        worker.getNavigator()
            .clearPathEntity();
        pathToward.reset();
        hasStand = false;
    }

    @Override
    public void updateTask() {
        ensureStand();

        if (isStandingAtLocker()) {
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

        MoveToward.tryMoveToward(worker, pathToward, standX, standY, standZ, Config.getPathSpeed());
        worker.getLookHelper()
            .setLookPosition(standX, standY + 1.0, standZ, 30.0F, 30.0F);
    }

    private void ensureStand() {
        if (hasStand) {
            return;
        }
        double[] s = StandPoints
            .nearestBeside(worker.worldObj, worker.getHomeX(), worker.getHomeY(), worker.getHomeZ(), worker);
        standX = s[0];
        standY = s[1];
        standZ = s[2];
        hasStand = true;
        pathToward.reset();
    }
}
