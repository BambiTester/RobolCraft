package com.angelika.lockerworker.tileentity;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.angelika.lockerworker.CommonProxy;
import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.LockerWorkerMod;
import com.angelika.lockerworker.block.BlockWorkerBed;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.item.ItemWorkerBed;
import com.angelika.lockerworker.sound.ModSounds;
import com.angelika.lockerworker.util.LockerLink;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * Bottom-half locker TE. Binds a worker by UUID, holds durable {@link #lockerId} for
 * bed linking, and <b>never</b> despawns the worker overnight (v13).
 *
 * <p>
 * Redstone: locker <b>receives</b> power on top or bottom to force stay. It does
 * <b>not</b> emit waiting power anymore (legacy workerStored=15 removed).
 */
public class TileEntityLocker extends TileEntity {

    private UUID workerUUID;
    private int workerEntityId = -1;
    private boolean pendingRespawn;
    private int respawnCooldown;

    /** Aggressive = worker attacks hostiles; peaceful = passive AI only. */
    private boolean aggressive;

    /**
     * Durable locker ↔ bed link id. Assigned on place; migrated for old lockers.
     */
    private UUID lockerId;

    /** True until a linked bed block exists in the world (or replacement dropped). */
    private boolean bedOwed = true;

    private boolean hasBedPos;
    private int bedX;
    private int bedY;
    private int bedZ;
    private int bedDim;

    /**
     * Legacy v12 overnight storage flag — migrated away on first tick (release worker).
     */
    private boolean legacyWorkerStored;

    /** One-shot: old locker loaded without lockerId → owe a bed drop. */
    private boolean needsMigrationBedDrop;

    /** After afterwork-death: wait at locker until tick 0 before morning routine. */
    private boolean waitForMorningAfterDeath;

    private int bedWatchCooldown;

    /** Ticks to wait after worker death before respawning. */
    public static final int RESPAWN_DELAY_TICKS = 100;

    private static final float WORK_EXIT_CHANCE = 0.75F;
    private static final float DAY_START_CHANCE = 0.25F;

    public void onPlacedBy(net.minecraft.entity.EntityLivingBase placer) {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        ensureLockerId();
        bedOwed = true;
        hasBedPos = false;
        giveOrDropBedItem(placer instanceof EntityPlayer ? (EntityPlayer) placer : null);
        // Always spawn living worker (no night despawn storage)
        spawnWorker(false, EntityLockerWorker.OUTFIT_WORK);
    }

    /** Assign UUID if missing (new place or old-world migrate). */
    public void ensureLockerId() {
        if (lockerId == null) {
            lockerId = UUID.randomUUID();
            markDirty();
        }
    }

    public UUID getLockerId() {
        return lockerId;
    }

    public UUID getWorkerUUID() {
        return workerUUID;
    }

    public void setWorkerUUID(UUID uuid) {
        this.workerUUID = uuid;
        markDirty();
    }

    public void setWorkerEntityId(int id) {
        this.workerEntityId = id;
        markDirty();
    }

    /** @deprecated v13 — overnight storage removed; always false after migrate. */
    public boolean isWorkerStored() {
        return false;
    }

    public boolean isAggressive() {
        return aggressive && Config.aggressiveModeAllowed;
    }

    public boolean isAggressiveRaw() {
        return aggressive;
    }

    public void setAggressive(boolean value) {
        boolean next = value && Config.aggressiveModeAllowed;
        if (aggressive == next) {
            if (!Config.aggressiveModeAllowed && aggressive) {
                aggressive = false;
                markDirty();
                syncToClient();
            }
            return;
        }
        aggressive = next;
        markDirty();
        syncToClient();
    }

    public void toggleAggressive(EntityPlayer player) {
        if (!Config.aggressiveModeAllowed) {
            if (aggressive) {
                aggressive = false;
                markDirty();
                syncToClient();
            }
            if (player != null && !worldObj.isRemote) {
                player.addChatMessage(new ChatComponentText("Aggressive mode disabled in config."));
            }
            return;
        }
        aggressive = !aggressive;
        markDirty();
        syncToClient();
        if (player != null && !worldObj.isRemote) {
            player.addChatMessage(new ChatComponentText(aggressive ? "Locker: AGGRESSIVE" : "Locker: peaceful"));
        }
    }

    /**
     * Redstone power into bottom or top half forces stay (same as shift-toggle).
     * Locker never emits redstone.
     */
    public boolean isRedstoneForcedStay() {
        if (worldObj == null) {
            return false;
        }
        return worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord)
            || worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord + 1, zCoord);
    }

    /** Combined force: player toggle on worker OR redstone into locker. */
    public boolean isForceStayActive(EntityLockerWorker worker) {
        if (isRedstoneForcedStay()) {
            return true;
        }
        return worker != null && worker.isPlayerForcedStay();
    }

    public boolean hasLinkedBed() {
        return hasBedPos && verifyBedStillThere();
    }

    public int getBedX() {
        return bedX;
    }

    public int getBedY() {
        return bedY;
    }

    public int getBedZ() {
        return bedZ;
    }

    public int getBedDim() {
        return bedDim;
    }

    public void onLinkedBedPlaced(int feetX, int feetY, int feetZ, int dim) {
        this.bedX = feetX;
        this.bedY = feetY;
        this.bedZ = feetZ;
        this.bedDim = dim;
        this.hasBedPos = true;
        this.bedOwed = false;
        markDirty();
    }

    public void onLinkedBedRemoved() {
        this.hasBedPos = false;
        this.bedOwed = true;
        markDirty();
    }

    public boolean isWaitForMorningAfterDeath() {
        return waitForMorningAfterDeath;
    }

    public void clearWaitForMorningAfterDeath() {
        waitForMorningAfterDeath = false;
        markDirty();
    }

    /** Called when stay toggle / mode may affect clients. */
    public void onWorkerStayOrModeMaybeChanged() {
        // no redstone emit
    }

    public void killAssignedWorker() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        EntityLockerWorker worker = findWorker();
        if (worker != null && !worker.isDead) {
            worker.setDeadFromLockerDestroyed();
        }
        workerUUID = null;
        workerEntityId = -1;
        pendingRespawn = false;
        legacyWorkerStored = false;
        // Remove linked bed in world
        removeLinkedBedInWorld();
        markDirty();
    }

    private void removeLinkedBedInWorld() {
        if (!hasBedPos || worldObj == null) {
            return;
        }
        if (worldObj.provider.dimensionId != bedDim) {
            return;
        }
        if (worldObj.getBlock(bedX, bedY, bedZ) == CommonProxy.blockWorkerBed) {
            int meta = worldObj.getBlockMetadata(bedX, bedY, bedZ);
            int[] head = BlockWorkerBed.headCoords(bedX, bedY, bedZ, meta);
            worldObj.setBlockToAir(bedX, bedY, bedZ);
            if (worldObj.getBlock(head[0], head[1], head[2]) == CommonProxy.blockWorkerBed) {
                worldObj.setBlockToAir(head[0], head[1], head[2]);
            }
        }
        hasBedPos = false;
        bedOwed = false; // locker going away — don't owe replacement
    }

    /**
     * Worker died (not locker destroy). Afterwork/pijama → respawn at locker and wait
     * until tick 0. Work outfit → normal delayed respawn.
     */
    public void onWorkerDied(EntityLockerWorker deadWorker) {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        boolean afterworkDeath = deadWorker != null && deadWorker.getOutfit() != EntityLockerWorker.OUTFIT_WORK;
        pendingRespawn = true;
        respawnCooldown = RESPAWN_DELAY_TICKS;
        workerEntityId = -1;
        if (afterworkDeath) {
            waitForMorningAfterDeath = true;
        }
        markDirty();
    }

    /** Legacy no-arg for any leftover callers. */
    public void onWorkerDied() {
        onWorkerDied(null);
    }

    public void bindWorker(EntityLockerWorker worker) {
        if (worker == null) {
            return;
        }
        workerUUID = worker.getUniqueID();
        workerEntityId = worker.getEntityId();
        pendingRespawn = false;
        legacyWorkerStored = false;
        worker.setHomeLocker(xCoord, yCoord, zCoord, worldObj.provider.dimensionId);
        markDirty();
    }

    /**
     * Evening outfit change at locker: changing_clothes 100%, then locker_sound 100% +
     * work_exit 75%.
     */
    public void playEveningClothesChangeSounds() {
        playChangingClothesAtLocker();
        playLockerSoundAtLocker();
        maybePlayWorkExitAtLocker();
    }

    /**
     * Morning outfit change at locker: changing_clothes 100%, then locker_sound 100% +
     * day_start 25%.
     */
    public void playMorningClothesChangeSounds() {
        playChangingClothesAtLocker();
        playLockerSoundAtLocker();
        maybePlayDayStartAtLocker();
    }

    private void playChangingClothesAtLocker() {
        playRandomFrom(ModSounds.changingClothes(), 1.0F);
    }

    private void playLockerSoundAtLocker() {
        playRandomFrom(ModSounds.lockerSound(), 1.0F);
    }

    private void maybePlayWorkExitAtLocker() {
        List<String> list = ModSounds.workExit();
        if (list == null || list.isEmpty()) {
            return;
        }
        if (worldObj.rand.nextFloat() >= WORK_EXIT_CHANCE) {
            return;
        }
        playSoundAtLocker(list.get(worldObj.rand.nextInt(list.size())), worldObj.rand, 1.0F);
    }

    private void maybePlayDayStartAtLocker() {
        List<String> list = ModSounds.dayStart();
        if (list == null || list.isEmpty()) {
            return;
        }
        if (worldObj.rand.nextFloat() >= DAY_START_CHANCE) {
            return;
        }
        playSoundAtLocker(list.get(worldObj.rand.nextInt(list.size())), worldObj.rand, 1.0F);
    }

    private void playRandomFrom(List<String> list, float volumeMul) {
        if (list == null || list.isEmpty() || worldObj == null) {
            return;
        }
        playSoundAtLocker(list.get(worldObj.rand.nextInt(list.size())), worldObj.rand, volumeMul);
    }

    private void playSoundAtLocker(String name, Random rand, float volumeMul) {
        float vol = Math.max(0.0F, Config.soundVolume) * volumeMul;
        if (vol <= 0.0F) {
            return;
        }
        float pitch = 0.95F + rand.nextFloat() * 0.1F;
        worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, name, vol, pitch);
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (!Config.aggressiveModeAllowed && aggressive) {
            aggressive = false;
            markDirty();
            syncToClient();
        }

        ensureLockerId();

        // Migrate old lockers missing ID → owe one bed drop at locker
        if (needsMigrationBedDrop) {
            needsMigrationBedDrop = false;
            bedOwed = true;
            dropBedItemAtLocker();
            markDirty();
        }

        // Migrate legacy overnight storage → living afterwork worker
        if (legacyWorkerStored) {
            legacyWorkerStored = false;
            EntityLockerWorker w = spawnWorker(false, EntityLockerWorker.OUTFIT_AFTERWORK);
            if (w != null && WorkerSchedule.isLocker(worldObj)) {
                w.beginNightAfterRelease();
            }
            markDirty();
        }

        // Bed owed / destroyed-item replacement (throttled)
        if (bedWatchCooldown > 0) {
            bedWatchCooldown--;
        } else {
            bedWatchCooldown = 40;
            tickBedOwnership();
        }

        if (pendingRespawn) {
            if (respawnCooldown > 0) {
                respawnCooldown--;
            } else {
                byte outfit = waitForMorningAfterDeath ? EntityLockerWorker.OUTFIT_AFTERWORK
                    : EntityLockerWorker.OUTFIT_WORK;
                EntityLockerWorker w = spawnWorker(false, outfit);
                pendingRespawn = false;
                if (w != null && waitForMorningAfterDeath) {
                    w.setWaitingForMorningAfterDeath(true);
                }
                markDirty();
            }
        } else if (workerUUID != null && findWorker() == null) {
            pendingRespawn = true;
            respawnCooldown = RESPAWN_DELAY_TICKS;
        }
    }

    private void tickBedOwnership() {
        if (hasBedPos) {
            if (!verifyBedStillThere()) {
                hasBedPos = false;
                bedOwed = true;
                markDirty();
            } else {
                bedOwed = false;
                return;
            }
        }
        if (!bedOwed) {
            return;
        }
        // Bed owed: if neither placed, nor item entity, nor in any player inv → drop replacement
        if (worldHasLinkedBedBlock() || worldHasLinkedBedItemEntity() || playerHasLinkedBedItem()) {
            return;
        }
        dropBedItemAtLocker();
    }

    private boolean verifyBedStillThere() {
        if (!hasBedPos || worldObj == null) {
            return false;
        }
        if (worldObj.provider.dimensionId != bedDim) {
            return false;
        }
        if (worldObj.getBlock(bedX, bedY, bedZ) != CommonProxy.blockWorkerBed) {
            return false;
        }
        TileEntityWorkerBed te = BlockWorkerBed.getBedTE(worldObj, bedX, bedY, bedZ);
        return te != null && lockerId != null && lockerId.equals(te.getLockerId());
    }

    private boolean worldHasLinkedBedBlock() {
        return verifyBedStillThere();
    }

    @SuppressWarnings("unchecked")
    private boolean worldHasLinkedBedItemEntity() {
        if (lockerId == null || worldObj == null) {
            return false;
        }
        // Broad loaded-entity scan around locker (items elsewhere still covered via player inv)
        AxisAlignedBB box = AxisAlignedBB
            .getBoundingBox(xCoord - 128, yCoord - 64, zCoord - 128, xCoord + 129, yCoord + 65, zCoord + 129);
        List<EntityItem> items = worldObj.getEntitiesWithinAABB(EntityItem.class, box);
        for (EntityItem ei : items) {
            if (ei == null || ei.isDead) {
                continue;
            }
            ItemStack s = ei.getEntityItem();
            if (s != null && s.getItem() == CommonProxy.itemWorkerBed
                && lockerId.equals(LockerLink.readIdFromStack(s))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean playerHasLinkedBedItem() {
        if (lockerId == null || worldObj == null) {
            return false;
        }
        List<EntityPlayer> players = worldObj.playerEntities;
        for (EntityPlayer p : players) {
            if (p == null) {
                continue;
            }
            for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
                ItemStack s = p.inventory.getStackInSlot(i);
                if (s != null && s.getItem() == CommonProxy.itemWorkerBed
                    && lockerId.equals(LockerLink.readIdFromStack(s))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void giveOrDropBedItem(EntityPlayer player) {
        ItemStack bed = ItemWorkerBed.createLinked(lockerId, xCoord, yCoord, zCoord, worldObj.provider.dimensionId);
        if (player != null) {
            if (player.inventory.addItemStackToInventory(bed)) {
                player.inventory.markDirty();
                return;
            }
        }
        dropStackAtLocker(bed);
    }

    private void dropBedItemAtLocker() {
        if (lockerId == null || worldObj == null || worldObj.isRemote) {
            return;
        }
        ItemStack bed = ItemWorkerBed.createLinked(lockerId, xCoord, yCoord, zCoord, worldObj.provider.dimensionId);
        dropStackAtLocker(bed);
        LockerWorkerMod.LOG
            .info("Dropped linked worker bed at locker ({}, {}, {}) id={}", xCoord, yCoord, zCoord, lockerId);
    }

    private void dropStackAtLocker(ItemStack stack) {
        EntityItem ei = new EntityItem(worldObj, xCoord + 0.5D, yCoord + 1.0D, zCoord + 0.5D, stack);
        ei.delayBeforeCanPickup = 10;
        worldObj.spawnEntityInWorld(ei);
    }

    private void syncToClient() {
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            worldObj.markBlockForUpdate(xCoord, yCoord + 1, zCoord);
        }
    }

    private EntityLockerWorker spawnWorker(boolean unusedDayStart, byte outfit) {
        if (worldObj == null || worldObj.isRemote) {
            return null;
        }
        EntityLockerWorker worker = new EntityLockerWorker(worldObj);
        int meta = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
        int facing = meta & 0x3;
        double ox = 0.5;
        double oz = 0.5;
        switch (facing) {
            case 0:
                oz = 1.5;
                break;
            case 1:
                ox = -0.5;
                break;
            case 2:
                oz = -0.5;
                break;
            case 3:
                ox = 1.5;
                break;
            default:
                oz = 1.5;
                break;
        }
        worker.setLocationAndAngles(xCoord + ox, yCoord, zCoord + oz, 0.0F, 0.0F);
        worker.setHomeLocker(xCoord, yCoord, zCoord, worldObj.provider.dimensionId);
        worker.setOutfit(outfit);
        worldObj.spawnEntityInWorld(worker);
        bindWorker(worker);
        LockerWorkerMod.LOG.info(
            "Spawned LockerWorker at locker ({}, {}, {}) dim={} outfit={}",
            xCoord,
            yCoord,
            zCoord,
            worldObj.provider.dimensionId,
            outfit);
        return worker;
    }

    @SuppressWarnings("unchecked")
    private EntityLockerWorker findWorker() {
        if (workerUUID == null) {
            return null;
        }
        if (workerEntityId >= 0) {
            Entity e = worldObj.getEntityByID(workerEntityId);
            if (e instanceof EntityLockerWorker && workerUUID.equals(e.getUniqueID())) {
                return (EntityLockerWorker) e;
            }
        }
        AxisAlignedBB box = AxisAlignedBB
            .getBoundingBox(xCoord - 64, yCoord - 16, zCoord - 64, xCoord + 65, yCoord + 17, zCoord + 65);
        List<EntityLockerWorker> list = worldObj.getEntitiesWithinAABB(EntityLockerWorker.class, box);
        for (EntityLockerWorker w : list) {
            if (workerUUID.equals(w.getUniqueID())) {
                workerEntityId = w.getEntityId();
                return w;
            }
        }
        return null;
    }

    /**
     * Find a locker TE in this world with the given durable id (loaded chunks only).
     */
    @SuppressWarnings("unchecked")
    public static TileEntityLocker findByLockerId(World world, UUID id) {
        if (world == null || id == null) {
            return null;
        }
        for (Object o : world.loadedTileEntityList) {
            if (o instanceof TileEntityLocker) {
                TileEntityLocker te = (TileEntityLocker) o;
                if (id.equals(te.getLockerId())) {
                    return te;
                }
            }
        }
        return null;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
        if (worldObj != null && worldObj.isRemote) {
            worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord + 1, zCoord);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (workerUUID != null) {
            tag.setLong("WorkerUUIDMost", workerUUID.getMostSignificantBits());
            tag.setLong("WorkerUUIDLeast", workerUUID.getLeastSignificantBits());
        }
        tag.setInteger("WorkerEntityId", workerEntityId);
        tag.setBoolean("PendingRespawn", pendingRespawn);
        tag.setInteger("RespawnCooldown", respawnCooldown);
        tag.setBoolean("Aggressive", aggressive);
        // Persist legacy flag as false always after migrate; keep key for clarity
        tag.setBoolean("WorkerStored", false);
        if (lockerId != null) {
            LockerLink.writeId(tag, lockerId);
        }
        tag.setBoolean("BedOwed", bedOwed);
        tag.setBoolean("HasBedPos", hasBedPos);
        if (hasBedPos) {
            tag.setInteger("BedX", bedX);
            tag.setInteger("BedY", bedY);
            tag.setInteger("BedZ", bedZ);
            tag.setInteger("BedDim", bedDim);
        }
        tag.setBoolean("WaitForMorningAfterDeath", waitForMorningAfterDeath);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("WorkerUUIDMost") && tag.hasKey("WorkerUUIDLeast")) {
            workerUUID = new UUID(tag.getLong("WorkerUUIDMost"), tag.getLong("WorkerUUIDLeast"));
        } else {
            workerUUID = null;
        }
        workerEntityId = tag.hasKey("WorkerEntityId") ? tag.getInteger("WorkerEntityId") : -1;
        pendingRespawn = tag.getBoolean("PendingRespawn");
        respawnCooldown = tag.getInteger("RespawnCooldown");
        aggressive = tag.getBoolean("Aggressive");

        // Migrate: old lockers with WorkerStored true
        legacyWorkerStored = tag.getBoolean("WorkerStored");

        lockerId = LockerLink.readId(tag);
        if (lockerId == null) {
            // Old world locker — assign on next tick + drop bed
            needsMigrationBedDrop = true;
        }

        if (tag.hasKey("BedOwed")) {
            bedOwed = tag.getBoolean("BedOwed");
        } else {
            bedOwed = true;
        }
        hasBedPos = tag.getBoolean("HasBedPos");
        if (hasBedPos) {
            bedX = tag.getInteger("BedX");
            bedY = tag.getInteger("BedY");
            bedZ = tag.getInteger("BedZ");
            bedDim = tag.getInteger("BedDim");
        }
        waitForMorningAfterDeath = tag.getBoolean("WaitForMorningAfterDeath");
    }
}
