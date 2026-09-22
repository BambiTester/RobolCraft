package com.angelika.lockerworker.entity;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIPanic;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.ai.EntityAIAttackHostile;
import com.angelika.lockerworker.entity.ai.EntityAIReturnToLocker;
import com.angelika.lockerworker.entity.ai.EntityAIWanderNearMachines;
import com.angelika.lockerworker.sound.WorkerSoundManager;
import com.angelika.lockerworker.tileentity.TileEntityLocker;

/**
 * Silent factory worker bound to a locker. Villager-like size/health.
 *
 * <p>
 * <b>Day/night (explicit Overworld ticks):</b> gated by
 * {@link com.angelika.lockerworker.util.VanillaDayNight} on
 * {@code world.getWorldTime() % 24000} — night {@code 12000–22999}, day otherwise.
 * Does <b>not</b> use {@code World.isDaytime()} / skylight. No custom day-length config.
 * <ul>
 * <li>Day: {@link EntityAIWanderNearMachines} — SEEK/ORBIT/LOOK/APPROACH/INSPECT near
 * whitelisted GT machines (unless {@link #forcedStayAtLocker})</li>
 * <li>Night or forced stay: {@link EntityAIReturnToLocker} — return and stand in front
 * of locker</li>
 * <li>Aggressive (locker TE): {@link EntityAIAttackHostile} vs hostiles</li>
 * </ul>
 * Tasks share mutex bit 1; opposite day/night / stay gates keep wander/return exclusive.
 */
public class EntityLockerWorker extends EntityCreature {

    private int homeX;
    private int homeY;
    private int homeZ;
    private int homeDim;
    private boolean hasHomeLocker;
    private boolean killedByLockerDestroy;

    /** Player shift+right-click toggle: stand at locker day or night until off. */
    private boolean forcedStayAtLocker;

    private final EntityAIWanderNearMachines wanderAI;
    private final EntityAIReturnToLocker returnAI;
    private final WorkerSoundManager soundManager;

    public EntityLockerWorker(World world) {
        super(world);
        setSize(0.6F, 1.8F); // villager-like
        getNavigator().setAvoidsWater(true);

        wanderAI = new EntityAIWanderNearMachines(this);
        returnAI = new EntityAIReturnToLocker(this);
        soundManager = new WorkerSoundManager(this);

        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIPanic(this, 1.25D)); // flee when hurt
        // Aggressive melee (priority 2) — gated internally; mutex with move tasks
        tasks.addTask(2, new EntityAIAttackHostile(this));
        // Night / forced-stay return (3) before day wander (4)
        tasks.addTask(3, returnAI);
        tasks.addTask(4, wanderAI);
        tasks.addTask(5, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
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
        // Needed for melee damage path; value itself comes from Config each hit
        getAttributeMap().registerAttribute(SharedMonsterAttributes.attackDamage);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(Config.aggressiveAttackDamage);
    }

    /** Re-apply movementSpeed from config (e.g. after GuiConfig reload). */
    public void refreshMovementSpeedFromConfig() {
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(Config.walkingSpeed);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(Config.aggressiveAttackDamage);
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

    public boolean isForcedStayAtLocker() {
        return forcedStayAtLocker;
    }

    public void setForcedStayAtLocker(boolean stay) {
        this.forcedStayAtLocker = stay;
    }

    public void toggleForcedStayAtLocker() {
        forcedStayAtLocker = !forcedStayAtLocker;
    }

    /**
     * True when home locker TE is aggressive and config allows it.
     */
    public boolean isAggressiveModeActive() {
        if (!Config.aggressiveModeAllowed) {
            return false;
        }
        TileEntityLocker te = getHomeLockerTE();
        return te != null && te.isAggressive();
    }

    public TileEntityLocker getHomeLockerTE() {
        if (!hasHomeLocker || worldObj == null) {
            return null;
        }
        if (worldObj.provider.dimensionId != homeDim) {
            return null;
        }
        TileEntity te = worldObj.getTileEntity(homeX, homeY, homeZ);
        return te instanceof TileEntityLocker ? (TileEntityLocker) te : null;
    }

    /**
     * Waiting at locker for redstone: standing close while night return or forced stay.
     */
    public boolean isWaitingAtLocker() {
        if (!hasHomeLocker) {
            return false;
        }
        if (!shouldStandAtLockerGate()) {
            return false;
        }
        return returnAI.isStandingAtLocker();
    }

    private boolean shouldStandAtLockerGate() {
        return forcedStayAtLocker || com.angelika.lockerworker.util.VanillaDayNight.isNighttime(worldObj);
    }

    public EntityAIWanderNearMachines.SoundPhase getDaySoundPhase() {
        return wanderAI.getSoundPhase();
    }

    /** Called when the locker block is broken — do not schedule respawn. */
    public void setDeadFromLockerDestroyed() {
        killedByLockerDestroy = true;
        setDead();
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!worldObj.isRemote) {
            soundManager.onUpdate();
        }
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

    // --- Living/hurt/death ambient still silent; custom sounds via WorkerSoundManager ---
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
        if (worldObj.isRemote) {
            return true;
        }
        if (player.isSneaking()) {
            toggleForcedStayAtLocker();
            String msg = forcedStayAtLocker ? "Worker will stay at locker." : "Worker resumed duties.";
            player.addChatMessage(new ChatComponentText(msg));
            // Notify locker TE so redstone can update
            TileEntityLocker te = getHomeLockerTE();
            if (te != null) {
                te.onWorkerStayOrModeMaybeChanged();
            }
            return true;
        }
        // Normal right-click: interaction sound only (no GUI)
        soundManager.playInteraction();
        return true;
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
        tag.setBoolean("ForcedStayAtLocker", forcedStayAtLocker);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        hasHomeLocker = tag.getBoolean("HasHomeLocker");
        homeX = tag.getInteger("HomeX");
        homeY = tag.getInteger("HomeY");
        homeZ = tag.getInteger("HomeZ");
        homeDim = tag.getInteger("HomeDim");
        forcedStayAtLocker = tag.getBoolean("ForcedStayAtLocker");
    }
}
