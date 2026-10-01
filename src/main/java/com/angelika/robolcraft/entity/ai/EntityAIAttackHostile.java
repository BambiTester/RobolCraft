package com.angelika.robolcraft.entity.ai;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;

import com.angelika.robolcraft.Config;
import com.angelika.robolcraft.entity.EntityRobolCraft;
import com.angelika.robolcraft.util.WorkerSchedule;

/**
 * Aggressive-mode melee: chase nearest hostile ({@link IMob} / {@link EntityMob})
 * — vanilla + GTNH hostiles via those interfaces, not a whitelist — and deal
 * {@link Config#aggressiveAttackDamage}. Players only if {@link Config#attackPlayers}.
 * Never targets other {@link EntityRobolCraft}s. Creepers are valid targets (1.0.3);
 * they still flee and will not explode near workers (see CreeperScareHandler).
 * Inactive unless home locker is aggressive and config allows it. Pack-aggro: adopting a
 * target notifies nearby aggressive workers (see {@link EntityRobolCraft#notifyPackAggro}).
 *
 * <p>
 * During {@link WorkerSchedule.Phase#LOCKER}, this AI yields so return/enter can run
 * (mutex would otherwise permanently block night despawn while a target is held).
 */
public class EntityAIAttackHostile extends EntityAIBase {

    private final EntityRobolCraft worker;
    private EntityLivingBase target;
    private int repathCooldown;
    private int attackCooldown;
    /** Ticks before another AABB retarget when no current target (1.0.6). */
    private int retargetCooldown;

    private static final int RETARGET_INTERVAL = 20;

    public EntityAIAttackHostile(EntityRobolCraft worker) {
        this.worker = worker;
        // Move + look — higher priority than wander/return when active
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (worker.isChangingClothes()) {
            return false;
        }
        if (!worker.isAggressiveModeActive()) {
            return false;
        }
        // Don't fight while forced to stay, lying in bed, or in pajamas
        if (worker.isForcedStayAtLocker() || worker.isLyingInBed()) {
            return false;
        }
        // LOCKER phase: night routine wins over combat
        if (WorkerSchedule.isLocker(worker.worldObj)) {
            clearAttackIfAny();
            return false;
        }
        // Prefer pack-assigned attack target if still valid and visible (1.0.8 LOS)
        EntityLivingBase existing = worker.getAttackTarget();
        if (isValidCombatTarget(existing) && worker.canEntityBeSeen(existing)) {
            target = existing;
            retargetCooldown = 0;
            return true;
        }
        if (existing != null && (!isValidCombatTarget(existing) || !worker.canEntityBeSeen(existing))) {
            worker.setAttackTarget(null);
        }
        if (retargetCooldown > 0) {
            retargetCooldown--;
            return false;
        }
        retargetCooldown = RETARGET_INTERVAL;
        target = findNearestHostile();
        if (target != null && !target.isDead) {
            worker.setAttackTarget(target);
            worker.notifyPackAggro(target);
            worker.onHostileChaseStarted(target);
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
        if (target != null) {
            boolean defeated = target.isDead || !target.isEntityAlive();
            boolean fled = !defeated && (worker.getDistanceSqToEntity(target)
                >= (double) (Config.hostileDetectRadius + 4.0F) * (Config.hostileDetectRadius + 4.0F));
            worker.onHostileChaseEnded(target, defeated, fled);
        }
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
        if (e instanceof EntityRobolCraft) {
            return false;
        }
        if (e instanceof EntityPlayer) {
            return Config.attackPlayers;
        }
        // Creepers are IMob — attack them; CreeperScareHandler still makes them flee + defuse
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
            // Vanilla-cheap LOS — same raytrace monsters use on players
            if (!worker.canEntityBeSeen(living)) {
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
