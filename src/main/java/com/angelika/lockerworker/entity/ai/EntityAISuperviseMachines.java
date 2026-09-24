package com.angelika.lockerworker.entity.ai;

import java.util.Random;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.Vec3;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityShiftSupervisor;
import com.angelika.lockerworker.util.GregTechMachineLookup;
import com.angelika.lockerworker.util.MachineFaultInspector;
import com.angelika.lockerworker.util.PathToward;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * Supervisor day AI: seek machine → approach → inspect (same dwell as worker) →
 * fault-check → switch sooner via {@link Config#supervisorMachineSwitchInterval}.
 * Active during WORK phase only (break uses break AI; deliver AI interrupts).
 */
public class EntityAISuperviseMachines extends EntityAIBase {

    private enum State {
        SEEK,
        APPROACH,
        INSPECT,
        IDLE
    }

    private static final float LOOK_SPEED = 30.0F;

    private final EntityShiftSupervisor supervisor;
    private final PathToward.Tracker pathToward = new PathToward.Tracker();

    private State state = State.SEEK;
    private int stateTicks;
    private int switchCooldown;
    private int scanCooldown;

    private int mx, my, mz;
    private boolean hasMachine;
    private double moveX, moveY, moveZ;
    private boolean hasMove;

    public EntityAISuperviseMachines(EntityShiftSupervisor supervisor) {
        this.supervisor = supervisor;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (supervisor.isForcedStayAtLocker() || supervisor.isLyingInBed()) {
            return false;
        }
        // Only WORK — break has its own AI; deliver has higher priority
        return WorkerSchedule.isWork(supervisor.worldObj);
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    public EntityAIWanderNearMachines.SoundPhase getSoundPhase() {
        if (!shouldExecute()) {
            return EntityAIWanderNearMachines.SoundPhase.NONE;
        }
        if (state == State.APPROACH || state == State.INSPECT) {
            return EntityAIWanderNearMachines.SoundPhase.WORKING;
        }
        if (state == State.IDLE) {
            return EntityAIWanderNearMachines.SoundPhase.FREE_ROAMING;
        }
        return EntityAIWanderNearMachines.SoundPhase.NONE;
    }

    @Override
    public void startExecuting() {
        resetState();
        switchCooldown = randomSwitch(supervisor.getRNG());
    }

    @Override
    public void resetTask() {
        resetState();
        supervisor.getNavigator()
            .clearPathEntity();
    }

    private void resetState() {
        state = State.SEEK;
        stateTicks = 0;
        hasMachine = false;
        hasMove = false;
        scanCooldown = 0;
        pathToward.reset();
    }

    @Override
    public void updateTask() {
        Random rand = supervisor.getRNG();
        if (tickLeash()) {
            return;
        }
        if (switchCooldown > 0) {
            switchCooldown--;
        }

        switch (state) {
            case SEEK:
                tickSeek(rand);
                break;
            case APPROACH:
                tickApproach(rand);
                break;
            case INSPECT:
                tickInspect(rand);
                break;
            case IDLE:
                tickIdle(rand);
                break;
            default:
                state = State.SEEK;
                break;
        }
    }

    private void tickSeek(Random rand) {
        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }
        scanCooldown = Config.machineScanIntervalTicks;
        int[] machine = GregTechMachineLookup.findRandomSupervisorMachine(
            supervisor.worldObj,
            (int) Math.floor(supervisor.posX),
            (int) Math.floor(supervisor.posY),
            (int) Math.floor(supervisor.posZ),
            Config.machineScanRadius,
            null,
            rand);
        if (machine == null || !withinLeash(machine[0], machine[2])) {
            enterIdle(rand);
            return;
        }
        setMachine(machine);
        switchCooldown = randomSwitch(rand);
        enterApproach(rand);
    }

    private void enterApproach(Random rand) {
        state = State.APPROACH;
        hasMove = false;
        pickClose(rand);
    }

    private void pickClose(Random rand) {
        if (!hasMachine) {
            state = State.SEEK;
            return;
        }
        double dist = 1.0 + rand.nextDouble() * 0.5;
        double angle = rand.nextDouble() * Math.PI * 2.0;
        moveX = mx + 0.5 + Math.cos(angle) * dist;
        moveY = my;
        moveZ = mz + 0.5 + Math.sin(angle) * dist;
        pathToward.reset();
        hasMove = true;
        PathToward.tryMoveToward(supervisor, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
    }

    private void tickApproach(Random rand) {
        if (!hasMachine) {
            state = State.SEEK;
            return;
        }
        lookAtMachine();
        if (!hasMove) {
            pickClose(rand);
            return;
        }
        boolean going = PathToward.tryMoveToward(supervisor, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
        if (going) {
            if (pathToward.stuckTicks > PathToward.STUCK_TICKS * 3) {
                hasMove = false;
                enterIdle(rand);
            }
            return;
        }
        hasMove = false;
        double dx = supervisor.posX - (mx + 0.5);
        double dz = supervisor.posZ - (mz + 0.5);
        if (dx * dx + dz * dz <= 2.25) {
            enterInspect(rand);
        } else {
            pickClose(rand);
        }
    }

    private void enterInspect(Random rand) {
        supervisor.getNavigator()
            .clearPathEntity();
        hasMove = false;
        state = State.INSPECT;
        // SAME dwell as worker inspect pause: 40 + rand(261)
        stateTicks = 40 + rand.nextInt(261);
    }

    private void tickInspect(Random rand) {
        lookAtMachine();
        stateTicks--;
        if (stateTicks > 0) {
            return;
        }
        // Fault check at end of inspect
        if (hasMachine) {
            MachineFaultInspector.FaultResult fault = MachineFaultInspector.inspect(supervisor.worldObj, mx, my, mz);
            if (fault != null && fault.hasFault()) {
                supervisor.getReportMemory()
                    .recordMachineFault(
                        fault.machineName,
                        fault.x,
                        fault.y,
                        fault.z,
                        fault.joinedPhrases(),
                        supervisor.worldObj);
            }
        }
        // After inspect → switch to another machine (shorter interval)
        trySwitch(rand);
    }

    private void trySwitch(Random rand) {
        int[] exclude = hasMachine ? new int[] { mx, my, mz } : null;
        int[] next = GregTechMachineLookup.findRandomSupervisorMachine(
            supervisor.worldObj,
            (int) Math.floor(supervisor.posX),
            (int) Math.floor(supervisor.posY),
            (int) Math.floor(supervisor.posZ),
            Config.machineScanRadius,
            exclude,
            rand);
        switchCooldown = randomSwitch(rand);
        if (next == null || !withinLeash(next[0], next[2])) {
            enterIdle(rand);
            return;
        }
        setMachine(next);
        enterApproach(rand);
    }

    private void enterIdle(Random rand) {
        state = State.IDLE;
        hasMachine = false;
        hasMove = false;
        pickIdleHop(rand);
    }

    private void tickIdle(Random rand) {
        if (hasMove) {
            boolean going = PathToward
                .tryMoveToward(supervisor, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
            if (!going) {
                hasMove = false;
                stateTicks = 40 + rand.nextInt(80);
            }
            return;
        }
        if (stateTicks > 0) {
            stateTicks--;
            return;
        }
        state = State.SEEK;
        scanCooldown = 0;
    }

    private void pickIdleHop(Random rand) {
        int hop = 3 + rand.nextInt(8);
        Vec3 dir = Vec3.createVectorHelper((rand.nextDouble() - 0.5) * 2, 0, (rand.nextDouble() - 0.5) * 2);
        if (dir.lengthVector() < 1.0E-4) {
            dir = Vec3.createVectorHelper(1, 0, 0);
        }
        dir = dir.normalize();
        moveX = supervisor.posX + dir.xCoord * hop;
        moveY = supervisor.posY;
        moveZ = supervisor.posZ + dir.zCoord * hop;
        pathToward.reset();
        hasMove = true;
        PathToward.tryMoveToward(supervisor, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
    }

    private void setMachine(int[] m) {
        mx = m[0];
        my = m[1];
        mz = m[2];
        hasMachine = true;
    }

    private void lookAtMachine() {
        if (!hasMachine) {
            return;
        }
        supervisor.getLookHelper()
            .setLookPosition(mx + 0.5, my + 0.5, mz + 0.5, LOOK_SPEED, LOOK_SPEED);
    }

    private boolean tickLeash() {
        if (Config.maxDistanceFromLocker <= 0 || !supervisor.hasHomeLocker()) {
            return false;
        }
        if (supervisor.worldObj.provider.dimensionId != supervisor.getHomeDim()) {
            return false;
        }
        double dx = supervisor.posX - (supervisor.getHomeX() + 0.5);
        double dz = supervisor.posZ - (supervisor.getHomeZ() + 0.5);
        if (Math.sqrt(dx * dx + dz * dz) <= Config.maxDistanceFromLocker) {
            return false;
        }
        hasMachine = false;
        hasMove = false;
        PathToward.tryMoveToward(
            supervisor,
            pathToward,
            supervisor.getHomeX() + 0.5,
            supervisor.getHomeY(),
            supervisor.getHomeZ() + 0.5,
            Config.getPathSpeed());
        state = State.IDLE;
        return true;
    }

    private boolean withinLeash(int x, int z) {
        if (Config.maxDistanceFromLocker <= 0 || !supervisor.hasHomeLocker()) {
            return true;
        }
        if (supervisor.worldObj.provider.dimensionId != supervisor.getHomeDim()) {
            return true;
        }
        double dx = (x + 0.5) - (supervisor.getHomeX() + 0.5);
        double dz = (z + 0.5) - (supervisor.getHomeZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz) <= Config.maxDistanceFromLocker;
    }

    private static int randomSwitch(Random rand) {
        int interval = Config.supervisorMachineSwitchInterval;
        if (interval < 40) {
            interval = 40;
        }
        // Slight jitter ±20%
        int jitter = Math.max(1, interval / 5);
        return Math.max(40, interval - jitter + rand.nextInt(jitter * 2 + 1));
    }
}
