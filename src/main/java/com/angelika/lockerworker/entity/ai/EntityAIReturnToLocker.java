package com.angelika.lockerworker.entity.ai;

import net.minecraft.entity.ai.EntityAIBase;

import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.util.VanillaDayNight;

/**
 * Night AI ({@link VanillaDayNight#isNighttime}: {@code t >= 12000 && t < 23000}):
 * path to stand in front of the home locker; once there, remain standing/facing
 * the locker until day ({@code t < 12000 || t >= 23000}).
 *
 * <p>
 * Mutually exclusive with {@link EntityAIWanderNearMachines} (day-only). Higher
 * priority (task 2 vs 3) plus opposite day/night gates so they never fight.
 * {@link #resetTask} clears the navigator on day transition.
 */
public class EntityAIReturnToLocker extends EntityAIBase {

    private final EntityLockerWorker worker;
    private int repathCooldown;

    public EntityAIReturnToLocker(EntityLockerWorker worker) {
        this.worker = worker;
        setMutexBits(1); // move — shared with wander-near-machines
    }

    @Override
    public boolean shouldExecute() {
        // Only while explicit-tick nighttime (t >= 12000 && t < 23000)
        if (!VanillaDayNight.isNighttime(worker.worldObj)) {
            return false;
        }
        return worker.hasHomeLocker() && worker.worldObj.provider.dimensionId == worker.getHomeDim();
    }

    @Override
    public boolean continueExecuting() {
        // Stay active all night, including when already at the locker (stand in front)
        return shouldExecute();
    }

    @Override
    public void resetTask() {
        worker.getNavigator()
            .clearPathEntity();
        repathCooldown = 0;
    }

    @Override
    public void updateTask() {
        double standX = worker.getHomeX() + 0.5;
        double standY = worker.getHomeY();
        double standZ = worker.getHomeZ() + 1.5; // default "in front" south; facing refined later

        // Prefer standing just outside the locker based on distance check
        double dx = worker.posX - standX;
        double dz = worker.posZ - (worker.getHomeZ() + 0.5);
        double distSq = dx * dx + dz * dz;

        if (distSq < 2.25) {
            // Already at locker — stand in front and face it until sunrise
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

        if (repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        repathCooldown = 20;
        worker.getNavigator()
            .tryMoveToXYZ(standX, standY, standZ, 0.7D);
    }
}
