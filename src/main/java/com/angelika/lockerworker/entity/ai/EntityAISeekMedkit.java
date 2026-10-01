package com.angelika.lockerworker.entity.ai;

import net.minecraft.entity.ai.EntityAIBase;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.util.MedkitRegistry;
import com.angelika.lockerworker.util.MoveToward;

/**
 * When under 50% health, path to the nearest medkit within {@link Config#medkitSearchRadius}
 * and regenerate {@code 1} HP every 100 ticks while within {@link Config#medkitHealRange}.
 */
public class EntityAISeekMedkit extends EntityAIBase {

    private static final int HEAL_INTERVAL_TICKS = 50; // half heart / 2.5s (2× vs 1.0.8)

    private final EntityLockerWorker worker;
    private final MoveToward.Tracker pathToward = new MoveToward.Tracker();

    private int medX;
    private int medY;
    private int medZ;
    private boolean hasMedkit;
    private int repathCooldown;
    private int healCooldown;

    public EntityAISeekMedkit(EntityLockerWorker worker) {
        this.worker = worker;
        setMutexBits(1); // movement
    }

    @Override
    public boolean shouldExecute() {
        if (worker.worldObj == null || worker.worldObj.isRemote) {
            return false;
        }
        if (worker.isLyingInBed() || worker.isForcedStayAtLocker() || worker.isChangingClothes()) {
            return false;
        }
        if (worker.getHealth() >= worker.getMaxHealth() * 0.5F) {
            return false;
        }
        MedkitRegistry reg = MedkitRegistry.get(worker.worldObj);
        if (reg == null) {
            return false;
        }
        int[] pos = reg.findNearest(worker.worldObj, worker.posX, worker.posY, worker.posZ, Config.medkitSearchRadius);
        if (pos == null) {
            return false;
        }
        medX = pos[0];
        medY = pos[1];
        medZ = pos[2];
        hasMedkit = true;
        return true;
    }

    @Override
    public boolean continueExecuting() {
        if (worker.isLyingInBed() || worker.isForcedStayAtLocker() || worker.isChangingClothes()) {
            return false;
        }
        if (worker.getHealth() >= worker.getMaxHealth() - 0.01F) {
            return false;
        }
        if (!hasMedkit) {
            return false;
        }
        // Still injured enough to keep going, or mid-heal below full
        if (worker.getHealth() >= worker.getMaxHealth() * 0.5F) {
            // Keep healing until full once we started and are in range
            return isInHealRange();
        }
        // Re-verify medkit still exists when below 50%
        MedkitRegistry reg = MedkitRegistry.get(worker.worldObj);
        if (reg == null) {
            return false;
        }
        int[] pos = reg.findNearest(worker.worldObj, worker.posX, worker.posY, worker.posZ, Config.medkitSearchRadius);
        if (pos == null) {
            return false;
        }
        medX = pos[0];
        medY = pos[1];
        medZ = pos[2];
        return true;
    }

    @Override
    public void startExecuting() {
        repathCooldown = 0;
        healCooldown = HEAL_INTERVAL_TICKS;
        pathToward.reset();
        worker.setHealingFlag(false);
    }

    @Override
    public void resetTask() {
        hasMedkit = false;
        pathToward.reset();
        worker.getNavigator()
            .clearPathEntity();
        worker.setHealingFlag(false);
    }

    @Override
    public void updateTask() {
        if (!hasMedkit) {
            return;
        }
        if (isInHealRange()) {
            worker.getNavigator()
                .clearPathEntity();
            worker.getLookHelper()
                .setLookPosition(medX + 0.5D, medY + 0.5D, medZ + 0.5D, 30.0F, 30.0F);
            worker.setHealingFlag(true);
            if (healCooldown > 0) {
                healCooldown--;
            } else {
                healCooldown = HEAL_INTERVAL_TICKS;
                if (worker.getHealth() < worker.getMaxHealth()) {
                    worker.heal(1.0F);
                }
            }
            return;
        }
        worker.setHealingFlag(false);
        if (repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        repathCooldown = 10;
        // Stand beside medkit center
        MoveToward.tryMoveToward(worker, pathToward, medX + 0.5D, medY, medZ + 0.5D, Config.getPathSpeed());
    }

    private boolean isInHealRange() {
        double r = Config.medkitHealRange;
        double dx = (medX + 0.5D) - worker.posX;
        double dy = (medY + 0.5D) - (worker.posY + worker.height * 0.5D);
        double dz = (medZ + 0.5D) - worker.posZ;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }
}
