package com.angelika.lockerworker.entity.ai;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.MathHelper;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.block.BlockWorkerBed;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.tileentity.TileEntityLocker;
import com.angelika.lockerworker.util.PathToward;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * v13 night / bed / morning routine. Worker stays in the world (no despawn).
 *
 * <p>
 * LOCKER phase: path home → afterwork outfit + sounds → bed (if linked) → pajamas +
 * lie until tick 0. No bed: stand at locker in afterwork.
 * Morning (leave LOCKER): wake → afterwork walk to locker → work outfit (unless
 * forced stay) → resume day schedule.
 */
public class EntityAINightRoutine extends EntityAIBase {

    public static final double ARRIVE_SQ = EntityAIReturnToLocker.ENTER_RANGE_SQ;

    public enum Stage {
        IDLE,
        GOTO_LOCKER_EVE,
        CHANGE_AFTERWORK,
        GOTO_BED,
        WAIT_BED_FREE,
        CHANGE_PIJAMA,
        SLEEPING,
        STAND_LOCKER_NIGHT,
        WAKE,
        GOTO_LOCKER_MORNING,
        CHANGE_WORK,
        STAND_FORCED_AFTERWORK
    }

    private final EntityLockerWorker worker;
    private final PathToward.Tracker pathToward = new PathToward.Tracker();
    private Stage stage = Stage.IDLE;
    private int stageTicks;
    private boolean morningStarted;

    public EntityAINightRoutine(EntityLockerWorker worker) {
        this.worker = worker;
        setMutexBits(1);
    }

    public Stage getStage() {
        return stage;
    }

    @Override
    public boolean shouldExecute() {
        if (!worker.hasHomeLocker()) {
            return false;
        }
        if (worker.worldObj.provider.dimensionId != worker.getHomeDim()) {
            return false;
        }
        // Death-wait until morning
        if (worker.isWaitingForMorningAfterDeath()) {
            return true;
        }
        // Active night / morning sequence
        if (stage != Stage.IDLE && stage != Stage.STAND_FORCED_AFTERWORK) {
            return true;
        }
        if (WorkerSchedule.isLocker(worker.worldObj)) {
            return true;
        }
        // Forced afterwork stand during day
        if (worker.isForcedStayAtLocker() && worker.getOutfit() == EntityLockerWorker.OUTFIT_AFTERWORK) {
            return true;
        }
        return false;
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        pathToward.reset();
        morningStarted = false;
        if (worker.isWaitingForMorningAfterDeath()) {
            stage = Stage.STAND_LOCKER_NIGHT;
            worker.setOutfit(EntityLockerWorker.OUTFIT_AFTERWORK);
            return;
        }
        if (WorkerSchedule.isLocker(worker.worldObj)) {
            if (worker.isLyingInBed()) {
                stage = Stage.SLEEPING;
            } else if (worker.getOutfit() == EntityLockerWorker.OUTFIT_AFTERWORK) {
                TileEntityLocker te = worker.getHomeLockerTE();
                if (te != null && te.hasLinkedBed()) {
                    stage = Stage.GOTO_BED;
                } else {
                    stage = Stage.STAND_LOCKER_NIGHT;
                }
            } else if (worker.getOutfit() == EntityLockerWorker.OUTFIT_PIJAMA) {
                stage = Stage.SLEEPING;
                worker.lieInBedAtCurrent();
            } else {
                stage = Stage.GOTO_LOCKER_EVE;
            }
        } else if (worker.isForcedStayAtLocker() && worker.getOutfit() == EntityLockerWorker.OUTFIT_AFTERWORK) {
            stage = Stage.STAND_FORCED_AFTERWORK;
        } else {
            stage = Stage.GOTO_LOCKER_EVE;
        }
        stageTicks = 0;
    }

    @Override
    public void resetTask() {
        if (stage != Stage.SLEEPING) {
            worker.getNavigator()
                .clearPathEntity();
        }
        pathToward.reset();
        if (!WorkerSchedule.isLocker(worker.worldObj) && !worker.isForcedStayAtLocker() && stage != Stage.CHANGE_WORK) {
            // Leaving night AI into day work — ensure not stuck lying
            if (worker.isLyingInBed()) {
                worker.wakeFromBed(false);
            }
            stage = Stage.IDLE;
        }
    }

