package com.angelika.lockerworker.entity;

import java.util.List;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIPanic;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.LockerWorkerMod;
import com.angelika.lockerworker.entity.ai.EntityAIAttackHostile;
import com.angelika.lockerworker.entity.ai.EntityAIReturnToLocker;
import com.angelika.lockerworker.entity.ai.EntityAIWanderNearMachines;
import com.angelika.lockerworker.sound.WorkerSoundManager;
import com.angelika.lockerworker.tileentity.TileEntityLocker;
import com.angelika.lockerworker.util.VanillaDayNight;

/**
 * Silent factory worker bound to a locker. Villager-like size/health.
 *
 * <p>
 * Day/night gated by {@link VanillaDayNight} on {@code world.getWorldTime() % 24000}
 * (night 12000–22999). Does not use {@code World.isDaytime()} / skylight.
 */
public class EntityLockerWorker extends EntityCreature {

    /** Datawatcher: ambient sound mode for client exclusive playback. */
    public static final int DW_SOUND_MODE = 20;
    /** Datawatcher: increments on each interaction sound request. */
    public static final int DW_INTERACT_SEQ = 21;

    public static final byte SOUND_MODE_NONE = 0;
    public static final byte SOUND_MODE_FREE_ROAMING = 1;
    public static final byte SOUND_MODE_WORKING = 2;

    private int homeX;
    private int homeY;
    private int homeZ;
    private int homeDim;
    private boolean hasHomeLocker;
    private boolean killedByLockerDestroy;

    /** Player shift+right-click toggle: stand at locker day or night until off. */
    private boolean forcedStayAtLocker;

    /** Guard against recursive pack-aggro notifications. */
    private boolean packAggroSuppress;

    private final EntityAIWanderNearMachines wanderAI;
    private final EntityAIReturnToLocker returnAI;
    private final WorkerSoundManager soundManager;

    public EntityLockerWorker(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        getNavigator().setAvoidsWater(true);

        wanderAI = new EntityAIWanderNearMachines(this);
        returnAI = new EntityAIReturnToLocker(this);
        soundManager = new WorkerSoundManager(this);

        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIPanic(this, 1.25D));
        tasks.addTask(2, new EntityAIAttackHostile(this));
        tasks.addTask(3, returnAI);
        tasks.addTask(4, wanderAI);
        tasks.addTask(5, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
    }

    @Override
    public boolean isAIEnabled() {
        return true;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_SOUND_MODE, Byte.valueOf(SOUND_MODE_NONE));
        dataWatcher.addObject(DW_INTERACT_SEQ, Integer.valueOf(0));
    }

    public byte getSyncedSoundMode() {
        return dataWatcher.getWatchableObjectByte(DW_SOUND_MODE);
    }

    public void setSyncedSoundMode(byte mode) {
        if (getSyncedSoundMode() != mode) {
            dataWatcher.updateObject(DW_SOUND_MODE, Byte.valueOf(mode));
        }
    }

    public int getInteractionSoundSeq() {
        return dataWatcher.getWatchableObjectInt(DW_INTERACT_SEQ);
    }

    /** Server: bump interaction seq so clients interrupt and play interaction. */
    public void triggerInteractionSound() {
        dataWatcher.updateObject(DW_INTERACT_SEQ, Integer.valueOf(getInteractionSoundSeq() + 1));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(20.0D);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(Config.walkingSpeed);
        getAttributeMap().registerAttribute(SharedMonsterAttributes.attackDamage);
        getEntityAttribute(SharedMonsterAttributes.attackDamage).setBaseValue(Config.aggressiveAttackDamage);
    }

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

    public boolean isAggressiveModeActive() {
        if (!Config.aggressiveModeAllowed) {
            return false;
        }
        TileEntityLocker te = getHomeLockerTE();
        return te != null && te.isAggressive();
    }

    /**
     * Wolf-like pack aggro: notify nearby aggressive workers to adopt {@code target}.
     */
    @SuppressWarnings("unchecked")
    public void notifyPackAggro(EntityLivingBase target) {
        if (worldObj == null || worldObj.isRemote || packAggroSuppress) {
            return;
        }
        if (target == null || !target.isEntityAlive()) {
            return;
        }
        if (!isAggressiveModeActive() || isForcedStayAtLocker()) {
            return;
        }
        float r = Config.getPackAggroRadius();
        if (r <= 0.0F) {
            r = Config.hostileDetectRadius;
        }
        AxisAlignedBB box = boundingBox.expand(r, r * 0.5, r);
        List<EntityLockerWorker> nearby = worldObj.getEntitiesWithinAABB(EntityLockerWorker.class, box);
        for (EntityLockerWorker other : nearby) {
            if (other == null || other == this || other.isDead) {
                continue;
            }
            if (!other.isAggressiveModeActive() || other.isForcedStayAtLocker()) {
                continue;
            }
            if (other.getAttackTarget() == target) {
                continue;
            }
            other.acceptPackAttackTarget(target);
        }
    }

    public void acceptPackAttackTarget(EntityLivingBase target) {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (!isAggressiveModeActive() || isForcedStayAtLocker()) {
            return;
        }
        if (target == null || !target.isEntityAlive() || target instanceof EntityLockerWorker) {
            return;
        }
        packAggroSuppress = true;
        try {
            setAttackTarget(target);
        } finally {
            packAggroSuppress = false;
        }
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
        return forcedStayAtLocker || VanillaDayNight.isNighttime(worldObj);
    }

    public EntityAIWanderNearMachines.SoundPhase getDaySoundPhase() {
        return wanderAI.getSoundPhase();
    }

    public void setDeadFromLockerDestroyed() {
        killedByLockerDestroy = true;
        setDead();
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!worldObj.isRemote) {
            soundManager.onUpdate();
        } else {
            LockerWorkerMod.proxy.tickWorkerClientSounds(this);
        }
    }

    @Override
    public void setDead() {
        if (worldObj != null && worldObj.isRemote) {
            LockerWorkerMod.proxy.stopWorkerClientSounds(getEntityId());
        }
        super.setDead();
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
            TileEntityLocker te = getHomeLockerTE();
            if (te != null) {
                te.onWorkerStayOrModeMaybeChanged();
            }
            return true;
        }
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
