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
import com.angelika.lockerworker.block.BlockWorkerBed;
import com.angelika.lockerworker.entity.ai.EntityAIAttackHostile;
import com.angelika.lockerworker.entity.ai.EntityAIBreakTime;
import com.angelika.lockerworker.entity.ai.EntityAINightRoutine;
import com.angelika.lockerworker.entity.ai.EntityAIReturnToLocker;
import com.angelika.lockerworker.entity.ai.EntityAIWanderNearMachines;
import com.angelika.lockerworker.sound.ModSounds;
import com.angelika.lockerworker.sound.WorkerSoundManager;
import com.angelika.lockerworker.tileentity.TileEntityLocker;
import com.angelika.lockerworker.util.LockerLink;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * Silent factory worker bound to a locker. Villager-like size/health.
 *
 * <p>
 * Schedule gated by {@link WorkerSchedule#phase(World)} on
 * {@code world.getWorldTime() % 24000}: LOCKER [12000–23999], WORK
 * [0–5999]/[8001–11999], BREAK [6000–8000]. Does not use
 * {@code World.isDaytime()} / skylight.
 *
 * <p>
 * v13: stays in the world overnight (outfit + bed). No despawn into locker.
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
    /** Datawatcher: outfit (WORK / AFTERWORK / PIJAMA). */
    public static final int DW_OUTFIT = 24;
    /** Datawatcher: lying in bed flag. */
    public static final int DW_LYING = 25;

    public static final byte SOUND_MODE_NONE = 0;
    public static final byte SOUND_MODE_FREE_ROAMING = 1;
    public static final byte SOUND_MODE_WORKING = 2;
    public static final byte SOUND_MODE_BREAKTIME = 3;
    public static final byte SOUND_MODE_AFTERWORK_ROAMING = 4;

    public static final byte ONESHOT_NONE = 0;
    public static final byte ONESHOT_DAY_START = 1;
    public static final byte ONESHOT_DAY_END = 2;
    public static final byte ONESHOT_BREAK_START = 3;
    public static final byte ONESHOT_BREAK_END = 4;
    public static final byte ONESHOT_SMOKING = 5;
    public static final byte ONESHOT_CHANGING_CLOTHES = 6;
    public static final byte ONESHOT_GET_INTO_BED = 7;
    public static final byte ONESHOT_GET_UP = 8;

    public static final byte OUTFIT_WORK = 0;
    public static final byte OUTFIT_AFTERWORK = 1;
    public static final byte OUTFIT_PIJAMA = 2;

    private static final float ONESHOT_CHANCE = 0.25F;

    private int homeX;
    private int homeY;
    private int homeZ;
    private int homeDim;
    private boolean hasHomeLocker;
    private boolean killedByLockerDestroy;

    /** Player shift-RC on locker toggle; OR'd with redstone in {@link #isForcedStayAtLocker()}. */
    private boolean playerForcedStay;

    /** After afterwork death: stand at locker until morning. */
    private boolean waitingForMorningAfterDeath;

    private int sleepBedX;
    private int sleepBedY;
    private int sleepBedZ;
    private boolean hasSleepBed;

    /** Guard against recursive pack-aggro notifications. */
    private boolean packAggroSuppress;

    protected final EntityAIWanderNearMachines wanderAI;
    private final EntityAIReturnToLocker returnAI;
    private final EntityAIBreakTime breakAI;
    private final EntityAINightRoutine nightAI;
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
        nightAI = new EntityAINightRoutine(this);
        soundManager = new WorkerSoundManager(this);

        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIPanic(this, 1.25D));
        tasks.addTask(2, new EntityAIAttackHostile(this));
        tasks.addTask(3, nightAI);
        tasks.addTask(4, returnAI);
        tasks.addTask(5, breakAI);
        tasks.addTask(6, wanderAI);
        tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
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
        dataWatcher.addObject(DW_OUTFIT, Byte.valueOf(OUTFIT_WORK));
        dataWatcher.addObject(DW_LYING, Byte.valueOf((byte) 0));
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

    public void triggerInteractionSound() {
        dataWatcher.updateObject(DW_INTERACT_SEQ, Integer.valueOf(getInteractionSoundSeq() + 1));
    }

    public int getOneshotSoundSeq() {
        return dataWatcher.getWatchableObjectInt(DW_ONESHOT_SEQ);
    }

    public byte getOneshotSoundKind() {
        return dataWatcher.getWatchableObjectByte(DW_ONESHOT_KIND);
    }

    public void triggerOneshotSound(byte kind) {
        dataWatcher.updateObject(DW_ONESHOT_KIND, Byte.valueOf(kind));
        dataWatcher.updateObject(DW_ONESHOT_SEQ, Integer.valueOf(getOneshotSoundSeq() + 1));
    }

    public byte getOutfit() {
        return dataWatcher.getWatchableObjectByte(DW_OUTFIT);
    }

    public void setOutfit(byte outfit) {
        if (getOutfit() != outfit) {
            dataWatcher.updateObject(DW_OUTFIT, Byte.valueOf(outfit));
        }
    }

    public boolean isLyingInBed() {
        return dataWatcher.getWatchableObjectByte(DW_LYING) != 0;
    }

    private void setLyingFlag(boolean lying) {
        dataWatcher.updateObject(DW_LYING, Byte.valueOf(lying ? (byte) 1 : (byte) 0));
    }

    /** Hook for shift supervisor combat memory — no-op on normal workers. */
    public void onHostileChaseStarted(EntityLivingBase target) {}

    /** Hook for shift supervisor combat memory — no-op on normal workers. */
    public void onHostileChaseEnded(EntityLivingBase target, boolean defeated, boolean targetFled) {}

    public EntityAINightRoutine getNightAI() {
        return nightAI;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(20.0D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(48.0D);
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

    /** Player-toggled forced stay (locker shift-RC); OR with redstone. */
    public boolean isPlayerForcedStay() {
        return playerForcedStay;
    }

    /**
     * Forced stay: player toggle (locker shift-RC) OR redstone into locker top/bottom.
     */
    public boolean isForcedStayAtLocker() {
        if (playerForcedStay) {
            return true;
        }
        TileEntityLocker te = getHomeLockerTE();
        return te != null && te.isRedstoneForcedStay();
    }

    public void setForcedStayAtLocker(boolean stay) {
        this.playerForcedStay = stay;
    }

    public void toggleForcedStayAtLocker() {
        playerForcedStay = !playerForcedStay;
    }

    public boolean isWaitingForMorningAfterDeath() {
        return waitingForMorningAfterDeath;
    }

    public void setWaitingForMorningAfterDeath(boolean v) {
        waitingForMorningAfterDeath = v;
    }

    /** Called when migrating from legacy overnight storage into living afterwork. */
    public void beginNightAfterRelease() {
        setOutfit(OUTFIT_AFTERWORK);
    }

    /**
     * Aggressive mode for combat: configured aggressive AND not in bed/pajamas.
     * Afterwork keeps configured aggressive/passive; bed/pijama forced peaceful.
     */
    public boolean isAggressiveModeActive() {
        if (isLyingInBed() || getOutfit() == OUTFIT_PIJAMA) {
            return false;
        }
        if (!Config.aggressiveModeAllowed) {
            return false;
        }
        TileEntityLocker te = getHomeLockerTE();
        return te != null && te.isAggressive();
    }

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
        if (WorkerSchedule.isLocker(worldObj)) {
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
        if (WorkerSchedule.isLocker(worldObj)) {
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
        if (te instanceof TileEntityLocker) {
            return (TileEntityLocker) te;
        }
        te = worldObj.getTileEntity(homeX, homeY - 1, homeZ);
        if (te instanceof TileEntityLocker) {
            homeY = homeY - 1;
            return (TileEntityLocker) te;
        }
        te = worldObj.getTileEntity(homeX, homeY + 1, homeZ);
        if (te instanceof TileEntityLocker) {
            homeY = homeY + 1;
            return (TileEntityLocker) te;
        }
        return null;
    }

    public boolean isWaitingAtLocker() {
        if (!hasHomeLocker) {
            return false;
        }
        if (!shouldStandAtLockerGate()) {
            return false;
        }
        return returnAI.isStandingAtLocker() || nightAI.getStage() == EntityAINightRoutine.Stage.STAND_LOCKER_NIGHT
            || nightAI.getStage() == EntityAINightRoutine.Stage.STAND_FORCED_AFTERWORK;
    }

    private boolean shouldStandAtLockerGate() {
        return isForcedStayAtLocker() || WorkerSchedule.isLocker(worldObj);
    }

    public EntityAIWanderNearMachines.SoundPhase getDaySoundPhase() {
        return wanderAI.getSoundPhase();
    }

    public boolean isBreakPhaseActive() {
        return !isForcedStayAtLocker() && WorkerSchedule.isBreak(worldObj) && !isLyingInBed();
    }

    public void setDeadFromLockerDestroyed() {
        killedByLockerDestroy = true;
        setDead();
    }

    /** @deprecated v13 — no overnight despawn. Kept as no-op for safety. */
    public void enterLockerForNight() {
        // no-op: worker stays in world
    }

    public void setDeadFromEnteringLocker() {
        // no-op legacy
    }

    public void playGetIntoBedSounds() {
        // changing_clothes 100% + get_into_bed 25%
        triggerOneshotSound(ONESHOT_CHANGING_CLOTHES);
        if (getRNG().nextFloat() < ONESHOT_CHANCE) {
            // Delay get_into_bed via second bump — client plays latest oneshot; play at entity pos too
            List<String> list = ModSounds.getIntoBed();
            if (list != null && !list.isEmpty() && worldObj != null && !worldObj.isRemote) {
                String name = list.get(getRNG().nextInt(list.size()));
                float vol = Math.max(0.0F, Config.soundVolume);
                if (vol > 0.0F) {
                    worldObj.playSoundAtEntity(this, name, vol, 0.95F + getRNG().nextFloat() * 0.1F);
                }
            }
        }
    }

    public void lieInLinkedBed() {
        TileEntityLocker te = getHomeLockerTE();
        if (te == null || !te.hasLinkedBed() || worldObj == null || worldObj.isRemote) {
            return;
        }
        if (te.getBedDim() != worldObj.provider.dimensionId) {
            return;
        }
        int bx = te.getBedX();
        int by = te.getBedY();
        int bz = te.getBedZ();
        lieInBedAt(bx, by, bz);
    }

    public void lieInBedAtCurrent() {
        if (hasSleepBed) {
            lieInBedAt(sleepBedX, sleepBedY, sleepBedZ);
        }
    }

    public void lieInBedAt(int feetX, int feetY, int feetZ) {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        sleepBedX = feetX;
        sleepBedY = feetY;
        sleepBedZ = feetZ;
        hasSleepBed = true;
        setOutfit(OUTFIT_PIJAMA);
        setLyingFlag(true);
        getNavigator().clearPathEntity();
        setPosition(feetX + 0.5D, feetY + 0.5625D, feetZ + 0.5D);
        motionX = motionY = motionZ = 0.0D;
        // Occupy both halves
        if (worldObj.getBlock(feetX, feetY, feetZ) instanceof BlockWorkerBed) {
            int meta = worldObj.getBlockMetadata(feetX, feetY, feetZ);
            BlockWorkerBed.setOccupied(worldObj, feetX, feetY, feetZ, true);
            int[] head = BlockWorkerBed.headCoords(feetX, feetY, feetZ, meta);
            if (worldObj.getBlock(head[0], head[1], head[2]) instanceof BlockWorkerBed) {
                BlockWorkerBed.setOccupied(worldObj, head[0], head[1], head[2], true);
            }
            // Face along bed
            int dir = BlockWorkerBed.getDirection(meta);
            float yaw = dir == 0 ? 0.0F : dir == 1 ? 90.0F : dir == 2 ? 180.0F : 270.0F;
            rotationYaw = yaw;
            rotationYawHead = yaw;
        }
    }

    /**
     * @param playGetUp if true, 25% get_up oneshot
     */
    public void wakeFromBed(boolean playGetUp) {
        if (worldObj != null && !worldObj.isRemote && hasSleepBed) {
            if (worldObj.getBlock(sleepBedX, sleepBedY, sleepBedZ) instanceof BlockWorkerBed) {
                int meta = worldObj.getBlockMetadata(sleepBedX, sleepBedY, sleepBedZ);
                BlockWorkerBed.setOccupied(worldObj, sleepBedX, sleepBedY, sleepBedZ, false);
                int[] head = BlockWorkerBed.headCoords(sleepBedX, sleepBedY, sleepBedZ, meta);
                if (worldObj.getBlock(head[0], head[1], head[2]) instanceof BlockWorkerBed) {
                    BlockWorkerBed.setOccupied(worldObj, head[0], head[1], head[2], false);
                }
            }
            // Stand beside bed
            setPosition(sleepBedX + 0.5D, sleepBedY + 0.1D, sleepBedZ + 0.5D);
        }
        setLyingFlag(false);
        setOutfit(OUTFIT_AFTERWORK); // pajamas ONLY while in bed
        hasSleepBed = false;
        if (playGetUp && getRNG().nextFloat() < ONESHOT_CHANCE) {
            triggerOneshotSound(ONESHOT_GET_UP);
        }
    }

    @Override
    public void onLivingUpdate() {
        if (isLyingInBed()) {
            // Keep still while sleeping
            motionX = motionY = motionZ = 0.0D;
            if (hasSleepBed) {
                setPosition(sleepBedX + 0.5D, sleepBedY + 0.5625D, sleepBedZ + 0.5D);
            }
        }
        super.onLivingUpdate();
        if (!worldObj.isRemote) {
            tickScheduleEdgesAndSmoking();
            soundManager.onUpdate();
        } else {
            LockerWorkerMod.proxy.tickWorkerClientSounds(this);
        }
    }

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

        if (phase == WorkerSchedule.Phase.BREAK && !isForcedStayAtLocker() && !isLyingInBed()) {
            tickSmoking();
        }
    }

    private void onPhaseTransition(WorkerSchedule.Phase from, WorkerSchedule.Phase to) {
        // day_end edge still optional 25% (clothes change also plays work_exit at locker)
        if (from == WorkerSchedule.Phase.WORK && to == WorkerSchedule.Phase.LOCKER) {
            maybeOneshot(ONESHOT_DAY_END);
        }
        if (to == WorkerSchedule.Phase.BREAK) {
            maybeOneshot(ONESHOT_BREAK_START);
        }
        if (from == WorkerSchedule.Phase.BREAK
            && (to == WorkerSchedule.Phase.WORK || to == WorkerSchedule.Phase.LOCKER)) {
            maybeOneshot(ONESHOT_BREAK_END);
        }
        // day_start moved to morning clothes change at locker (25%)
    }

    private void maybeOneshot(byte kind) {
        if (getRNG().nextFloat() < ONESHOT_CHANCE) {
            triggerOneshotSound(kind);
        }
    }

    private void resetSmokingCadence() {
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
                smokeExhaleCooldown = 12 + getRNG().nextInt(18);
            }
            if (smokeBurstLeft <= 0) {
                smokeQuietLeft = 80 + getRNG().nextInt(281);
            }
            return;
        }
        if (smokeQuietLeft > 0) {
            smokeQuietLeft--;
            return;
        }
        smokeBurstLeft = 1 + getRNG().nextInt(3);
        smokeExhaleCooldown = 0;
    }

    @Override
    public void setDead() {
        if (worldObj != null && worldObj.isRemote) {
            LockerWorkerMod.proxy.stopWorkerClientSounds(getEntityId());
        }
        if (!worldObj.isRemote && isLyingInBed()) {
            wakeFromBed(false);
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
        TileEntityLocker te = getHomeLockerTE();
        if (te != null) {
            te.onWorkerDied(this);
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
        // Right-click: chat linked locker ID + interaction sound (v15; no shift forced-stay)
        player.addChatMessage(new ChatComponentText(getLinkedLockerIdChat()));
        soundManager.playInteraction();
        return true;
    }

    /** Linked locker durable UUID chat line, or "(none)" if unbound. */
    public String getLinkedLockerIdChat() {
        TileEntityLocker te = getHomeLockerTE();
        return LockerLink.formatChatId(te != null ? te.getLockerId() : null);
    }

    @Override
    public boolean canBePushed() {
        return !isLyingInBed();
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setBoolean("HasHomeLocker", hasHomeLocker);
        tag.setInteger("HomeX", homeX);
        tag.setInteger("HomeY", homeY);
        tag.setInteger("HomeZ", homeZ);
        tag.setInteger("HomeDim", homeDim);
        tag.setBoolean("ForcedStayAtLocker", playerForcedStay);
        tag.setByte("Outfit", getOutfit());
        tag.setBoolean("LyingInBed", isLyingInBed());
        tag.setBoolean("WaitingForMorningAfterDeath", waitingForMorningAfterDeath);
        tag.setBoolean("HasSleepBed", hasSleepBed);
        if (hasSleepBed) {
            tag.setInteger("SleepBedX", sleepBedX);
            tag.setInteger("SleepBedY", sleepBedY);
            tag.setInteger("SleepBedZ", sleepBedZ);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        hasHomeLocker = tag.getBoolean("HasHomeLocker");
        homeX = tag.getInteger("HomeX");
        homeY = tag.getInteger("HomeY");
        homeZ = tag.getInteger("HomeZ");
        homeDim = tag.getInteger("HomeDim");
        playerForcedStay = tag.getBoolean("ForcedStayAtLocker");
        if (tag.hasKey("Outfit")) {
            setOutfit(tag.getByte("Outfit"));
        }
        waitingForMorningAfterDeath = tag.getBoolean("WaitingForMorningAfterDeath");
        hasSleepBed = tag.getBoolean("HasSleepBed");
        if (hasSleepBed) {
            sleepBedX = tag.getInteger("SleepBedX");
            sleepBedY = tag.getInteger("SleepBedY");
            sleepBedZ = tag.getInteger("SleepBedZ");
        }
        if (tag.getBoolean("LyingInBed")) {
            setLyingFlag(true);
        }
    }
}
