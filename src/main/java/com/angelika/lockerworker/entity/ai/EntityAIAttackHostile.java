package com.angelika.lockerworker.entity.ai;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * Aggressive-mode melee: chase nearest hostile ({@link IMob} / {@link EntityMob})
 * — vanilla + GTNH hostiles via those interfaces, not a whitelist — and deal
 * {@link Config#aggressiveAttackDamage}. Players only if {@link Config#attackPlayers}.
 * Never targets other {@link EntityLockerWorker}s or {@link EntityCreeper}s. Inactive unless home locker is
 * aggressive and config allows it. Pack-aggro: adopting a target notifies nearby
 * aggressive workers (see {@link EntityLockerWorker#notifyPackAggro}).
 *
 * <p>
 * During {@link WorkerSchedule.Phase#LOCKER}, this AI yields so return/enter can run
 * (mutex would otherwise permanently block night despawn while a target is held).
 */
public class EntityAIAttackHostile extends EntityAIBase {

    private final EntityLockerWorker worker;
    private EntityLivingBase target;
    private int repathCooldown;
    private int attackCooldown;

    public EntityAIAttackHostile(EntityLockerWorker worker) {
        this.worker = worker;
        // Move + look — higher priority than wander/return when active
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (!worker.isAggressiveModeActive()) {
            return false;
        }
        // Don't fight while forced to stay at locker
        if (worker.isForcedStayAtLocker()) {
            return false;
        }
        // LOCKER phase: return/enter wins over combat (panic still higher priority)
        if (WorkerSchedule.isLocker(worker.worldObj)) {
            clearAttackIfAny();
            return false;
        }
        // Prefer pack-assigned attack target if still valid
        EntityLivingBase existing = worker.getAttackTarget();
        if (isValidCombatTarget(existing)) {
            target = existing;
            return true;
        }
        target = findNearestHostile();
        if (target != null && !target.isDead) {
            worker.setAttackTarget(target);
            worker.notifyPackAggro(target);
            return true;
        }
        return false;
    }

    @Override
    public boolean continueExecuting() {
        if (!worker.isAggressiveModeActive() || worker.isForcedStayAtLocker()) {
            return false;
        }
        if (WorkerSchedule.isLocker(worker.worldObj)) {
            clearAttackIfAny();
            return false;
        }
        // Follow pack reassignment
        EntityLivingBase assigned = worker.getAttackTarget();
        if (assigned != null && assigned != target && isValidCombatTarget(assigned)) {
            target = assigned;
        }
        float range = Config.hostileDetectRadius;
        return target != null && !target.isDead
            && target.isEntityAlive()
            && isValidCombatTarget(target)
            && worker.getDistanceSqToEntity(target) < (double) (range + 4.0F) * (range + 4.0F);
    }

    private void clearAttackIfAny() {
        target = null;
        if (worker.getAttackTarget() != null) {
            worker.setAttackTarget(null);
        }
    }

    @Override
    public void resetTask() {
        target = null;
        if (worker.getAttackTarget() != null) {
            EntityLivingBase cur = worker.getAttackTarget();
            if (cur == null || cur.isDead || !isValidCombatTarget(cur) || WorkerSchedule.isLocker(worker.worldObj)) {
                worker.setAttackTarget(null);
            }
        }
        worker.getNavigator()
            .clearPathEntity();
        repathCooldown = 0;
        attackCooldown = 0;
    }

    @Override
    public void updateTask() {
        if (target == null) {
            return;
        }
        // Near home locker during LOCKER — abort so enter can proceed
        if (WorkerSchedule.isLocker(worker.worldObj)) {
            clearAttackIfAny();
            return;
        }
        worker.getLookHelper()
            .setLookPositionWithEntity(target, 30.0F, 30.0F);

        double distSq = worker.getDistanceSqToEntity(target);
        double reach = Math.max(worker.width * 2.0F * worker.width * 2.0F, 2.25D);

        if (distSq <= reach) {
            if (attackCooldown <= 0) {
                attackCooldown = 20;
                float dmg = Config.aggressiveAttackDamage;
                if (dmg > 0.0F) {
                    target.attackEntityFrom(DamageSource.causeMobDamage(worker), dmg);
                }
            }
        } else if (repathCooldown <= 0) {
            repathCooldown = 10;
            worker.getNavigator()
                .tryMoveToEntityLiving(target, Config.getPathSpeed());
        }

        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (repathCooldown > 0) {
            repathCooldown--;
        }
    }

    /**
     * Valid combat target: living, not a worker; players only if config allows;
     * hostiles via IMob/EntityMob (or player when attackPlayers).
     */
    public static boolean isValidCombatTarget(Entity e) {
        if (e == null || !(e instanceof EntityLivingBase)) {
            return false;
        }
        EntityLivingBase living = (EntityLivingBase) e;
        if (!living.isEntityAlive() || living.isDead) {
            return false;
        }
        if (e instanceof EntityLockerWorker) {
            return false;
        }
        // Workers never attack creepers — they scare them away instead
        if (e instanceof EntityCreeper) {
            return false;
        }
        if (e instanceof EntityPlayer) {
            return Config.attackPlayers;
        }
        return e instanceof IMob || e instanceof EntityMob;
    }

    @SuppressWarnings("unchecked")
    private EntityLivingBase findNearestHostile() {
        float range = Config.hostileDetectRadius;
        AxisAlignedBB box = worker.boundingBox.expand(range, range * 0.5, range);
        List<EntityLivingBase> list = worker.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, box);
        EntityLivingBase nearest = null;
        double best = Double.MAX_VALUE;
        for (EntityLivingBase living : list) {
            if (!isValidCombatTarget(living)) {
                continue;
            }
            double d = worker.getDistanceSqToEntity(living);
            if (d < best) {
                best = d;
                nearest = living;
            }
        }
        return nearest;
    }
}
