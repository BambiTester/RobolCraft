package com.angelika.lockerworker.entity.ai;

import java.util.Random;

import net.minecraft.entity.ai.EntityAIBase;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityShiftSupervisor;
import com.angelika.lockerworker.util.GregTechMachineLookup;
import com.angelika.lockerworker.util.MachineFaultInspector;
import com.angelika.lockerworker.util.MoveToward;
import com.angelika.lockerworker.util.StandPoints;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * Supervisor day AI: seek → approach stand cell → inspect → switch.
 * Working sound only while inspecting (standing at machine).
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
    private final MoveToward.Tracker pathToward = new MoveToward.Tracker();

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
        if (supervisor.isChangingClothes()) {
            return false;
        }
        if (supervisor.getOutfit() != EntityShiftSupervisor.OUTFIT_WORK) {
            return false;
        }
        if (supervisor.isForcedStayAtLocker() || supervisor.isLyingInBed()) {
            return false;
        }
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
        if (state == State.INSPECT) {
            return EntityAIWanderNearMachines.SoundPhase.WORKING;
        }
        if (state == State.APPROACH || state == State.IDLE) {
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
        int sx = (int) Math.floor(supervisor.posX);
        int sy = (int) Math.floor(supervisor.posY);
        int sz = (int) Math.floor(supervisor.posZ);
        if (supervisor.hasHomeLocker() && supervisor.worldObj.provider.dimensionId == supervisor.getHomeDim()) {
            double hx = supervisor.getHomeX() + 0.5;
            double hz = supervisor.getHomeZ() + 0.5;
            double dx = supervisor.posX - hx;
            double dz = supervisor.posZ - hz;
            if (dx * dx + dz * dz <= 9.0D) {
                sx = supervisor.getHomeX();
                sy = supervisor.getHomeY();
                sz = supervisor.getHomeZ();
            }
        }
        int[] machine = GregTechMachineLookup
            .findRandomSupervisorMachine(supervisor.worldObj, sx, sy, sz, Config.machineScanRadius, null, rand);
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
        double[] s = StandPoints.nearestBeside(supervisor.worldObj, mx, my, mz, supervisor);
        moveX = s[0];
        moveY = s[1];
        moveZ = s[2];
        pathToward.reset();
        hasMove = true;
        MoveToward.tryMoveToward(supervisor, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
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
        boolean going = MoveToward.tryMoveToward(supervisor, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
        if (going) {
            if (pathToward.stuckTicks > MoveToward.TELEPORT_TICKS) {
                hasMove = false;
                enterIdle(rand);
            }
            return;
        }
        hasMove = false;
        double dx = supervisor.posX - moveX;
        double dz = supervisor.posZ - moveZ;
        if (dx * dx + dz * dz <= MoveToward.ARRIVE_RANGE_SQ) {
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
        stateTicks = 300 + rand.nextInt(101);
    }

    private void tickInspect(Random rand) {
        lookAtMachine();
        stateTicks--;
        if (stateTicks > 0) {
            return;
        }
        if (hasMachine) {
            MachineFaultInspector.FaultResult fault = MachineFaultInspector.inspect(supervisor.worldObj, mx, my, mz);
            if (fault != null && fault.hasFault()) {
                boolean recorded = supervisor.getReportMemory()
                    .recordMachineFault(
                        fault.machineName,
                        fault.x,
                        fault.y,
                        fault.z,
                        fault.joinedPhrases(),
                        supervisor.worldObj);
                if (recorded) {
                    supervisor.onReportMemoryChanged();
                }
            }
        }
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
        if (supervisor.hasHomeLocker() && supervisor.worldObj.provider.dimensionId == supervisor.getHomeDim()) {
            pathTowardLockerStand(rand);
        } else {
            stateTicks = 40 + rand.nextInt(80);
        }
    }

    private void tickIdle(Random rand) {
        if (hasMove) {
            boolean going = MoveToward
                .tryMoveToward(supervisor, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
            if (!going) {
                hasMove = false;
                stateTicks = 0;
            }
            return;
        }
        if (supervisor.hasHomeLocker() && supervisor.worldObj.provider.dimensionId == supervisor.getHomeDim()) {
            double hx = supervisor.getHomeX() + 0.5;
            double hz = supervisor.getHomeZ() + 0.5;
            double dx = supervisor.posX - hx;
            double dz = supervisor.posZ - hz;
            if (dx * dx + dz * dz > 9.0D
                || Math.abs(supervisor.posY - supervisor.getHomeY()) > MoveToward.SAME_FLOOR_Y_SLACK) {
                pathTowardLockerStand(rand);
                return;
            }
            supervisor.getNavigator()
                .clearPathEntity();
        }
        state = State.SEEK;
        scanCooldown = 0;
    }

    private void pathTowardLockerStand(Random rand) {
        double[] s = StandPoints.nearestBeside(
            supervisor.worldObj,
            supervisor.getHomeX(),
            supervisor.getHomeY(),
            supervisor.getHomeZ(),
            supervisor);
        moveX = s[0];
        moveY = s[1];
        moveZ = s[2];
        pathToward.reset();
        hasMove = true;
        MoveToward.tryMoveToward(supervisor, pathToward, moveX, moveY, moveZ, Config.getPathSpeed());
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
        if (supervisor.getMaxWorkDistance() <= 0 || !supervisor.hasHomeLocker()) {
            return false;
        }
        if (supervisor.worldObj.provider.dimensionId != supervisor.getHomeDim()) {
            return false;
        }
        double dx = supervisor.posX - (supervisor.getHomeX() + 0.5);
        double dz = supervisor.posZ - (supervisor.getHomeZ() + 0.5);
        if (Math.sqrt(dx * dx + dz * dz) <= supervisor.getMaxWorkDistance()) {
            return false;
        }
        hasMachine = false;
        hasMove = false;
        double[] s = StandPoints.nearestBeside(
            supervisor.worldObj,
            supervisor.getHomeX(),
            supervisor.getHomeY(),
            supervisor.getHomeZ(),
            supervisor);
        MoveToward.tryMoveToward(supervisor, pathToward, s[0], s[1], s[2], Config.getPathSpeed());
        state = State.IDLE;
        return true;
    }

    private boolean withinLeash(int x, int z) {
        if (supervisor.getMaxWorkDistance() <= 0 || !supervisor.hasHomeLocker()) {
            return true;
        }
        if (supervisor.worldObj.provider.dimensionId != supervisor.getHomeDim()) {
            return true;
        }
        double dx = (x + 0.5) - (supervisor.getHomeX() + 0.5);
        double dz = (z + 0.5) - (supervisor.getHomeZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz) <= supervisor.getMaxWorkDistance();
    }

    private static int randomSwitch(Random rand) {
        int interval = Config.supervisorMachineSwitchInterval;
        if (interval < 40) {
            interval = 40;
        }
        int jitter = Math.max(1, interval / 5);
        return Math.max(40, interval - jitter + rand.nextInt(jitter * 2 + 1));
    }
}