    @Override
    public void updateTask() {
        stageTicks++;
        // Drop combat during night routine
        if (worker.getAttackTarget() != null) {
            worker.setAttackTarget(null);
        }

        boolean lockerPhase = WorkerSchedule.isLocker(worker.worldObj);

        // Morning edge: leave LOCKER
        if (!lockerPhase && !morningStarted
            && (stage == Stage.SLEEPING || stage == Stage.STAND_LOCKER_NIGHT
                || stage == Stage.GOTO_BED
                || stage == Stage.WAIT_BED_FREE
                || stage == Stage.CHANGE_PIJAMA
                || worker.isWaitingForMorningAfterDeath())) {
            morningStarted = true;
            beginMorning();
        }

        switch (stage) {
            case GOTO_LOCKER_EVE:
                tickGoToLocker(true);
                break;
            case CHANGE_AFTERWORK:
                // sounds already fired on enter; brief pause then continue
                if (stageTicks >= 10) {
                    TileEntityLocker te = worker.getHomeLockerTE();
                    if (te != null && te.hasLinkedBed()) {
                        enter(Stage.GOTO_BED);
                    } else {
                        enter(Stage.STAND_LOCKER_NIGHT);
                    }
                }
                break;
            case GOTO_BED:
                tickGoToBed();
                break;
            case WAIT_BED_FREE:
                tickWaitBedFree();
                break;
            case CHANGE_PIJAMA:
                if (stageTicks >= 5) {
                    worker.lieInLinkedBed();
                    enter(Stage.SLEEPING);
                }
                break;
            case SLEEPING:
                tickSleeping();
                break;
            case STAND_LOCKER_NIGHT:
                tickStandAtLocker();
                if (worker.isWaitingForMorningAfterDeath() && !lockerPhase) {
                    worker.setWaitingForMorningAfterDeath(false);
                    TileEntityLocker te = worker.getHomeLockerTE();
                    if (te != null) {
                        te.clearWaitForMorningAfterDeath();
                    }
                    beginMorning();
                }
                break;
            case WAKE:
                if (stageTicks >= 5) {
                    worker.setOutfit(EntityLockerWorker.OUTFIT_AFTERWORK);
                    enter(Stage.GOTO_LOCKER_MORNING);
                }
                break;
            case GOTO_LOCKER_MORNING:
                tickGoToLocker(false);
                break;
            case CHANGE_WORK:
                if (stageTicks >= 10) {
                    enter(Stage.IDLE);
                }
                break;
            case STAND_FORCED_AFTERWORK:
                tickStandAtLocker();
                // When force clears during day → change to work and idle
                if (!lockerPhase && !worker.isForcedStayAtLocker()) {
                    doMorningClothesToWork();
                    enter(Stage.CHANGE_WORK);
                }
                break;
            default:
                break;
        }
    }

    private void beginMorning() {
        if (worker.isLyingInBed()) {
            worker.wakeFromBed(true); // get_up 25%
            enter(Stage.WAKE);
        } else {
            worker.setOutfit(EntityLockerWorker.OUTFIT_AFTERWORK);
            enter(Stage.GOTO_LOCKER_MORNING);
        }
        worker.setWaitingForMorningAfterDeath(false);
        TileEntityLocker te = worker.getHomeLockerTE();
        if (te != null) {
            te.clearWaitForMorningAfterDeath();
        }
    }

    private void enter(Stage next) {
        stage = next;
        stageTicks = 0;
        pathToward.reset();
        worker.getNavigator()
            .clearPathEntity();
    }

    private void tickGoToLocker(boolean evening) {
        double lx = worker.getHomeX() + 0.5;
        double ly = worker.getHomeY();
        double lz = worker.getHomeZ() + 0.5;
        double dx = worker.posX - lx;
        double dz = worker.posZ - lz;
        if (dx * dx + dz * dz <= ARRIVE_SQ || isNearLockerColumn()) {
            worker.getNavigator()
                .clearPathEntity();
            if (evening) {
                doEveningClothesChange();
                enter(Stage.CHANGE_AFTERWORK);
            } else {
                // Morning arrive
                if (worker.isForcedStayAtLocker()) {
                    worker.setOutfit(EntityLockerWorker.OUTFIT_AFTERWORK);
                    enter(Stage.STAND_FORCED_AFTERWORK);
                } else {
                    doMorningClothesToWork();
                    enter(Stage.CHANGE_WORK);
                }
            }
            return;
        }
        PathToward.tryMoveToward(worker, pathToward, lx, ly, lz, Config.getPathSpeed());
        worker.getLookHelper()
            .setLookPosition(lx, ly + 1.0, lz, 30.0F, 30.0F);
    }

    private boolean isNearLockerColumn() {
        int bx = MathHelper.floor_double(worker.posX);
        int bz = MathHelper.floor_double(worker.posZ);
        int cheb = Math.max(Math.abs(bx - worker.getHomeX()), Math.abs(bz - worker.getHomeZ()));
        return cheb <= 1;
    }

