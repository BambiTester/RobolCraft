package com.angelika.lockerworker.entity.ai;

import java.util.Random;

import net.minecraft.entity.ai.EntityAIBase;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.util.GregTechMachineLookup;
import com.angelika.lockerworker.util.MoveToward;
import com.angelika.lockerworker.util.StandPoints;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * WORK-phase factory-employee AI ({@link WorkerSchedule.Phase#WORK}):
 * seek GT machine → orbit (2–5 blocks, same floor) → sometimes approach stand cell → inspect.
 * Long-range moves use {@link MoveToward}.
 */
public class EntityAIWanderNearMachines extends EntityAIBase {

    private enum State {
        SEEK_MACHINE,
        ORBIT,
        LOOK_FROM_DISTANCE,
        APPROACH_CLOSE,
        INSPECT_PAUSE,
        IDLE_WANDER
    }

    /** Coarse phase for ambient sounds. */
    public enum SoundPhase {
        NONE,
        FREE_ROAMING,
        WORKING
    }

    private static final float LOOK_SPEED = 30.0F;
    /** Ticks to wait after reaching an orbit point before picking the next (stops spin loops). */
    private static final int ORBIT_ARRIVE_PAUSE_MIN = 15;
    private static final int ORBIT_ARRIVE_PAUSE_SPAN = 25;

    private final EntityLockerWorker worker;
    private final MoveToward.Tracker pathToward = new MoveToward.Tracker();

    private State state = State.SEEK_MACHINE;
    private int stateTicks;
    private int switchCooldownTicks;
    private int scanCooldown;
    private int pathFailStreak;
    private int arrivePauseTicks;

    private int targetMachineX;
    private int targetMachineY;
    private int targetMachineZ;
    private boolean hasMachineTarget;

    private double moveX;
    private double moveY;
    private double moveZ;
    private boolean hasMoveTarget;

    public EntityAIWanderNearMachines(EntityLockerWorker worker) {
        this.worker = worker;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (worker.isChangingClothes()) {
            return false;
        }
        if (worker.getOutfit() != EntityLockerWorker.OUTFIT_WORK) {
            return false;
        }
        if (worker.isForcedStayAtLocker()) {
            return false;
        }
        return WorkerSchedule.isWork(worker.worldObj);
    }

    @Override
    public boolean continueExecuting() {
        if (worker.isChangingClothes() || worker.isForcedStayAtLocker()) {
            return false;
        }
        if (worker.getOutfit() != EntityLockerWorker.OUTFIT_WORK) {
            return false;
        }
        return WorkerSchedule.isWork(worker.worldObj);
    }

    /**
     * Working ambient only while standing at the machine ({@link State#INSPECT_PAUSE}),
     * not while walking toward it.
     */
    public SoundPhase getSoundPhase() {
        if (!shouldExecute()) {
            return SoundPhase.NONE;
        }
        switch (state) {
            case INSPECT_PAUSE:
                return SoundPhase.WORKING;
            case APPROACH_CLOSE:
            case ORBIT:
            case LOOK_FROM_DISTANCE:
            case IDLE_WANDER:
                return SoundPhase.FREE_ROAMING;
            default:
                return SoundPhase.NONE;
        }
    }

    @Override
    public void startExecuting() {
        resetDayState();
        switchCooldownTicks = randomSwitchInterval(worker.getRNG());
    }

    @Override
    public void resetTask() {
        resetDayState();
        worker.getNavigator()
            .clearPathEntity();
    }

    private void resetDayState() {
        state = State.SEEK_MACHINE;
        stateTicks = 0;
        hasMoveTarget = false;
        hasMachineTarget = false;
        pathFailStreak = 0;
        scanCooldown = 0;
        arrivePauseTicks = 0;
        pathToward.reset();
    }

    @Override
    public void updateTask() {
        Random rand = worker.getRNG();

        if (tickReturnTowardLockerIfNeeded()) {
            return;
        }

        if (switchCooldownTicks > 0) {
            switchCooldownTicks--;
        }

        if (arrivePauseTicks > 0) {
            arrivePauseTicks--;
            lookAtCurrentMachine();
            worker.getNavigator()
                .clearPathEntity();
            if (arrivePauseTicks > 0) {
                return;
            }
        }

        if (state == State.LOOK_FROM_DISTANCE || state == State.INSPECT_PAUSE) {
            stateTicks--;
            lookAtCurrentMachine();
            if (stateTicks <= 0) {
                afterLookOrInspect(rand);
            }
            return;
        }

        if (hasMachineTarget && switchCooldownTicks <= 0 && state != State.SEEK_MACHINE) {
            trySwitchMachine(rand, true);
            return;
        }

        switch (state) {
            case SEEK_MACHINE:
                tickSeekMachine(rand);
                break;
            case ORBIT:
                tickOrbit(rand);
                break;
            case APPROACH_CLOSE:
                tickApproachClose(rand);
                break;
            case IDLE_WANDER:
                tickIdleWander(rand);
                break;
            default:
                state = State.SEEK_MACHINE;
                break;
        }
    }

    private void tickSeekMachine(Random rand) {
        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }
        scanCooldown = Config.machineScanIntervalTicks;

        int sx = (int) Math.floor(worker.posX);
        int sy = (int) Math.floor(worker.posY);
        int sz = (int) Math.floor(worker.posZ);
        if (worker.hasHomeLocker() && worker.worldObj.provider.dimensionId == worker.getHomeDim()) {
            double hx = worker.getHomeX() + 0.5;
            double hz = worker.getHomeZ() + 0.5;
            double dx = worker.posX - hx;
            double dz = worker.posZ - hz;
            if (dx * dx + dz * dz <= 9.0D) {
                sx = worker.getHomeX();
                sy = worker.getHomeY();
                sz = worker.getHomeZ();
            }
        }
        int[] machine = GregTechMachineLookup
            .findRandomMachine(worker.worldObj, sx, sy, sz, Config.machineScanRadius, null, rand);

        if (machine == null || !isWithinLockerRange(machine[0], machine[1], machine[2])) {
            enterIdleWander(rand);
            return;
        }

        setMachineTarget(machine);
        switchCooldownTicks = randomSwitchInterval(rand);
        enterOrbit(rand);
    }

    private void tickOrbit(Random rand) {
        if (!hasMachineTarget) {
            state = State.SEEK_MACHINE;
            return;
        }

        lookAtCurrentMachine();

        if (hasMoveTarget) {
            boolean stillGoing = MoveToward
                .tryMoveToward(worker, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
            if (stillGoing) {
                return;
            }
            hasMoveTarget = false;
            pathFailStreak = 0;
            arrivePauseTicks = ORBIT_ARRIVE_PAUSE_MIN + rand.nextInt(ORBIT_ARRIVE_PAUSE_SPAN + 1);
            float roll = rand.nextFloat();
            if (roll < 0.08F) {
                enterApproachClose(rand);
            } else if (roll < 0.18F) {
                enterLookFromDistance(rand);
            } else if (roll < 0.24F && switchCooldownTicks <= 40) {
                trySwitchMachine(rand, false);
            } else if (rand.nextFloat() < 0.15F) {
                stateTicks = 10 + rand.nextInt(31);
                state = State.LOOK_FROM_DISTANCE;
            } else {
                // pause first; next tick after pause picks orbit point
                hasMoveTarget = false;
            }
            return;
        }

        pickOrbitPoint(rand);
    }

    private void enterOrbit(Random rand) {
        state = State.ORBIT;
        hasMoveTarget = false;
        arrivePauseTicks = 0;
        pickOrbitPoint(rand);
    }

    private void pickOrbitPoint(Random rand) {
        if (!hasMachineTarget) {
            state = State.SEEK_MACHINE;
            return;
        }
        double radius = 2.0 + rand.nextDouble() * 3.0;
        double angle = rand.nextDouble() * Math.PI * 2.0;
        double rawX = targetMachineX + 0.5 + Math.cos(angle) * radius;
        double rawZ = targetMachineZ + 0.5 + Math.sin(angle) * radius;
        double[] snapped = StandPoints.snapRoam(worker.worldObj, rawX, targetMachineY, rawZ);
        moveX = snapped[0];
        moveY = snapped[1];
        moveZ = snapped[2];
        pathToward.reset();
        hasMoveTarget = true;
        pathFailStreak = 0;
        MoveToward.tryMoveToward(worker, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
    }

    private void enterLookFromDistance(Random rand) {
        worker.getNavigator()
            .clearPathEntity();
        hasMoveTarget = false;
        state = State.LOOK_FROM_DISTANCE;
        if (rand.nextFloat() < 0.08F) {
            stateTicks = 600 + rand.nextInt(1201);
        } else {
            stateTicks = 40 + rand.nextInt(201);
        }
    }

    private void enterApproachClose(Random rand) {
        state = State.APPROACH_CLOSE;
        hasMoveTarget = false;
        pathFailStreak = 0;
        pickCloseApproachPoint(rand);
    }

    private void pickCloseApproachPoint(Random rand) {
        if (!hasMachineTarget) {
            state = State.SEEK_MACHINE;
            return;
        }
        double[] s = StandPoints.nearestBeside(worker.worldObj, targetMachineX, targetMachineY, targetMachineZ, worker);
        moveX = s[0];
        moveY = s[1];
        moveZ = s[2];
        pathToward.reset();
        hasMoveTarget = true;
        pathFailStreak = 0;
        MoveToward.tryMoveToward(worker, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
    }

    private void tickApproachClose(Random rand) {
        if (!hasMachineTarget) {
            state = State.SEEK_MACHINE;
            return;
        }
        lookAtCurrentMachine();

        if (!hasMoveTarget) {
            pickCloseApproachPoint(rand);
            return;
        }

        boolean stillGoing = MoveToward.tryMoveToward(worker, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
        if (stillGoing) {
            if (pathToward.stuckTicks > MoveToward.TELEPORT_TICKS) {
                hasMoveTarget = false;
                pathToward.reset();
                enterOrbit(rand);
            }
            return;
        }
        hasMoveTarget = false;
        double dx = worker.posX - moveX;
        double dz = worker.posZ - moveZ;
        if (dx * dx + dz * dz <= MoveToward.ARRIVE_RANGE_SQ) {
            enterInspectPause(rand);
        } else {
            enterOrbit(rand);
        }
    }

    private void enterInspectPause(Random rand) {
        worker.getNavigator()
            .clearPathEntity();
        hasMoveTarget = false;
        state = State.INSPECT_PAUSE;
        stateTicks = 300 + rand.nextInt(101);
    }

    private void afterLookOrInspect(Random rand) {
        float roll = rand.nextFloat();
        if (state == State.INSPECT_PAUSE) {
            if (roll < 0.45F && trySwitchMachine(rand, false)) {
                return;
            }
            enterOrbit(rand);
            return;
        }
        if (roll < 0.10F) {
            enterApproachClose(rand);
        } else if (roll < 0.18F) {
            trySwitchMachine(rand, false);
        } else {
            enterOrbit(rand);
        }
    }

    private boolean trySwitchMachine(Random rand, boolean forceNew) {
        int[] exclude = hasMachineTarget ? new int[] { targetMachineX, targetMachineY, targetMachineZ } : null;
        int[] next = GregTechMachineLookup.findRandomMachine(
            worker.worldObj,
            (int) Math.floor(worker.posX),
            (int) Math.floor(worker.posY),
            (int) Math.floor(worker.posZ),
            Config.machineScanRadius,
            exclude,
            rand);

        if (next == null || !isWithinLockerRange(next[0], next[1], next[2])) {
            switchCooldownTicks = randomSwitchInterval(rand);
            if (!hasMachineTarget) {
                enterIdleWander(rand);
            } else {
                enterOrbit(rand);
            }
            return false;
        }

        boolean same = hasMachineTarget && next[0] == targetMachineX
            && next[1] == targetMachineY
            && next[2] == targetMachineZ;
        if (forceNew && same) {
            switchCooldownTicks = randomSwitchInterval(rand);
            enterOrbit(rand);
            return false;
        }

        setMachineTarget(next);
        switchCooldownTicks = randomSwitchInterval(rand);
        worker.getNavigator()
            .clearPathEntity();
        hasMoveTarget = false;

        if (!same && rand.nextFloat() < 0.7F) {
            hopTowardMachine(rand);
        }
        enterOrbit(rand);
        return !same;
    }

    private void hopTowardMachine(Random rand) {
        if (!hasMachineTarget) {
            return;
        }
        double mx = targetMachineX + 0.5;
        double mz = targetMachineZ + 0.5;
        double dx = mx - worker.posX;
        double dz = mz - worker.posZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) {
            return;
        }
        if (len > MoveToward.LONG_RANGE_THRESHOLD) {
            double[] s = StandPoints
                .nearestBeside(worker.worldObj, targetMachineX, targetMachineY, targetMachineZ, worker);
            moveX = s[0];
            moveY = s[1];
            moveZ = s[2];
        } else {
            int hop = 3 + rand.nextInt(8);
            double scale = Math.min(hop, len) / len;
            double[] snapped = StandPoints
                .snapRoam(worker.worldObj, worker.posX + dx * scale, worker.posY, worker.posZ + dz * scale);
            moveX = snapped[0];
            moveY = snapped[1];
            moveZ = snapped[2];
        }
        pathToward.reset();
        hasMoveTarget = true;
        MoveToward.tryMoveToward(worker, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
    }

    private void enterIdleWander(Random rand) {
        state = State.IDLE_WANDER;
        hasMachineTarget = false;
        hasMoveTarget = false;
        if (worker.hasHomeLocker() && worker.worldObj.provider.dimensionId == worker.getHomeDim()) {
            pathTowardLockerStand(rand);
        } else {
            stateTicks = 40 + rand.nextInt(80);
        }
    }

    private void tickIdleWander(Random rand) {
        if (hasMoveTarget) {
            boolean stillGoing = MoveToward
                .tryMoveToward(worker, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
            if (!stillGoing) {
                hasMoveTarget = false;
                hasMachineTarget = false;
                stateTicks = 0;
            }
            return;
        }

        if (worker.hasHomeLocker() && worker.worldObj.provider.dimensionId == worker.getHomeDim()) {
            double hx = worker.getHomeX() + 0.5;
            double hz = worker.getHomeZ() + 0.5;
            double dx = worker.posX - hx;
            double dz = worker.posZ - hz;
            if (dx * dx + dz * dz > 9.0D || Math.abs(worker.posY - worker.getHomeY()) > MoveToward.SAME_FLOOR_Y_SLACK) {
                pathTowardLockerStand(rand);
                return;
            }
            worker.getNavigator()
                .clearPathEntity();
        }

        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }
        scanCooldown = Config.machineScanIntervalTicks;

        int sx;
        int sy;
        int sz;
        if (worker.hasHomeLocker() && worker.worldObj.provider.dimensionId == worker.getHomeDim()) {
            sx = worker.getHomeX();
            sy = worker.getHomeY();
            sz = worker.getHomeZ();
        } else {
            sx = (int) Math.floor(worker.posX);
            sy = (int) Math.floor(worker.posY);
            sz = (int) Math.floor(worker.posZ);
        }
        int[] machine = GregTechMachineLookup.findNearestMachine(worker.worldObj, sx, sy, sz, Config.machineScanRadius);
        if (machine != null && isWithinLockerRange(machine[0], machine[1], machine[2])) {
            setMachineTarget(machine);
            switchCooldownTicks = randomSwitchInterval(rand);
            enterOrbit(rand);
        }
    }

    private void pathTowardLockerStand(Random rand) {
        double[] s = StandPoints
            .nearestBeside(worker.worldObj, worker.getHomeX(), worker.getHomeY(), worker.getHomeZ(), worker);
        moveX = s[0];
        moveY = s[1];
        moveZ = s[2];
        pathToward.reset();
        hasMoveTarget = true;
        MoveToward.tryMoveToward(worker, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
    }

    private double distanceFromLocker() {
        if (!worker.hasHomeLocker() || worker.worldObj.provider.dimensionId != worker.getHomeDim()) {
            return 0.0D;
        }
        double dx = worker.posX - (worker.getHomeX() + 0.5);
        double dz = worker.posZ - (worker.getHomeZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean isBeyondMaxDistance() {
        if (worker.getMaxWorkDistance() <= 0) {
            return false;
        }
        if (!worker.hasHomeLocker() || worker.worldObj.provider.dimensionId != worker.getHomeDim()) {
            return false;
        }
        return distanceFromLocker() > worker.getMaxWorkDistance();
    }

    private boolean isWithinLockerRange(int x, int y, int z) {
        if (worker.getMaxWorkDistance() <= 0) {
            return true;
        }
        if (!worker.hasHomeLocker() || worker.worldObj.provider.dimensionId != worker.getHomeDim()) {
            return true;
        }
        double dx = (x + 0.5) - (worker.getHomeX() + 0.5);
        double dz = (z + 0.5) - (worker.getHomeZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz) <= worker.getMaxWorkDistance();
    }

    private boolean tickReturnTowardLockerIfNeeded() {
        if (!isBeyondMaxDistance()) {
            return false;
        }
        hasMachineTarget = false;
        hasMoveTarget = false;
        double[] s = StandPoints
            .nearestBeside(worker.worldObj, worker.getHomeX(), worker.getHomeY(), worker.getHomeZ(), worker);
        MoveToward.tryMoveToward(worker, pathToward, s[0], s[1], s[2], Config.getPathSpeed());
        state = State.IDLE_WANDER;
        return true;
    }

    private void setMachineTarget(int[] machine) {
        targetMachineX = machine[0];
        targetMachineY = machine[1];
        targetMachineZ = machine[2];
        hasMachineTarget = true;
    }

    private void lookAtCurrentMachine() {
        if (!hasMachineTarget) {
            return;
        }
        worker.getLookHelper()
            .setLookPosition(targetMachineX + 0.5, targetMachineY + 0.5, targetMachineZ + 0.5, LOOK_SPEED, LOOK_SPEED);
    }

    private static int randomSwitchInterval(Random rand) {
        int min = Config.machineSwitchMinTicks;
        int max = Config.machineSwitchMaxTicks;
        if (min < 40) {
            min = 40;
        }
        if (max < min) {
            max = min;
        }
        if (max == min) {
            return min;
        }
        return min + rand.nextInt(max - min + 1);
    }
}
