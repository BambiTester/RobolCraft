package com.angelika.lockerworker.entity.ai;

import java.util.Random;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.Vec3;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.util.GregTechMachineLookup;
import com.angelika.lockerworker.util.VanillaDayNight;

/**
 * Daytime factory-employee AI ({@link VanillaDayNight#isDaytime}:
 * {@code t < 12000 || t >= 23000}):
 * <ol>
 * <li>Seek a nearby whitelisted GT processing machine</li>
 * <li>Orbit / patrol around it (pathfind to circle points ~2–5 blocks out)</li>
 * <li>Sometimes look from a short distance; sometimes approach close (~1–1.5 blocks),
 * inspect/pause, then resume orbit or switch</li>
 * <li>Periodically switch to a <b>different</b> nearby machine</li>
 * </ol>
 *
 * <p>
 * Respects {@link Config#maxDistanceFromLocker} (leash) and {@link Config#getPathSpeed()}.
 * Night or forced-stay: inactive — {@link EntityAIReturnToLocker} owns mutex bit 1.
 * Both share mutex bit 1; day gate here + night gate there prevents fighting.
 * On night transition {@link #resetTask} clears the navigator.
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
        // Day roam only — forced stay sends worker home like night
        if (worker.isForcedStayAtLocker()) {
            return false;
        }
        return VanillaDayNight.isDaytime(worker.worldObj);
    }

    @Override
    public boolean continueExecuting() {
        if (worker.isForcedStayAtLocker()) {
            return false;
        }
        return VanillaDayNight.isDaytime(worker.worldObj);
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
    }

    @Override
    public void updateTask() {
        Random rand = worker.getRNG();

        // Past maxDistanceFromLocker — ignore machines and path home
        if (tickReturnTowardLockerIfNeeded()) {
            return;
        }

        if (switchCooldownTicks > 0) {
            switchCooldownTicks--;
        }

        // Timed look / inspect: face machine, do not path
        if (state == State.LOOK_FROM_DISTANCE || state == State.INSPECT_PAUSE) {
            stateTicks--;
            lookAtCurrentMachine();
            if (stateTicks <= 0) {
                afterLookOrInspect(rand);
            }
            return;
        }

        // Switch timer: leave current machine for another (even mid-orbit)
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

    // --- SEEK ---

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
            // No machines in scan radius / within locker leash — hop locally
            enterIdleWander(rand);
            return;
        }

        setMachineTarget(machine);
        switchCooldownTicks = randomSwitchInterval(rand);
        enterOrbit(rand);
    }

    // --- ORBIT / PATROL ---

    private void tickOrbit(Random rand) {
        if (!hasMachineTarget) {
            state = State.SEEK_MACHINE;
            return;
        }

        lookAtCurrentMachine();

        if (hasMoveTarget) {
            if (worker.getNavigator()
                .noPath()) {
                hasMoveTarget = false;
                pathFailStreak = 0;
                // Arrived at orbit point — decide next action
                float roll = rand.nextFloat();
                if (roll < 0.35F) {
                    enterApproachClose(rand);
                } else if (roll < 0.50F) {
                    enterLookFromDistance(rand);
                } else if (roll < 0.58F && switchCooldownTicks <= 40) {
                    trySwitchMachine(rand, false);
                } else {
                    // Brief pause then next orbit point
                    // (handled by picking a new point below next tick via hasMoveTarget=false)
                    if (rand.nextFloat() < 0.4F) {
                        // micro-stand 0.5–2s then continue orbit
                        stateTicks = 10 + rand.nextInt(31);
                        // reuse LOOK state briefly as stand-looking
                        state = State.LOOK_FROM_DISTANCE;
                        return;
                    }
                    pickOrbitPoint(rand);
                }
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
        // Circle radius ~2–5 blocks, random angle
        double radius = 2.0 + rand.nextDouble() * 3.0;
        double angle = rand.nextDouble() * Math.PI * 2.0;
        moveX = targetMachineX + 0.5 + Math.cos(angle) * radius;
        moveY = targetMachineY;
        moveZ = targetMachineZ + 0.5 + Math.sin(angle) * radius;

        if (worker.getNavigator()
            .tryMoveToXYZ(moveX, moveY, moveZ, Config.getPathSpeed())) {
            hasMoveTarget = true;
            pathFailStreak = 0;
        } else {
            pathFailStreak++;
            if (pathFailStreak >= 4) {
                // Stuck orbiting — try switching or idle hop
                pathFailStreak = 0;
                if (!trySwitchMachine(rand, false)) {
                    enterIdleWander(rand);
                }
            }
        }
    }

    // --- LOOK FROM DISTANCE ---

    private void enterLookFromDistance(Random rand) {
        worker.getNavigator()
            .clearPathEntity();
        hasMoveTarget = false;
        state = State.LOOK_FROM_DISTANCE;
        // Prefer short looks; rare long stare so it doesn't dominate
        if (rand.nextFloat() < 0.08F) {
            // Rare: 30s–90s (was up to 3min — shortened)
            stateTicks = 600 + rand.nextInt(1201);
        } else {
            // Common: 2–12s
            stateTicks = 40 + rand.nextInt(201);
        }
    }

    // --- APPROACH CLOSE + INSPECT ---

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
        // Within ~1.0–1.5 blocks of block center
        double dist = 1.0 + rand.nextDouble() * 0.5;
        double angle = rand.nextDouble() * Math.PI * 2.0;
        moveX = targetMachineX + 0.5 + Math.cos(angle) * dist;
        moveY = targetMachineY;
        moveZ = targetMachineZ + 0.5 + Math.sin(angle) * dist;

        if (worker.getNavigator()
            .tryMoveToXYZ(moveX, moveY, moveZ, Config.getPathSpeed())) {
            hasMoveTarget = true;
            pathFailStreak = 0;
        } else {
            pathFailStreak++;
            if (pathFailStreak >= 3) {
                pathFailStreak = 0;
                enterOrbit(rand);
            }
        }
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

        if (worker.getNavigator()
            .noPath()) {
            hasMoveTarget = false;
            // Close enough or arrived — inspect
            double dx = worker.posX - (targetMachineX + 0.5);
            double dz = worker.posZ - (targetMachineZ + 0.5);
            double distSq = dx * dx + dz * dz;
            if (distSq <= 2.25 || pathFailStreak == 0) {
                enterInspectPause(rand);
            } else {
                // Didn't get close — resume orbit
                enterOrbit(rand);
            }
        }
    }

    private void enterInspectPause(Random rand) {
        worker.getNavigator()
            .clearPathEntity();
        hasMoveTarget = false;
        state = State.INSPECT_PAUSE;
        // Inspect / work pause 2–15s
        stateTicks = 40 + rand.nextInt(261);
    }

    private void afterLookOrInspect(Random rand) {
        float roll = rand.nextFloat();
        if (state == State.INSPECT_PAUSE) {
            // After inspect: often switch, else resume orbit
            if (roll < 0.45F) {
                if (trySwitchMachine(rand, false)) {
                    return;
                }
            }
            enterOrbit(rand);
            return;
        }
        // After look-from-distance
        if (roll < 0.30F) {
            enterApproachClose(rand);
        } else if (roll < 0.40F) {
            trySwitchMachine(rand, false);
        } else {
            enterOrbit(rand);
        }
    }

    // --- SWITCH MACHINE ---

    /**
     * @param forceNew if true, only succeed when a different xyz is found
     * @return true if a (new) machine was selected and orbit started
     */
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
            // Only one machine in range — keep attending it, reset timer
            switchCooldownTicks = randomSwitchInterval(rand);
            enterOrbit(rand);
            return false;
        }

        setMachineTarget(next);
        switchCooldownTicks = randomSwitchInterval(rand);
        worker.getNavigator()
            .clearPathEntity();
        hasMoveTarget = false;

        // Often hop toward the new area first (3–10 blocks toward machine), then orbit
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
        double dx = (targetMachineX + 0.5) - worker.posX;
        double dz = (targetMachineZ + 0.5) - worker.posZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) {
            return;
        }
        int hop = 3 + rand.nextInt(8); // 3–10
        double scale = Math.min(hop, len) / len;
        moveX = worker.posX + dx * scale;
        moveY = worker.posY;
        moveZ = worker.posZ + dz * scale;
        if (worker.getNavigator()
            .tryMoveToXYZ(moveX, moveY, moveZ, Config.getPathSpeed())) {
            hasMoveTarget = true;
        }
    }

    // --- IDLE (no machines) ---

    private void enterIdleWander(Random rand) {
        state = State.IDLE_WANDER;
        hasMachineTarget = false;
        hasMoveTarget = false;
        pickIdleHop(rand);
    }

    private void tickIdleWander(Random rand) {
        // Idle stand pause (no machine to look at) — countdown then re-seek
        if (stateTicks > 0 && !hasMoveTarget) {
            stateTicks--;
            if (stateTicks <= 0) {
                state = State.SEEK_MACHINE;
                scanCooldown = 0;
            }
            return;
        }

        if (hasMoveTarget) {
            if (worker.getNavigator()
                .noPath()) {
                hasMoveTarget = false;
                // Stand 3–20s then re-seek
                stateTicks = 60 + rand.nextInt(341);
                hasMachineTarget = false;
            }
            return;
        }

        // Re-scan occasionally while idle
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
        // Near leash edge: bias hop toward locker
        if (worker.hasHomeLocker() && worker.worldObj.provider.dimensionId == worker.getHomeDim()
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
        // Reject hop that would leave the leash
        if (worker.hasHomeLocker() && worker.worldObj.provider.dimensionId == worker.getHomeDim()) {
            double dx = moveX - (worker.getHomeX() + 0.5);
            double dz = moveZ - (worker.getHomeZ() + 0.5);
            if (Math.sqrt(dx * dx + dz * dz) > Config.maxDistanceFromLocker) {
                moveX = worker.getHomeX() + 0.5;
                moveZ = worker.getHomeZ() + 0.5;
            }
        }
        if (worker.getNavigator()
            .tryMoveToXYZ(moveX, moveY, moveZ, Config.getPathSpeed())) {
            hasMoveTarget = true;
        } else {
            scanCooldown = Math.min(scanCooldown, 20);
        }
    }

    // --- LEASH (maxDistanceFromLocker) ---

    /** Horizontal distance from home locker; huge if no home / wrong dim. */
    private double distanceFromLocker() {
        if (!worker.hasHomeLocker() || worker.worldObj.provider.dimensionId != worker.getHomeDim()) {
            return 0.0D; // no leash without a valid home
        }
        double dx = worker.posX - (worker.getHomeX() + 0.5);
        double dz = worker.posZ - (worker.getHomeZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean isBeyondMaxDistance() {
        if (!worker.hasHomeLocker() || worker.worldObj.provider.dimensionId != worker.getHomeDim()) {
            return false;
        }
        return distanceFromLocker() > Config.maxDistanceFromLocker;
    }

    /** True if block xyz is within maxDistanceFromLocker of home (or no home). */
    private boolean isWithinLockerRange(int x, int y, int z) {
        if (!worker.hasHomeLocker() || worker.worldObj.provider.dimensionId != worker.getHomeDim()) {
            return true;
        }
        double dx = (x + 0.5) - (worker.getHomeX() + 0.5);
        double dz = (z + 0.5) - (worker.getHomeZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz) <= Config.maxDistanceFromLocker;
    }

    /** Path back toward locker when past leash; returns true if handling this tick. */
    private boolean tickReturnTowardLockerIfNeeded() {
        if (!isBeyondMaxDistance()) {
            return false;
        }
        // Drop machine focus — get back in range first
        hasMachineTarget = false;
        hasMoveTarget = false;
        double standX = worker.getHomeX() + 0.5;
        double standY = worker.getHomeY();
        double standZ = worker.getHomeZ() + 0.5;
        worker.getNavigator()
            .tryMoveToXYZ(standX, standY, standZ, Config.getPathSpeed());
        state = State.IDLE_WANDER;
        return true;
    }

    // --- helpers ---

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

    /** Switch interval 20–90 seconds. */
    private static int randomSwitchInterval(Random rand) {
        return 400 + rand.nextInt(1401);
    }
}
