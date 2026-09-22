package com.angelika.lockerworker.entity.ai;

import java.util.List;
import java.util.Random;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.block.BlockTrashcan;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * BREAK-phase AI ({@link WorkerSchedule.Phase#BREAK}: t in [6000, 8000]):
 * path near nearest {@link BlockTrashcan}, linger, glance at peer workers.
 * Smoking / ambient break sounds are driven from {@link EntityLockerWorker}.
 * Owns movement during break — day machine AI must not run.
 */
public class EntityAIBreakTime extends EntityAIBase {

    private static final float LOOK_SPEED = 30.0F;

    private final EntityLockerWorker worker;

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
        findTrashAndStand(worker.getRNG());
    }

    @Override
    public void resetTask() {
        worker.getNavigator()
            .clearPathEntity();
        repathCooldown = 0;
        hasTrash = false;
        hasStand = false;
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

        if (repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        repathCooldown = 20;
        worker.getNavigator()
            .tryMoveToXYZ(standX, standY, standZ, Config.getPathSpeed());
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

        int radius = Math.min(Config.maxDistanceFromLocker, 48);
        int originX;
        int originY;
        int originZ;
        if (worker.hasHomeLocker() && world.provider.dimensionId == worker.getHomeDim()) {
            originX = worker.getHomeX();
            originY = worker.getHomeY();
            originZ = worker.getHomeZ();
        } else {
            originX = MathHelper.floor_double(worker.posX);
            originY = MathHelper.floor_double(worker.posY);
            originZ = MathHelper.floor_double(worker.posZ);
        }

        int bestX = 0;
        int bestY = 0;
        int bestZ = 0;
        double bestDist = Double.MAX_VALUE;
        boolean found = false;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = originX + dx;
                    int y = originY + dy;
                    int z = originZ + dz;
                    if (!(world.getBlock(x, y, z) instanceof BlockTrashcan)) {
                        continue;
                    }
                    double ddx = (x + 0.5) - worker.posX;
                    double ddz = (z + 0.5) - worker.posZ;
                    double d = ddx * ddx + ddz * ddz;
                    if (d < bestDist) {
                        bestDist = d;
                        bestX = x;
                        bestY = y;
                        bestZ = z;
                        found = true;
                    }
                }
            }
        }

        if (!found) {
            return;
        }
        trashX = bestX;
        trashY = bestY;
        trashZ = bestZ;
        hasTrash = true;
        pickStandNearTrash(rand);
        lingerTicks = 40 + rand.nextInt(100);
    }

    private void pickStandNearTrash(Random rand) {
        if (!hasTrash) {
            return;
        }
        // Stand 1–5 blocks away, not inside the block
        double dist = 1.0 + rand.nextDouble() * 4.0;
        double angle = rand.nextDouble() * Math.PI * 2.0;
        standX = trashX + 0.5 + Math.cos(angle) * dist;
        standY = trashY;
        standZ = trashZ + 0.5 + Math.sin(angle) * dist;
        hasStand = true;
        repathCooldown = 0;
    }

    private void idleNearLocker(Random rand) {
        if (!worker.hasHomeLocker() || worker.worldObj.provider.dimensionId != worker.getHomeDim()) {
            return;
        }
        double hx = worker.getHomeX() + 0.5;
        double hz = worker.getHomeZ() + 0.5;
        double dx = worker.posX - hx;
        double dz = worker.posZ - hz;
        if (dx * dx + dz * dz < 9.0) {
            worker.getNavigator()
                .clearPathEntity();
            return;
        }
        if (repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        repathCooldown = 25;
        double angle = rand.nextDouble() * Math.PI * 2.0;
        double r = 1.5 + rand.nextDouble() * 2.5;
        worker.getNavigator()
            .tryMoveToXYZ(hx + Math.cos(angle) * r, worker.getHomeY(), hz + Math.sin(angle) * r, Config.getPathSpeed());
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
