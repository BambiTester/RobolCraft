package com.angelika.lockerworker.entity.ai;

import java.util.Random;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.Vec3;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.util.GregTechMachineLookup;
import com.angelika.lockerworker.util.PathToward;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * WORK-phase factory-employee AI ({@link WorkerSchedule.Phase#WORK}:
 * {@code t in [0, 5999] OR [8001, 11999]}):
 * <ol>
 * <li>Seek a nearby whitelisted GT processing machine</li>
 * <li>Orbit / patrol around it (pathfind to circle points ~2–5 blocks out)</li>
 * <li>Sometimes look from a short distance; sometimes approach close (~1–1.5 blocks),
 * inspect/pause, then resume orbit or switch</li>
 * <li>Periodically switch to a <b>different</b> nearby machine</li>
 * </ol>
 *
 * <p>
 * Respects {@link Config#maxDistanceFromLocker} (day leash; {@code 0} = unlimited) and
 * {@link Config#getPathSpeed()}. All long-range moves use {@link PathToward}.
 * LOCKER / BREAK / forced-stay: inactive — return or break AI owns mutex bit 1.
 * On phase exit {@link #resetTask} clears the navigator.
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

    /** Coarse phase for ambient sounds (v3). */
    public enum SoundPhase {
        NONE,
        FREE_ROAMING,
        WORKING
    }

    private static final float LOOK_SPEED = 30.0F;

    private final EntityLockerWorker worker;
    private final PathToward.Tracker pathToward = new PathToward.Tracker();

    private State state = State.SEEK_MACHINE;
    private int stateTicks;
    private int switchCooldownTicks;
    private int scanCooldown;
    private int pathFailStreak;

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
        setMutexBits(1); // move — shared with return-to-locker
    }

    @Override
    public boolean shouldExecute() {
        if (worker.isForcedStayAtLocker()) {
            return false;
        }
        return WorkerSchedule.isWork(worker.worldObj);
    }

    @Override
    public boolean continueExecuting() {
        if (worker.isForcedStayAtLocker()) {
            return false;
        }
        return WorkerSchedule.isWork(worker.worldObj);
    }

    /** Ambient sound phase for {@code WorkerSoundManager}. */
    public SoundPhase getSoundPhase() {
        if (!shouldExecute()) {
            return SoundPhase.NONE;
        }
        switch (state) {
            case APPROACH_CLOSE:
            case INSPECT_PAUSE:
                return SoundPhase.WORKING;
            case ORBIT:
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

        int[] machine = GregTechMachineLookup.findRandomMachine(
            worker.worldObj,
            (int) Math.floor(worker.posX),
            (int) Math.floor(worker.posY),
            (int) Math.floor(worker.posZ),
            Config.machineScanRadius,
            null,
            rand);

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
            boolean stillGoing = PathToward
                .tryMoveToward(worker, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
            if (stillGoing) {
                return;
            }
            hasMoveTarget = false;
            pathFailStreak = 0;
            float roll = rand.nextFloat();
            if (roll < 0.35F) {
                enterApproachClose(rand);
            } else if (roll < 0.50F) {
                enterLookFromDistance(rand);
            } else if (roll < 0.58F && switchCooldownTicks <= 40) {
                trySwitchMachine(rand, false);
            } else if (rand.nextFloat() < 0.4F) {
                stateTicks = 10 + rand.nextInt(31);
                state = State.LOOK_FROM_DISTANCE;
            } else {
                pickOrbitPoint(rand);
            }
            return;
        }

        pickOrbitPoint(rand);
    }

    private void enterOrbit(Random rand) {
        state = State.ORBIT;
        hasMoveTarget = false;
        pickOrbitPoint(rand);
    }

    private void pickOrbitPoint(Random rand) {
        if (!hasMachineTarget) {
            state = State.SEEK_MACHINE;
            return;
        }
        double radius = 2.0 + rand.nextDouble() * 3.0;
        double angle = rand.nextDouble() * Math.PI * 2.0;
        moveX = targetMachineX + 0.5 + Math.cos(angle) * radius;
        moveY = targetMachineY;
        moveZ = targetMachineZ + 0.5 + Math.sin(angle) * radius;
        pathToward.reset();
        hasMoveTarget = true;
        pathFailStreak = 0;
        PathToward.tryMoveToward(worker, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
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
        double dist = 1.0 + rand.nextDouble() * 0.5;
        double angle = rand.nextDouble() * Math.PI * 2.0;
        moveX = targetMachineX + 0.5 + Math.cos(angle) * dist;
        moveY = targetMachineY;
        moveZ = targetMachineZ + 0.5 + Math.sin(angle) * dist;
        pathToward.reset();
        hasMoveTarget = true;
        pathFailStreak = 0;
        PathToward.tryMoveToward(worker, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
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

        boolean stillGoing = PathToward.tryMoveToward(worker, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
        if (stillGoing) {
            if (pathToward.stuckTicks > PathToward.STUCK_TICKS * 3) {
                hasMoveTarget = false;
                pathToward.reset();
                enterOrbit(rand);
            }
            return;
        }
        hasMoveTarget = false;
        double dx = worker.posX - (targetMachineX + 0.5);
        double dz = worker.posZ - (targetMachineZ + 0.5);
        if (dx * dx + dz * dz <= 2.25) {
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
        stateTicks = 40 + rand.nextInt(261);
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
        if (roll < 0.30F) {
            enterApproachClose(rand);
        } else if (roll < 0.40F) {
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
        if (len > PathToward.LONG_RANGE_THRESHOLD) {
            moveX = mx;
            moveY = targetMachineY;
            moveZ = mz;
        } else {
            int hop = 3 + rand.nextInt(8);
            double scale = Math.min(hop, len) / len;
            moveX = worker.posX + dx * scale;
            moveY = worker.posY;
            moveZ = worker.posZ + dz * scale;
        }
        pathToward.reset();
        hasMoveTarget = true;
        PathToward.tryMoveToward(worker, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
    }

    private void enterIdleWander(Random rand) {
        state = State.IDLE_WANDER;
        hasMachineTarget = false;
        hasMoveTarget = false;
        pickIdleHop(rand);
    }

    private void tickIdleWander(Random rand) {
        if (stateTicks > 0 && !hasMoveTarget) {
            stateTicks--;
            if (stateTicks <= 0) {
                state = State.SEEK_MACHINE;
                scanCooldown = 0;
            }
            return;
        }

        if (hasMoveTarget) {
            boolean stillGoing = PathToward
                .tryMoveToward(worker, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
            if (!stillGoing) {
                hasMoveTarget = false;
                stateTicks = 60 + rand.nextInt(341);
                hasMachineTarget = false;
            }
            return;
        }

        if (scanCooldown > 0) {
            scanCooldown--;
        } else {
            scanCooldown = Config.machineScanIntervalTicks;
            int[] machine = GregTechMachineLookup.findNearestMachine(
                worker.worldObj,
                (int) Math.floor(worker.posX),
                (int) Math.floor(worker.posY),
                (int) Math.floor(worker.posZ),
                Config.machineScanRadius);
            if (machine != null && isWithinLockerRange(machine[0], machine[1], machine[2])) {
                setMachineTarget(machine);
                switchCooldownTicks = randomSwitchInterval(rand);
                enterOrbit(rand);
                return;
            }
        }
        pickIdleHop(rand);
    }

    private void pickIdleHop(Random rand) {
        int hop = 3 + rand.nextInt(8);
        Vec3 dir = Vec3.createVectorHelper((rand.nextDouble() - 0.5) * 2, 0, (rand.nextDouble() - 0.5) * 2);
        if (Config.maxDistanceFromLocker > 0 && worker.hasHomeLocker()
            && worker.worldObj.provider.dimensionId == worker.getHomeDim()
            && distanceFromLocker() > Config.maxDistanceFromLocker * 0.75) {
            dir = Vec3.createVectorHelper(
                (worker.getHomeX() + 0.5) - worker.posX,
                0,
                (worker.getHomeZ() + 0.5) - worker.posZ);
        }
        if (dir.lengthVector() < 1.0E-4) {
            dir = Vec3.createVectorHelper(1, 0, 0);
        }
        dir = dir.normalize();
        moveX = worker.posX + dir.xCoord * hop;
        moveY = worker.posY;
        moveZ = worker.posZ + dir.zCoord * hop;
        if (Config.maxDistanceFromLocker > 0 && worker.hasHomeLocker()
            && worker.worldObj.provider.dimensionId == worker.getHomeDim()) {
            double dx = moveX - (worker.getHomeX() + 0.5);
            double dz = moveZ - (worker.getHomeZ() + 0.5);
            if (Math.sqrt(dx * dx + dz * dz) > Config.maxDistanceFromLocker) {
                moveX = worker.getHomeX() + 0.5;
                moveZ = worker.getHomeZ() + 0.5;
            }
        }
        pathToward.reset();
        hasMoveTarget = true;
        PathToward.tryMoveToward(worker, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
    }

    private double distanceFromLocker() {
        if (!worker.hasHomeLocker() || worker.worldObj.provider.dimensionId != worker.getHomeDim()) {
            return 0.0D;
        }
        double dx = worker.posX - (worker.getHomeX() + 0.5);
        double dz = worker.posZ - (worker.getHomeZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Daytime WORK leash only. {@code maxDistanceFromLocker <= 0} = unlimited. */
    private boolean isBeyondMaxDistance() {
        if (Config.maxDistanceFromLocker <= 0) {
            return false;
        }
        if (!worker.hasHomeLocker() || worker.worldObj.provider.dimensionId != worker.getHomeDim()) {
            return false;
        }
        return distanceFromLocker() > Config.maxDistanceFromLocker;
    }

    private boolean isWithinLockerRange(int x, int y, int z) {
        if (Config.maxDistanceFromLocker <= 0) {
            return true;
        }
        if (!worker.hasHomeLocker() || worker.worldObj.provider.dimensionId != worker.getHomeDim()) {
            return true;
        }
        double dx = (x + 0.5) - (worker.getHomeX() + 0.5);
        double dz = (z + 0.5) - (worker.getHomeZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz) <= Config.maxDistanceFromLocker;
    }

    private boolean tickReturnTowardLockerIfNeeded() {
        if (!isBeyondMaxDistance()) {
            return false;
        }
        hasMachineTarget = false;
        hasMoveTarget = false;
        PathToward.tryMoveToward(
            worker,
            pathToward,
            worker.getHomeX() + 0.5,
            worker.getHomeY(),
            worker.getHomeZ() + 0.5,
            Config.getPathSpeed());
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
