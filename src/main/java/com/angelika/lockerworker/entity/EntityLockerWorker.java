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
import com.angelika.lockerworker.entity.ai.EntityAIBreakTime;
import com.angelika.lockerworker.entity.ai.EntityAIReturnToLocker;
import com.angelika.lockerworker.entity.ai.EntityAIWanderNearMachines;
import com.angelika.lockerworker.sound.WorkerSoundManager;
import com.angelika.lockerworker.tileentity.TileEntityLocker;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * Silent factory worker bound to a locker. Villager-like size/health.
 *
 * <p>
 * Schedule gated by {@link WorkerSchedule#phase(World)} on
 * {@code world.getWorldTime() % 24000}: LOCKER [12000–23999], WORK
 * [0–5999]/[8001–11999], BREAK [6000–8000]. Does not use
 * {@code World.isDaytime()} / skylight.
 */
public class EntityLockerWorker extends EntityCreature {

    /** Datawatcher: ambient sound mode for client exclusive playback. */
    public static final int DW_SOUND_MODE = 20;
    /** Datawatcher: increments on each interaction sound request. */
    public static final int DW_INTERACT_SEQ = 21;
    /** Datawatcher: increments on each one-shot event sound. */
    public static final int DW_ONESHOT_SEQ = 22;
    /** Datawatcher: kind of last one-shot (see ONESHOT_*). */
    public static final int DW_ONESHOT_KIND = 23;

    public static final byte SOUND_MODE_NONE = 0;
    public static final byte SOUND_MODE_FREE_ROAMING = 1;
    public static final byte SOUND_MODE_WORKING = 2;
    public static final byte SOUND_MODE_BREAKTIME = 3;

    public static final byte ONESHOT_NONE = 0;
    public static final byte ONESHOT_DAY_START = 1;
    public static final byte ONESHOT_DAY_END = 2;
    public static final byte ONESHOT_BREAK_START = 3;
    public static final byte ONESHOT_BREAK_END = 4;
    public static final byte ONESHOT_SMOKING = 5;

    private static final float ONESHOT_CHANCE = 0.25F;

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
    private final EntityAIBreakTime breakAI;
    private final WorkerSoundManager soundManager;

    /** Previous schedule phase for edge-detect (server). */
    private WorkerSchedule.Phase lastPhase;
    private boolean phaseInitialized;

    /** Smoking cadence during BREAK. */
    private int smokeQuietLeft;
    private int smokeBurstLeft;
    private int smokeExhaleCooldown;

    public EntityLockerWorker(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        getNavigator().setAvoidsWater(true);

        wanderAI = new EntityAIWanderNearMachines(this);
        returnAI = new EntityAIReturnToLocker(this);
        breakAI = new EntityAIBreakTime(this);
        soundManager = new WorkerSoundManager(this);

        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIPanic(this, 1.25D));
        tasks.addTask(2, new EntityAIAttackHostile(this));
        tasks.addTask(3, returnAI);
        tasks.addTask(4, breakAI);
        tasks.addTask(5, wanderAI);
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
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
        dataWatcher.addObject(DW_ONESHOT_SEQ, Integer.valueOf(0));
        dataWatcher.addObject(DW_ONESHOT_KIND, Byte.valueOf(ONESHOT_NONE));
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

    public int getOneshotSoundSeq() {
        return dataWatcher.getWatchableObjectInt(DW_ONESHOT_SEQ);
    }

    public byte getOneshotSoundKind() {
        return dataWatcher.getWatchableObjectByte(DW_ONESHOT_KIND);
    }

    /** Server: fire a one-shot exclusive event sound (and smoking particles on client). */
    public void triggerOneshotSound(byte kind) {
        dataWatcher.updateObject(DW_ONESHOT_KIND, Byte.valueOf(kind));
        dataWatcher.updateObject(DW_ONESHOT_SEQ, Integer.valueOf(getOneshotSoundSeq() + 1));
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
        return forcedStayAtLocker || WorkerSchedule.isLocker(worldObj);
    }

    public EntityAIWanderNearMachines.SoundPhase getDaySoundPhase() {
        return wanderAI.getSoundPhase();
    }

    public boolean isBreakPhaseActive() {
        return !forcedStayAtLocker && WorkerSchedule.isBreak(worldObj);
    }

    public void setDeadFromLockerDestroyed() {
        killedByLockerDestroy = true;
        setDead();
    }

    /**
     * Night enter: despawn into home locker without death-respawn.
     * {@link TileEntityLocker#storeWorkerOvernight} plays work_exit at the locker.
     */
    public void enterLockerForNight() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        TileEntityLocker te = getHomeLockerTE();
        if (te != null) {
            te.storeWorkerOvernight(this);
        }
    }

    /** Despawn used by overnight locker enter — skips {@link #onWorkerDied} notify. */
    public void setDeadFromEnteringLocker() {
        killedByLockerDestroy = true;
        setDead();
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!worldObj.isRemote) {
            tickScheduleEdgesAndSmoking();
            soundManager.onUpdate();
        } else {
            LockerWorkerMod.proxy.tickWorkerClientSounds(this);
        }
    }

    /**
     * Edge-detect schedule transitions (25% one-shots) and BREAK smoking cadence.
     */
    private void tickScheduleEdgesAndSmoking() {
        WorkerSchedule.Phase phase = WorkerSchedule.phase(worldObj);
        if (!phaseInitialized) {
            lastPhase = phase;
            phaseInitialized = true;
            resetSmokingCadence();
            return;
        }

        if (phase != lastPhase) {
            onPhaseTransition(lastPhase, phase);
            lastPhase = phase;
            if (phase == WorkerSchedule.Phase.BREAK) {
                resetSmokingCadence();
            } else {
                smokeBurstLeft = 0;
                smokeQuietLeft = 0;
            }
        }

        if (phase == WorkerSchedule.Phase.BREAK && !forcedStayAtLocker) {
            tickSmoking();
        }
    }

    private void onPhaseTransition(WorkerSchedule.Phase from, WorkerSchedule.Phase to) {
        // Day start: leave LOCKER into morning WORK (t wraps into 0)
        if (from == WorkerSchedule.Phase.LOCKER && to == WorkerSchedule.Phase.WORK) {
            maybeOneshot(ONESHOT_DAY_START);
        }
        // Day end: enter LOCKER from WORK (cross 12000)
        if (from == WorkerSchedule.Phase.WORK && to == WorkerSchedule.Phase.LOCKER) {
            maybeOneshot(ONESHOT_DAY_END);
        }
        // Also WORK→LOCKER can happen from afternoon WORK; already covered.
        // BREAK enter
        if (to == WorkerSchedule.Phase.BREAK) {
            maybeOneshot(ONESHOT_BREAK_START);
        }
        // BREAK exit into WORK or LOCKER
        if (from == WorkerSchedule.Phase.BREAK
            && (to == WorkerSchedule.Phase.WORK || to == WorkerSchedule.Phase.LOCKER)) {
            maybeOneshot(ONESHOT_BREAK_END);
        }
    }

    private void maybeOneshot(byte kind) {
        if (getRNG().nextFloat() < ONESHOT_CHANCE) {
            triggerOneshotSound(kind);
        }
    }

    private void resetSmokingCadence() {
        // Quiet stretch 2–12s then a short burst of exhales
        smokeQuietLeft = 40 + getRNG().nextInt(201);
        smokeBurstLeft = 0;
        smokeExhaleCooldown = 0;
    }

    private void tickSmoking() {
        if (smokeExhaleCooldown > 0) {
            smokeExhaleCooldown--;
        }
        if (smokeBurstLeft > 0) {
            if (smokeExhaleCooldown <= 0) {
                triggerOneshotSound(ONESHOT_SMOKING);
                smokeBurstLeft--;
                smokeExhaleCooldown = 12 + getRNG().nextInt(18); // ~0.6–1.5s between exhales
            }
            if (smokeBurstLeft <= 0) {
                smokeQuietLeft = 80 + getRNG().nextInt(281); // 4–18s quiet
            }
            return;
        }
        if (smokeQuietLeft > 0) {
            smokeQuietLeft--;
            return;
        }
        // Start a new exhale burst (1–3 puffs)
        smokeBurstLeft = 1 + getRNG().nextInt(3);
        smokeExhaleCooldown = 0;
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
