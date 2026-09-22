package com.angelika.lockerworker.entity;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIPanic;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.ai.EntityAIReturnToLocker;
import com.angelika.lockerworker.entity.ai.EntityAIWanderNearMachines;
import com.angelika.lockerworker.tileentity.TileEntityLocker;

/**
 * Silent factory worker bound to a locker. Villager-like size/health; not player-interactable.
 *
 * <p>
 * <b>Day/night (explicit Overworld ticks):</b> gated by
 * {@link com.angelika.lockerworker.util.VanillaDayNight} on
 * {@code world.getWorldTime() % 24000} — night {@code 12000–22999}, day otherwise.
 * Does <b>not</b> use {@code World.isDaytime()} / skylight. No custom day-length config.
 * <ul>
 * <li>Day: {@link EntityAIWanderNearMachines} — SEEK/ORBIT/LOOK/APPROACH/INSPECT near
 * whitelisted GT machines</li>
 * <li>Night: {@link EntityAIReturnToLocker} — return and stand in front of locker
 * until day</li>
 * </ul>
 * Tasks share mutex bit 1; opposite day/night gates keep them exclusive.
 * TODO: Texture Artist — assets/lockerworker/textures/entity/locker_worker.png
 * TODO: sounds — currently silent; add custom sounds later if desired.
 */
public class EntityLockerWorker extends EntityCreature {

    private int homeX;
    private int homeY;
    private int homeZ;
    private int homeDim;
    private boolean hasHomeLocker;
    private boolean killedByLockerDestroy;

    public EntityLockerWorker(World world) {
        super(world);
        setSize(0.6F, 1.8F); // villager-like
        getNavigator().setAvoidsWater(true);

        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIPanic(this, 1.25D)); // flee when hurt
        // Night return (priority 2) before day wander (3); gates are mutually exclusive
        tasks.addTask(2, new EntityAIReturnToLocker(this));
        tasks.addTask(3, new EntityAIWanderNearMachines(this));
        tasks.addTask(4, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
        // No trading / no EntityAITradePlayer / no interact
    }

    @Override
    public boolean isAIEnabled() {
        return true;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        // Vanilla villager health = 20
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(20.0D);
        // Config.walkingSpeed = SharedMonsterAttributes.movementSpeed base (default 0.3)
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(Config.walkingSpeed);
    }

    /** Re-apply movementSpeed from config (e.g. after GuiConfig reload). */
    public void refreshMovementSpeedFromConfig() {
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(Config.walkingSpeed);
    }

    public void setHomeLocker(int x, int y, int z, int dim) {
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
        this.homeDim = dim;
        this.hasHomeLocker = true;
    }

    public boolean hasHomeLocker() {
        return hasHomeLocker;
    }

    public int getHomeX() {
        return homeX;
    }

    public int getHomeY() {
        return homeY;
    }

    public int getHomeZ() {
        return homeZ;
    }

    public int getHomeDim() {
        return homeDim;
    }

    /** Called when the locker block is broken — do not schedule respawn. */
    public void setDeadFromLockerDestroyed() {
        killedByLockerDestroy = true;
        setDead();
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!worldObj.isRemote && !killedByLockerDestroy && hasHomeLocker) {
            notifyLockerOfDeath();
        }
    }

    private void notifyLockerOfDeath() {
        if (worldObj.provider.dimensionId != homeDim) {
            return;
        }
        TileEntity te = worldObj.getTileEntity(homeX, homeY, homeZ);
        if (te instanceof TileEntityLocker) {
            ((TileEntityLocker) te).onWorkerDied();
        }
    }

    // --- Silent for now ---
    @Override
    protected String getLivingSound() {
        return null;
    }

    @Override
    protected String getHurtSound() {
        return null;
    }

    @Override
    protected String getDeathSound() {
        return null;
    }

    @Override
    protected float getSoundVolume() {
        return 0.0F;
    }

    @Override
    public boolean interact(EntityPlayer player) {
        // Not player-interactable (no trading)
        return false;
    }

    @Override
    public boolean canBePushed() {
        return true;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setBoolean("HasHomeLocker", hasHomeLocker);
        tag.setInteger("HomeX", homeX);
        tag.setInteger("HomeY", homeY);
        tag.setInteger("HomeZ", homeZ);
        tag.setInteger("HomeDim", homeDim);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        hasHomeLocker = tag.getBoolean("HasHomeLocker");
        homeX = tag.getInteger("HomeX");
        homeY = tag.getInteger("HomeY");
        homeZ = tag.getInteger("HomeZ");
        homeDim = tag.getInteger("HomeDim");
    }
}
