package com.angelika.lockerworker.entity.ai;

import java.util.List;

import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityLockerWorker;

/**
 * Aggressive-mode melee: chase nearest hostile ({@link IMob} / {@link EntityMob})
 * and deal {@link Config#aggressiveAttackDamage}. Never targets players.
 * Inactive unless home locker is aggressive and config allows it.
 */
public class EntityAIAttackHostile extends EntityAIBase {

    private static final IEntitySelector HOSTILE_SELECTOR = new IEntitySelector() {

        @Override
        public boolean isEntityApplicable(Entity e) {
            if (e == null || !(e instanceof EntityLivingBase) || e instanceof EntityPlayer) {
                return false;
            }
            if (e instanceof EntityLockerWorker) {
                return false;
            }
            return e instanceof IMob || e instanceof EntityMob;
        }
    };

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
        // Don't fight while forced to stay at locker (or let attack interrupt stay —
        // prefer attack during day roam; skip when forced stay so stay wins)
        if (worker.isForcedStayAtLocker()) {
            return false;
        }
        target = findNearestHostile();
        return target != null && !target.isDead;
    }

    @Override
    public boolean continueExecuting() {
        if (!worker.isAggressiveModeActive() || worker.isForcedStayAtLocker()) {
            return false;
        }
        return target != null && !target.isDead
            && target.isEntityAlive()
            && worker.getDistanceSqToEntity(target)
                < (double) (Config.aggressiveTargetRange + 4.0F) * (Config.aggressiveTargetRange + 4.0F);
    }

    @Override
    public void resetTask() {
        target = null;
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

    @SuppressWarnings("unchecked")
    private EntityLivingBase findNearestHostile() {
        float range = Config.aggressiveTargetRange;
        AxisAlignedBB box = worker.boundingBox.expand(range, range * 0.5, range);
        List<EntityLivingBase> list = worker.worldObj
            .selectEntitiesWithinAABB(EntityLivingBase.class, box, HOSTILE_SELECTOR);
        EntityLivingBase nearest = null;
        double best = Double.MAX_VALUE;
        for (EntityLivingBase living : list) {
            if (living == null || !living.isEntityAlive()) {
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
