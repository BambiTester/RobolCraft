package com.angelika.lockerworker.entity.ai;

import java.util.List;
import java.util.Random;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.util.MoveToward;
import com.angelika.lockerworker.util.StandPoints;
import com.angelika.lockerworker.util.TrashcanRegistry;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * BREAK-phase AI ({@link WorkerSchedule.Phase#BREAK}: t in [6000, 8000]):
 * path near nearest trashcan (registry; {@link Config#trashcanSearchRadius}, 0=unlimited),
 * linger, glance at peer workers. Locker idle only when no can exists in the searchable world.
 * Smoking / ambient break sounds are driven from {@link EntityLockerWorker}.
 * Owns movement during break — day machine AI must not run.
 * Long-range walks use {@link MoveToward} waypoint stepping.
 */
public class EntityAIBreakTime extends EntityAIBase {

    private static final float LOOK_SPEED = 30.0F;

    private final EntityLockerWorker worker;
    private final MoveToward.Tracker pathToward = new MoveToward.Tracker();

    private int repathCooldown;
    private int lookPeerCooldown;
    private int lingerTicks;

    private int trashX;
    private int trashY;
    private int trashZ;
    private boolean hasTrash;
    private double standX;
    private double standY;
    private double standZ;
    private boolean hasStand;

    public EntityAIBreakTime(EntityLockerWorker worker) {
        this.worker = worker;
        setMutexBits(1); // move — exclusive with return / wander
    }

    @Override
    public boolean shouldExecute() {
        if (worker.isChangingClothes()) {
            return false;
        }
        // v27: break only in work outfit (morning clothes must finish first)
        if (worker.getOutfit() != EntityLockerWorker.OUTFIT_WORK) {
            return false;
        }
        if (worker.isForcedStayAtLocker()) {
            return false;
        }
        return WorkerSchedule.isBreak(worker.worldObj);
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        repathCooldown = 0;
        lookPeerCooldown = 0;
        lingerTicks = 0;
        hasTrash = false;
        hasStand = false;
        pathToward.reset();
        findTrashAndStand(worker.getRNG());
    }

    @Override
    public void resetTask() {
        worker.getNavigator()
            .clearPathEntity();
        repathCooldown = 0;
        hasTrash = false;
        hasStand = false;
        pathToward.reset();
    }

    @Override
    public void updateTask() {
        Random rand = worker.getRNG();

        if (lookPeerCooldown > 0) {
            lookPeerCooldown--;
        } else if (rand.nextFloat() < 0.35F) {
            lookAtNearbyPeer(rand);
            lookPeerCooldown = 40 + rand.nextInt(80);
        }

        if (!hasTrash || !hasStand) {
            if (scanCooldownTick()) {
                findTrashAndStand(rand);
            }
            if (!hasTrash) {
                idleNearLocker(rand);
                return;
            }
        }

        double dx = worker.posX - standX;
        double dz = worker.posZ - standZ;
        double distSq = dx * dx + dz * dz;

        if (distSq < 1.5D) {
            worker.getNavigator()
                .clearPathEntity();
            if (hasTrash) {
                worker.getLookHelper()
                    .setLookPosition(trashX + 0.5, trashY + 0.6, trashZ + 0.5, LOOK_SPEED, LOOK_SPEED);
            }
            if (lingerTicks > 0) {
                lingerTicks--;
            } else if (rand.nextFloat() < 0.02F) {
                // Occasional re-pick stand offset around same trashcan
                pickStandNearTrash(rand);
                lingerTicks = 60 + rand.nextInt(120);
            }
            return;
        }

        MoveToward.tryMoveToward(worker, pathToward, standX, standY, standZ, Config.getPathSpeed());
    }

    private boolean scanCooldownTick() {
        if (repathCooldown > 0) {
            repathCooldown--;
            return repathCooldown == 0;
        }
        repathCooldown = 40;
        return true;
    }

    private void findTrashAndStand(Random rand) {
        hasTrash = false;
        hasStand = false;
        World world = worker.worldObj;
        if (world == null) {
            return;
        }

        // Prefer nearest trashcan via dimension registry (O(cans), not O(radius³)).
        // radius 0 = unlimited — any can in this dimension. Idle-at-locker ONLY if none exist.
        TrashcanRegistry reg = TrashcanRegistry.get(world);
        int[] nearest = null;
        if (reg != null) {
            nearest = reg.findNearest(world, worker.posX, worker.posY, worker.posZ, Config.trashcanSearchRadius);
        }
        if (nearest == null) {
            return;
        }
        trashX = nearest[0];
        trashY = nearest[1];
        trashZ = nearest[2];
        hasTrash = true;
        pickStandNearTrash(rand);
        lingerTicks = 40 + rand.nextInt(100);
    }

    private void pickStandNearTrash(Random rand) {
        if (!hasTrash) {
            return;
        }
        double[] s = StandPoints.nearestBeside(worker.worldObj, trashX, trashY, trashZ, worker);
        standX = s[0];
        standY = s[1];
        standZ = s[2];
        hasStand = true;
        repathCooldown = 0;
        pathToward.reset();
    }

    private void idleNearLocker(Random rand) {
        if (!worker.hasHomeLocker() || worker.worldObj.provider.dimensionId != worker.getHomeDim()) {
            return;
        }
        double[] s = StandPoints
            .nearestBeside(worker.worldObj, worker.getHomeX(), worker.getHomeY(), worker.getHomeZ(), worker);
        double hx = s[0];
        double hy = s[1];
        double hz = s[2];
        double dx = worker.posX - hx;
        double dz = worker.posZ - hz;
        boolean sameFloor = Math.abs(worker.posY - hy) <= EntityAIReturnToLocker.SAME_FLOOR_Y_SLACK;
        if (dx * dx + dz * dz < 2.25 && sameFloor) {
            worker.getNavigator()
                .clearPathEntity();
            return;
        }
        if (!hasStand || repathCooldown <= 0 || !sameFloor) {
            standX = hx;
            standY = hy;
            standZ = hz;
            hasStand = true;
            pathToward.reset();
            repathCooldown = 80 + rand.nextInt(40);
        } else {
            repathCooldown--;
        }
        MoveToward.tryMoveToward(worker, pathToward, standX, standY, standZ, Config.getPathSpeed());
    }

    @SuppressWarnings("unchecked")
    private void lookAtNearbyPeer(Random rand) {
        AxisAlignedBB box = worker.boundingBox.expand(8.0D, 3.0D, 8.0D);
        List<EntityLockerWorker> peers = worker.worldObj.getEntitiesWithinAABB(EntityLockerWorker.class, box);
        if (peers == null || peers.isEmpty()) {
            return;
        }
        EntityLockerWorker pick = null;
        int count = 0;
        for (EntityLockerWorker other : peers) {
            if (other == null || other == worker || other.isDead) {
                continue;
            }
            count++;
            if (rand.nextInt(count) == 0) {
                pick = other;
            }
        }
        if (pick != null) {
            worker.getLookHelper()
                .setLookPositionWithEntity(pick, LOOK_SPEED, LOOK_SPEED);
        }
    }
}