    private void doEveningClothesChange() {
        worker.setOutfit(EntityLockerWorker.OUTFIT_AFTERWORK);
        TileEntityLocker te = worker.getHomeLockerTE();
        if (te != null) {
            te.playEveningClothesChangeSounds();
        }
    }

    private void doMorningClothesToWork() {
        worker.setOutfit(EntityLockerWorker.OUTFIT_WORK);
        TileEntityLocker te = worker.getHomeLockerTE();
        if (te != null) {
            te.playMorningClothesChangeSounds();
        }
    }

    private void tickGoToBed() {
        TileEntityLocker te = worker.getHomeLockerTE();
        if (te == null || !te.hasLinkedBed()) {
            enter(Stage.STAND_LOCKER_NIGHT);
            return;
        }
        if (te.getBedDim() != worker.worldObj.provider.dimensionId) {
            enter(Stage.STAND_LOCKER_NIGHT);
            return;
        }
        double bx = te.getBedX() + 0.5;
        double by = te.getBedY();
        double bz = te.getBedZ() + 0.5;
        // If player occupying, wait nearby
        int meta = worker.worldObj.getBlockMetadata(te.getBedX(), te.getBedY(), te.getBedZ());
        int[] head = BlockWorkerBed.headCoords(te.getBedX(), te.getBedY(), te.getBedZ(), meta);
        if (BlockWorkerBed.isPlayerOccupying(worker.worldObj, head[0], head[1], head[2])) {
            enter(Stage.WAIT_BED_FREE);
            return;
        }
        double dx = worker.posX - bx;
        double dz = worker.posZ - bz;
        if (dx * dx + dz * dz <= ARRIVE_SQ) {
            worker.getNavigator()
                .clearPathEntity();
            worker.playGetIntoBedSounds();
            worker.setOutfit(EntityLockerWorker.OUTFIT_PIJAMA);
            enter(Stage.CHANGE_PIJAMA);
            return;
        }
        PathToward.tryMoveToward(worker, pathToward, bx, by, bz, Config.getPathSpeed());
    }

    private void tickWaitBedFree() {
        TileEntityLocker te = worker.getHomeLockerTE();
        if (te == null || !te.hasLinkedBed()) {
            enter(Stage.STAND_LOCKER_NIGHT);
            return;
        }
        int meta = worker.worldObj.getBlockMetadata(te.getBedX(), te.getBedY(), te.getBedZ());
        int[] head = BlockWorkerBed.headCoords(te.getBedX(), te.getBedY(), te.getBedZ(), meta);
        // Stand near bed feet and wait
        double bx = te.getBedX() + 0.5;
        double by = te.getBedY();
        double bz = te.getBedZ() + 0.5;
        double dx = worker.posX - bx;
        double dz = worker.posZ - bz;
        if (dx * dx + dz * dz > 4.0) {
            PathToward.tryMoveToward(worker, pathToward, bx, by, bz, Config.getPathSpeed());
        } else {
            worker.getNavigator()
                .clearPathEntity();
        }
        if (!BlockWorkerBed.isPlayerOccupying(worker.worldObj, head[0], head[1], head[2])) {
            enter(Stage.GOTO_BED);
        }
    }

    private void tickSleeping() {
        if (!worker.isLyingInBed()) {
            worker.lieInLinkedBed();
        }
        // Stay until morning edge handled at top of updateTask
    }

    private void tickStandAtLocker() {
        double lx = worker.getHomeX() + 0.5;
        double ly = worker.getHomeY();
        double lz = worker.getHomeZ() + 0.5;
        double dx = worker.posX - lx;
        double dz = worker.posZ - lz;
        if (dx * dx + dz * dz > ARRIVE_SQ) {
            PathToward.tryMoveToward(worker, pathToward, lx, ly, lz, Config.getPathSpeed());
        } else {
            worker.getNavigator()
                .clearPathEntity();
            worker.getLookHelper()
                .setLookPosition(
                    worker.getHomeX() + 0.5,
                    worker.getHomeY() + 1.0,
                    worker.getHomeZ() + 0.5,
                    30.0F,
                    30.0F);
        }
        if (worker.getOutfit() != EntityLockerWorker.OUTFIT_AFTERWORK
            && worker.getOutfit() != EntityLockerWorker.OUTFIT_PIJAMA) {
            worker.setOutfit(EntityLockerWorker.OUTFIT_AFTERWORK);
        }
    }

    /** Ambient afterwork_roaming while walking to bed / morning locker. */
    public boolean isAfterworkRoaming() {
        return stage == Stage.GOTO_BED || stage == Stage.GOTO_LOCKER_MORNING || stage == Stage.WAIT_BED_FREE;
    }
}
