package com.angelika.lockerworker.entity.ai;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.MathHelper;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.block.BlockWorkerBed;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.tileentity.TileEntityLocker;
import com.angelika.lockerworker.util.MoveToward;
import com.angelika.lockerworker.util.StandPoints;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * v13 night / bed / morning routine. Worker stays in the world (no despawn).
 *
 * <p>
 * LOCKER phase: path home → afterwork outfit + sounds → bed (if linked) → pajamas +
 * lie until tick 0. No bed: stand at locker in afterwork.
 * Same-night: if a linked bed appears while standing, resume GOTO_BED immediately.
 * Morning (leave LOCKER): wake → afterwork walk to locker → work outfit (unless
 * forced stay) → resume day schedule.
 *
 * <p>
 * v24: every outfit swap uses {@link EntityLockerWorker#beginClothesChange(byte)}
 * (60-tick hold, skin at start). Bed destroyed while sleeping/GOTO_BED wakes in
 * place, changes to afterwork, then paths to locker.
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
    private final MoveToward.Tracker pathToward = new MoveToward.Tracker();
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
        if (worker.isChangingClothes()) {
            return true;
        }
        if (worker.isWaitingForMorningAfterDeath()) {
            return true;
        }
        if (isMorningClothesStage(stage)) {
            return true;
        }
        if (stage != Stage.IDLE && stage != Stage.STAND_FORCED_AFTERWORK) {
            return true;
        }
        if (WorkerSchedule.isLocker(worker.worldObj)) {
            return true;
        }
        if (worker.isForcedStayAtLocker() && worker.getOutfit() == EntityLockerWorker.OUTFIT_AFTERWORK) {
            return true;
        }
        // v27: WORK/BREAK but wrong outfit — own AI until locker clothes change finishes
        if (!WorkerSchedule.isLocker(worker.worldObj) && worker.getOutfit() != EntityLockerWorker.OUTFIT_WORK
            && !worker.isForcedStayAtLocker()) {
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
        // v27: resume unfinished morning clothes pipeline after combat interrupt
        if (isMorningClothesStage(stage)) {
            morningStarted = true;
            return;
        }
        morningStarted = false;
        if (worker.isWaitingForMorningAfterDeath()) {
            stage = Stage.STAND_LOCKER_NIGHT;
            worker.beginClothesChange(EntityLockerWorker.OUTFIT_AFTERWORK);
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
        } else if (worker.getOutfit() != EntityLockerWorker.OUTFIT_WORK) {
            // Day/break with afterwork/pijama — path to locker and change before work AI
            morningStarted = true;
            if (worker.isLyingInBed() || worker.getOutfit() == EntityLockerWorker.OUTFIT_PIJAMA) {
                beginMorning();
            } else {
                enter(Stage.GOTO_LOCKER_MORNING);
            }
            return;
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
        // v27: never drop unfinished morning clothes stages on combat interrupt
        if (isMorningClothesStage(stage)) {
            return;
        }
        if (!WorkerSchedule.isLocker(worker.worldObj) && !worker.isForcedStayAtLocker() && stage != Stage.CHANGE_WORK) {
            if (worker.isLyingInBed()) {
                worker.wakeFromBed(false);
            }
            stage = Stage.IDLE;
        }
    }

    /** Morning path to locker / work-clothes hold — must survive combat mutex interrupt. */
    private static boolean isMorningClothesStage(Stage s) {
        return s == Stage.WAKE || s == Stage.GOTO_LOCKER_MORNING || s == Stage.CHANGE_WORK;
    }

    @Override
    public void updateTask() {
        stageTicks++;
        if (worker.getAttackTarget() != null) {
            worker.setAttackTarget(null);
        }

        // Hold still while the centralized clothes-change timer runs
        if (worker.isChangingClothes()) {
            worker.getNavigator()
                .clearPathEntity();
            // Still allow bed-loss / morning edge checks below for sleep stages
        }

        boolean lockerPhase = WorkerSchedule.isLocker(worker.worldObj);

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
                if (!worker.isChangingClothes()) {
                    tickGoToLocker(true);
                }
                break;
            case CHANGE_AFTERWORK:
                worker.getNavigator()
                    .clearPathEntity();
                if (!worker.isChangingClothes()) {
                    TileEntityLocker te = worker.getHomeLockerTE();
                    if (te != null && te.hasLinkedBed()) {
                        enter(Stage.GOTO_BED);
                    } else {
                        enter(Stage.STAND_LOCKER_NIGHT);
                    }
                }
                break;
            case GOTO_BED:
                if (!worker.isChangingClothes()) {
                    tickGoToBed();
                }
                break;
            case WAIT_BED_FREE:
                if (!worker.isChangingClothes()) {
                    tickWaitBedFree();
                }
                break;
            case CHANGE_PIJAMA:
                worker.getNavigator()
                    .clearPathEntity();
                if (!worker.isChangingClothes()) {
                    TileEntityLocker te = worker.getHomeLockerTE();
                    if (!linkedBedBlockExists(te)) {
                        handleBedDestroyed();
                    } else {
                        worker.lieInLinkedBed();
                        if (worker.isLyingInBed()) {
                            enter(Stage.SLEEPING);
                        } else {
                            // Lie failed (bed vanished between checks)
                            handleBedDestroyed();
                        }
                    }
                }
                break;
            case SLEEPING:
                tickSleeping();
                break;
            case STAND_LOCKER_NIGHT:
                if (!worker.isChangingClothes()) {
                    tickStandAtLocker();
                    if (lockerPhase && !worker.isWaitingForMorningAfterDeath()) {
                        TileEntityLocker bedTe = worker.getHomeLockerTE();
                        if (bedTe != null && bedTe.hasLinkedBed()) {
                            enter(Stage.GOTO_BED);
                            break;
                        }
                    }
                    if (worker.isWaitingForMorningAfterDeath() && !lockerPhase) {
                        worker.setWaitingForMorningAfterDeath(false);
                        TileEntityLocker te = worker.getHomeLockerTE();
                        if (te != null) {
                            te.clearWaitForMorningAfterDeath();
                        }
                        beginMorning();
                    }
                }
                break;
            case WAKE:
                worker.getNavigator()
                    .clearPathEntity();
                if (!worker.isChangingClothes()) {
                    enter(Stage.GOTO_LOCKER_MORNING);
                }
                break;
            case GOTO_LOCKER_MORNING:
                if (!worker.isChangingClothes()) {
                    tickGoToLocker(false);
                }
                break;
            case CHANGE_WORK:
                worker.getNavigator()
                    .clearPathEntity();
                if (!worker.isChangingClothes()) {
                    enter(Stage.IDLE);
                }
                break;
            case STAND_FORCED_AFTERWORK:
                if (!worker.isChangingClothes()) {
                    tickStandAtLocker();
                    if (!lockerPhase && !worker.isForcedStayAtLocker()) {
                        doMorningClothesToWork();
                        enter(Stage.CHANGE_WORK);
                    }
                }
                break;
            default:
                break;
        }
    }

    private void beginMorning() {
        if (worker.isLyingInBed()) {
            worker.wakeFromBed(true); // stand + beginClothesChange(AFTERWORK)
            enter(Stage.WAKE);
        } else {
            if (worker.getOutfit() != EntityLockerWorker.OUTFIT_AFTERWORK) {
                worker.beginClothesChange(EntityLockerWorker.OUTFIT_AFTERWORK);
            }
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

    /**
     * Bed broken/removed while sleeping, changing into pajamas, or pathing to bed.
     * Wake in place → afterwork clothes delay → stand/path at locker.
     */
    private void handleBedDestroyed() {
        if (worker.isLyingInBed()) {
            worker.wakeFromBed(false, false); // already begins afterwork change
        } else if (worker.getOutfit() == EntityLockerWorker.OUTFIT_PIJAMA) {
            worker.beginClothesChange(EntityLockerWorker.OUTFIT_AFTERWORK);
            worker.triggerOneshotSound(EntityLockerWorker.ONESHOT_CHANGING_CLOTHES);
        }
        enter(Stage.CHANGE_AFTERWORK); // waits clothes timer, then STAND_LOCKER_NIGHT (no bed)
    }

    private void tickGoToLocker(boolean evening) {
        double[] s = StandPoints
            .nearestBeside(worker.worldObj, worker.getHomeX(), worker.getHomeY(), worker.getHomeZ(), worker);
        double lx = s[0];
        double ly = s[1];
        double lz = s[2];
        if (isAtStand(lx, ly, lz) || isNearLockerColumn()) {
            worker.getNavigator()
                .clearPathEntity();
            if (evening) {
                doEveningClothesChange();
                enter(Stage.CHANGE_AFTERWORK);
            } else {
                if (worker.isForcedStayAtLocker()) {
                    if (worker.getOutfit() != EntityLockerWorker.OUTFIT_AFTERWORK) {
                        worker.beginClothesChange(EntityLockerWorker.OUTFIT_AFTERWORK);
                    }
                    enter(Stage.STAND_FORCED_AFTERWORK);
                } else {
                    doMorningClothesToWork();
                    enter(Stage.CHANGE_WORK);
                }
            }
            return;
        }
        MoveToward.tryMoveToward(worker, pathToward, lx, ly, lz, Config.getPathSpeed());
        worker.getLookHelper()
            .setLookPosition(lx, ly + 1.0, lz, 30.0F, 30.0F);
    }

    private boolean isAtStand(double lx, double ly, double lz) {
        double dx = worker.posX - lx;
        double dz = worker.posZ - lz;
        if (dx * dx + dz * dz > ARRIVE_SQ) {
            return false;
        }
        return Math.abs(worker.posY - ly) <= EntityAIReturnToLocker.SAME_FLOOR_Y_SLACK;
    }

    private boolean isNearLockerColumn() {
        int bx = MathHelper.floor_double(worker.posX);
        int bz = MathHelper.floor_double(worker.posZ);
        int cheb = Math.max(Math.abs(bx - worker.getHomeX()), Math.abs(bz - worker.getHomeZ()));
        if (cheb > 1) {
            return false;
        }
        // Same-floor only — ignore upper floor above the locker column.
        return Math.abs(worker.posY - worker.getHomeY()) <= EntityAIReturnToLocker.SAME_FLOOR_Y_SLACK;
    }

    private void doEveningClothesChange() {
        worker.beginClothesChange(EntityLockerWorker.OUTFIT_AFTERWORK);
        TileEntityLocker te = worker.getHomeLockerTE();
        if (te != null) {
            te.playEveningClothesChangeSounds();
        }
    }

    private void doMorningClothesToWork() {
        worker.beginClothesChange(EntityLockerWorker.OUTFIT_WORK);
        TileEntityLocker te = worker.getHomeLockerTE();
        if (te != null) {
            te.playMorningClothesChangeSounds();
        }
    }

    private void tickGoToBed() {
        TileEntityLocker te = worker.getHomeLockerTE();
        if (!linkedBedBlockExists(te)) {
            handleBedDestroyed();
            return;
        }
        double[] s = StandPoints.nearestBeside(worker.worldObj, te.getBedX(), te.getBedY(), te.getBedZ(), worker);
        double bx = s[0];
        double by = s[1];
        double bz = s[2];
        int meta = worker.worldObj.getBlockMetadata(te.getBedX(), te.getBedY(), te.getBedZ());
        int[] head = BlockWorkerBed.headCoords(te.getBedX(), te.getBedY(), te.getBedZ(), meta);
        if (BlockWorkerBed.isPlayerOccupying(worker.worldObj, head[0], head[1], head[2])) {
            enter(Stage.WAIT_BED_FREE);
            return;
        }
        double dx = worker.posX - bx;
        double dz = worker.posZ - bz;
        if (dx * dx + dz * dz <= ARRIVE_SQ && Math.abs(worker.posY - by) <= EntityAIReturnToLocker.SAME_FLOOR_Y_SLACK) {
            worker.getNavigator()
                .clearPathEntity();
            worker.playGetIntoBedSounds();
            worker.beginClothesChange(EntityLockerWorker.OUTFIT_PIJAMA);
            enter(Stage.CHANGE_PIJAMA);
            return;
        }
        MoveToward.tryMoveToward(worker, pathToward, bx, by, bz, Config.getPathSpeed());
    }

    private void tickWaitBedFree() {
        TileEntityLocker te = worker.getHomeLockerTE();
        if (!linkedBedBlockExists(te)) {
            handleBedDestroyed();
            return;
        }
        int meta = worker.worldObj.getBlockMetadata(te.getBedX(), te.getBedY(), te.getBedZ());
        int[] head = BlockWorkerBed.headCoords(te.getBedX(), te.getBedY(), te.getBedZ(), meta);
        double[] s = StandPoints.nearestBeside(worker.worldObj, te.getBedX(), te.getBedY(), te.getBedZ(), worker);
        double bx = s[0];
        double by = s[1];
        double bz = s[2];
        double dx = worker.posX - bx;
        double dz = worker.posZ - bz;
        if (dx * dx + dz * dz > 4.0) {
            MoveToward.tryMoveToward(worker, pathToward, bx, by, bz, Config.getPathSpeed());
        } else {
            worker.getNavigator()
                .clearPathEntity();
        }
        if (!BlockWorkerBed.isPlayerOccupying(worker.worldObj, head[0], head[1], head[2])) {
            enter(Stage.GOTO_BED);
        }
    }

    private void tickSleeping() {
        TileEntityLocker te = worker.getHomeLockerTE();
        if (te == null || !te.hasLinkedBed() || !worker.isSleepBedBlockPresent()) {
            handleBedDestroyed();
            return;
        }
        if (!worker.isLyingInBed()) {
            worker.lieInLinkedBed();
            if (!worker.isLyingInBed()) {
                handleBedDestroyed();
            }
        }
    }

    private void tickStandAtLocker() {
        double[] s = StandPoints
            .nearestBeside(worker.worldObj, worker.getHomeX(), worker.getHomeY(), worker.getHomeZ(), worker);
        double lx = s[0];
        double ly = s[1];
        double lz = s[2];
        if (!isAtStand(lx, ly, lz)) {
            MoveToward.tryMoveToward(worker, pathToward, lx, ly, lz, Config.getPathSpeed());
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
            worker.beginClothesChange(EntityLockerWorker.OUTFIT_AFTERWORK);
        }
    }

    /** Linked bed TE coords still hold a BlockWorkerBed (covers setBlock-to-air edge cases). */
    private boolean linkedBedBlockExists(TileEntityLocker te) {
        if (te == null || !te.hasLinkedBed()) {
            return false;
        }
        if (te.getBedDim() != worker.worldObj.provider.dimensionId) {
            return false;
        }
        return worker.worldObj.getBlock(te.getBedX(), te.getBedY(), te.getBedZ()) instanceof BlockWorkerBed;
    }

    /**
     * Waiting at/near locker at night because the linked bed is not placed in the
     * world (STAND_LOCKER_NIGHT, not death-wait). Used for waiting_for_bed ambient.
     */
    public boolean isWaitingForBed() {
        if (stage != Stage.STAND_LOCKER_NIGHT) {
            return false;
        }
        if (worker.isLyingInBed() || worker.isWaitingForMorningAfterDeath()) {
            return false;
        }
        if (!WorkerSchedule.isLocker(worker.worldObj)) {
            return false;
        }
        TileEntityLocker te = worker.getHomeLockerTE();
        return te == null || !te.hasLinkedBed();
    }

    /** Ambient afterwork_roaming while walking to bed / morning locker. */
    public boolean isAfterworkRoaming() {
        return stage == Stage.GOTO_BED || stage == Stage.GOTO_LOCKER_MORNING || stage == Stage.WAIT_BED_FREE;
    }
}
