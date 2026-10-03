package com.angelika.robolcraft.tileentity;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.angelika.robolcraft.CommonProxy;
import com.angelika.robolcraft.Config;
import com.angelika.robolcraft.RobolCraftMod;
import com.angelika.robolcraft.block.BlockWorkerBed;
import com.angelika.robolcraft.entity.EntityRobolCraft;
import com.angelika.robolcraft.item.ItemWorkerBed;
import com.angelika.robolcraft.sound.ModSounds;
import com.angelika.robolcraft.util.LockerLink;
import com.angelika.robolcraft.util.ShortIdRegistry;
import com.angelika.robolcraft.util.WorkerSchedule;

/**
 * Bottom-half locker TE. Binds a worker by UUID, holds durable {@link #lockerId} for
 * bed linking, and <b>never</b> despawns the worker overnight (v13).
 *
 * <p>
 * Redstone: locker <b>receives</b> power on top or bottom to force stay. It does
 * <b>not</b> emit waiting power anymore (legacy workerStored=15 removed).
 */
public class TileEntityLocker extends TileEntity implements IInventory {

    private UUID workerUUID;
    private int workerEntityId = -1;
    private boolean pendingRespawn;
    private int respawnCooldown;

    /** Aggressive = worker attacks hostiles; peaceful = passive AI only. */
    private boolean aggressive;

    /**
     * Player Stay toggle mirrored on the TE (like aggressive) so the GUI client
     * can refresh Stay: ON/OFF via description-packet sync. Authoritative AI flag
     * still lives on the worker entity (datawatcher).
     */
    private boolean playerForcedStay;

    /**
     * Durable locker ↔ bed link id. Assigned on place; migrated for old lockers.
     */
    private UUID lockerId;

    /** Short sequential display ID (>=1). Worker and supervisor use separate pools. */
    private int shortId = -1;
    private boolean shortIdIsSupervisor;

    /** Single GUI bed slot — holds linked bed item while not placed in world. */
    private ItemStack bedSlot;

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
    /** v25: skip AABB/player scans while last “exists elsewhere” was true. */
    private int bedExistsCacheTicks;
    /** v25: consecutive ticks findWorker missed while UUID set (chunk-unload grace). */
    private int workerMissingTicks;

    /**
     * Per-locker day work leash (XZ). {@code 0} = unlimited. Default from
     * {@link Config#maxDistanceFromLocker}. Break/bed ignore this.
     */
    private int maxWorkDistance = -1; // -1 = uninitialized → Config default on first use

    /** Ticks to wait after worker death before respawning. */
    public static final int RESPAWN_DELAY_TICKS = 100;

    public static final int MAX_WORK_DISTANCE_CAP = 9999;

    private static final float WORK_EXIT_CHANCE = 0.75F;
    private static final float DAY_START_CHANCE = 0.75F;

    public void onPlacedBy(net.minecraft.entity.EntityLivingBase placer) {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        ensureLockerId();
        // Keep registry bit set across chunk reloads
        if (shortId >= 1) {
            ShortIdRegistry reg = ShortIdRegistry.get(worldObj);
            if (reg != null) {
                if (isShortIdSupervisor()) {
                    reg.markSupervisorUsed(shortId);
                } else {
                    reg.markWorkerUsed(shortId);
                }
            }
        }
        bedOwed = true;
        hasBedPos = false;
        maxWorkDistance = clampMaxWorkDistance(Config.maxDistanceFromLocker);
        giveOrDropBedItem(placer instanceof EntityPlayer ? (EntityPlayer) placer : null);
        // Always spawn living worker (no night despawn storage)
        spawnWorker(false, EntityRobolCraft.OUTFIT_WORK);
    }

    /** Assign UUID if missing (new place or old-world migrate). */
    public void ensureLockerId() {
        if (lockerId == null) {
            lockerId = UUID.randomUUID();
            markDirty();
        }
        ensureShortId();
    }

    /** Lowest-free short ID; migrates legacy UUID-only lockers on first load. */
    public void ensureShortId() {
        if (shortId >= 1 || worldObj == null || worldObj.isRemote) {
            return;
        }
        shortIdIsSupervisor = isSupervisorLocker();
        ShortIdRegistry reg = ShortIdRegistry.get(worldObj);
        if (reg == null) {
            return;
        }
        shortId = shortIdIsSupervisor ? reg.allocateSupervisor() : reg.allocateWorker();
        markDirty();
        syncToClients();
        com.angelika.robolcraft.npc.NpcWorldFile
            .ensureId(worldObj, shortIdIsSupervisor ? "supervisor" : "worker", shortId);
    }

    /** Override in supervisor TE. */
    protected boolean isSupervisorLocker() {
        return false;
    }

    public int getShortId() {
        return shortId;
    }

    public boolean isShortIdSupervisor() {
        return shortIdIsSupervisor || isSupervisorLocker();
    }

    public String getShortLabel() {
        int kind = isShortIdSupervisor() ? LockerLink.KIND_SUPERVISOR : LockerLink.KIND_WORKER;
        return LockerLink.formatShortLabel(kind, shortId);
    }

    public String formatChatIdLine() {
        int kind = isShortIdSupervisor() ? LockerLink.KIND_SUPERVISOR : LockerLink.KIND_WORKER;
        return LockerLink.formatChatId(kind, shortId);
    }

    public UUID getLockerId() {
        return lockerId;
    }

    public EntityRobolCraft findWorkerPublic() {
        return findWorker();
    }

    public UUID getWorkerUUID() {
        return workerUUID;
    }

    public void setWorkerUUID(UUID uuid) {
        this.workerUUID = uuid;
        markDirty();
        syncToClients();
    }

    public void setWorkerEntityId(int id) {
        this.workerEntityId = id;
        markDirty();
        syncToClients();
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

    /** GUI Stay: ON/OFF — TE-synced copy of the worker player-forced-stay flag. */
    public boolean isPlayerForcedStay() {
        return playerForcedStay;
    }

    public void setPlayerForcedStay(boolean stay) {
        if (playerForcedStay == stay) {
            return;
        }
        playerForcedStay = stay;
        markDirty();
        syncToClients();
    }

    public void setAggressive(boolean value) {
        boolean next = value && Config.aggressiveModeAllowed;
        if (aggressive == next) {
            if (!Config.aggressiveModeAllowed && aggressive) {
                aggressive = false;
                markDirty();
                syncToClients();
            }
            return;
        }
        aggressive = next;
        markDirty();
        syncToClients();
    }

    public void toggleAggressive(EntityPlayer player) {
        if (!Config.aggressiveModeAllowed) {
            if (aggressive) {
                aggressive = false;
                markDirty();
                syncToClients();
            }
            if (player != null && !worldObj.isRemote) {
                player.addChatMessage(new ChatComponentText("Aggressive mode disabled in config."));
            }
            return;
        }
        aggressive = !aggressive;
        markDirty();
        syncToClients();
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
    public boolean isForceStayActive(EntityRobolCraft worker) {
        if (isRedstoneForcedStay()) {
            return true;
        }
        return worker != null && worker.isPlayerForcedStay();
    }

    /**
     * Shift-RC on locker: toggle assigned worker forced-stay and notify player.
     * 
     * @return true if a living worker was toggled
     */
    public boolean toggleWorkerForcedStay(EntityPlayer player) {
        EntityRobolCraft worker = findWorker();
        if (worker == null || worker.isDead) {
            if (player != null && worldObj != null && !worldObj.isRemote) {
                player.addChatMessage(new ChatComponentText("No worker assigned to toggle stay."));
            }
            return false;
        }
        worker.toggleForcedStayAtLocker();
        // Mirror onto TE + always sync (like aggressive) so client Stay label updates
        playerForcedStay = worker.isPlayerForcedStay();
        markDirty();
        syncToClients();
        onWorkerStayOrModeMaybeChanged();
        if (player != null && worldObj != null && !worldObj.isRemote) {
            String msg = worker.isPlayerForcedStay() ? "Worker will stay at locker." : "Worker resumed duties.";
            player.addChatMessage(new ChatComponentText(msg));
        }
        return true;
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
        this.bedSlot = null; // bed is in the world
        markDirty();
        syncToClients();
    }

    public void onLinkedBedRemoved() {
        this.hasBedPos = false;
        this.bedOwed = true;
        this.bedExistsCacheTicks = 0;
        markDirty();
        syncToClients();
        // v19: broken bed item goes into GUI slot (not floor)
        putLinkedBedIntoSlot();
    }

    public boolean isWaitForMorningAfterDeath() {
        return waitForMorningAfterDeath;
    }

    public void clearWaitForMorningAfterDeath() {
        waitForMorningAfterDeath = false;
        markDirty();
        syncToClients();
    }

    /** Called when stay toggle / mode may affect clients. */
    public void onWorkerStayOrModeMaybeChanged() {
        // no redstone emit
    }

    public void killAssignedWorker() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        EntityRobolCraft worker = findWorker();
        if (worker != null && !worker.isDead) {
            worker.setDeadFromLockerDestroyed();
        }
        workerUUID = null;
        workerEntityId = -1;
        pendingRespawn = false;
        legacyWorkerStored = false;
        // Remove linked bed in world
        removeLinkedBedInWorld();
        bedSlot = null;
        freeShortId();
        onLockerDestroyedExtra();
        markDirty();
    }

    protected void freeShortId() {
        if (shortId < 1 || worldObj == null || worldObj.isRemote) {
            return;
        }
        ShortIdRegistry reg = ShortIdRegistry.get(worldObj);
        if (reg != null) {
            if (isShortIdSupervisor()) {
                reg.freeSupervisor(shortId);
            } else {
                reg.freeWorker(shortId);
            }
        }
        com.angelika.robolcraft.npc.NpcWorldFile
            .removeId(worldObj, isShortIdSupervisor() ? "supervisor" : "worker", shortId);
        shortId = -1;
    }

    /** Hook for supervisor report-memory clear. */
    protected void onLockerDestroyedExtra() {}

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
    public void onWorkerDied(EntityRobolCraft deadWorker) {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        boolean afterworkDeath = deadWorker != null && deadWorker.getOutfit() != EntityRobolCraft.OUTFIT_WORK;
        pendingRespawn = true;
        respawnCooldown = RESPAWN_DELAY_TICKS;
        // v27: clear UUID immediately so pendingRespawn reclaim cannot re-bind the
        // dying entity (isDead may still be false in the same tick as onDeath).
        workerUUID = null;
        workerEntityId = -1;
        workerMissingTicks = 0;
        if (afterworkDeath) {
            waitForMorningAfterDeath = true;
        }
        markDirty();
        syncToClients();
        RobolCraftMod.LOG
            .info("Scheduled respawn at locker ({}, {}, {}) afterworkDeath={}", xCoord, yCoord, zCoord, afterworkDeath);
    }

    /** Legacy no-arg for any leftover callers. */
    public void onWorkerDied() {
        onWorkerDied(null);
    }

    public void bindWorker(EntityRobolCraft worker) {
        if (worker == null) {
            return;
        }
        workerUUID = worker.getUniqueID();
        workerEntityId = worker.getEntityId();
        pendingRespawn = false;
        legacyWorkerStored = false;
        worker.setHomeLocker(xCoord, yCoord, zCoord, worldObj.provider.dimensionId);
        // Reconcile Stay: prefer worker NBT if already on; else push TE → worker
        if (worker.isPlayerForcedStay() && !playerForcedStay) {
            playerForcedStay = true;
        } else {
            worker.setForcedStayAtLocker(playerForcedStay);
        }
        markDirty();
        syncToClients();
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
     * day_start 75%.
     */
    public void playMorningClothesChangeSounds() {
        playChangingClothesAtLocker();
        playLockerSoundAtLocker();
        maybePlayDayStartAtLocker();
    }

    private void playChangingClothesAtLocker() {
        playRandomFrom(ModSounds.clipsFor(findWorker(), ModSounds.CAT_CHANGING_CLOTHES), 1.0F);
    }

    private void playLockerSoundAtLocker() {
        playRandomFrom(ModSounds.clipsFor(findWorker(), ModSounds.CAT_LOCKER_SOUND), 1.0F);
    }

    private void maybePlayWorkExitAtLocker() {
        List<String> list = ModSounds.clipsFor(findWorker(), ModSounds.CAT_WORK_EXIT);
        if (list == null || list.isEmpty()) {
            return;
        }
        if (worldObj.rand.nextFloat() >= WORK_EXIT_CHANCE) {
            return;
        }
        playSoundAtLocker(list.get(worldObj.rand.nextInt(list.size())), worldObj.rand, 1.0F);
    }

    private void maybePlayDayStartAtLocker() {
        List<String> list = ModSounds.clipsFor(findWorker(), ModSounds.CAT_DAY_START);
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
        float vol = Math.max(0.0F, Config.getBroadcastSoundVolume()) * volumeMul;
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
            syncToClients();
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
            EntityRobolCraft w = spawnWorker(false, EntityRobolCraft.OUTFIT_AFTERWORK);
            if (w != null && WorkerSchedule.isLocker(w)) {
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
            // Reclaim if the “dead” worker is already loaded again (chunk reload race)
            EntityRobolCraft existing = findWorker();
            if (existing == null) {
                existing = findLoadedWorkerAtThisLocker();
            }
            if (existing != null && existing.getHealth() > 0.0F && !existing.isDead) {
                bindWorker(existing);
                workerMissingTicks = 0;
                pendingRespawn = false;
                markDirty();
            } else if (respawnCooldown > 0) {
                respawnCooldown--;
            } else {
                byte outfit = waitForMorningAfterDeath ? EntityRobolCraft.OUTFIT_AFTERWORK
                    : EntityRobolCraft.OUTFIT_WORK;
                EntityRobolCraft w = spawnWorker(false, outfit);
                pendingRespawn = false;
                workerMissingTicks = 0;
                if (w != null && waitForMorningAfterDeath) {
                    w.setWaitingForMorningAfterDeath(true);
                }
                markDirty();
            }
        } else if (workerUUID != null) {
            // v25: do NOT treat chunk-unload as death (was causing duplicate spawns).
            // Only onWorkerDied sets pendingRespawn. Refresh entity-id cache when present.
            EntityRobolCraft w = findWorker();
            if (w != null) {
                workerMissingTicks = 0;
            } else {
                workerMissingTicks++;
                // Safety net only: missing from ALL loaded entities for 10 minutes
                if (workerMissingTicks > 12000) {
                    pendingRespawn = true;
                    respawnCooldown = RESPAWN_DELAY_TICKS;
                    workerMissingTicks = 0;
                    markDirty();
                }
            }
        }
    }

    private void tickBedOwnership() {
        if (hasBedPos) {
            if (!verifyBedStillThere()) {
                hasBedPos = false;
                bedOwed = true;
                bedExistsCacheTicks = 0;
                markDirty();
                syncToClients();
            } else {
                bedOwed = false;
                return;
            }
        }
        // Slot occupied — uniqueness satisfied; no world/player AABB scan
        if (bedSlot != null) {
            if (bedOwed) {
                bedOwed = false;
                markDirty();
                syncToClients();
            }
            return;
        }
        // v25: while a recent positive “exists elsewhere” cache is warm, skip AABB
        if (bedExistsCacheTicks > 0) {
            bedExistsCacheTicks--;
            return;
        }
        // Only scan invent/items when we might recreate (owed) or need to clear owed
        if (!bedOwed) {
            return;
        }
        if (linkedBedExistsElsewhere()) {
            bedExistsCacheTicks = 5; // 5 ownership pulses (~200 ticks) while bed known elsewhere
            bedOwed = false;
            markDirty();
            syncToClients();
            return;
        }
        // Truly missing — recreate the single linked bed into the GUI slot
        putLinkedBedIntoSlot();
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
            // Mouse-cursor stack (GUI pick-up) — missing this caused bed-slot dupes
            ItemStack cursor = p.inventory.getItemStack();
            if (isOwnedBedStack(cursor)) {
                return true;
            }
            for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
                if (isOwnedBedStack(p.inventory.getStackInSlot(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOwnedBedStack(ItemStack s) {
        return s != null && s.getItem() == CommonProxy.itemWorkerBed
            && lockerId != null
            && lockerId.equals(LockerLink.readIdFromStack(s));
    }

    /** True if linked bed exists outside this TE slot (world / item entity / player+cursor). */
    private boolean linkedBedExistsElsewhere() {
        return worldHasLinkedBedBlock() || worldHasLinkedBedItemEntity() || playerHasLinkedBedItem();
    }

    private void giveOrDropBedItem(EntityPlayer player) {
        // v19: owed bed goes into GUI slot (not floor / not player inventory)
        putLinkedBedIntoSlot();
    }

    private void dropBedItemAtLocker() {
        // v19: replacement bed goes into GUI slot (not floor)
        putLinkedBedIntoSlot();
    }

    /** Create linked bed item and place it into the locker GUI bed slot. */
    public void putLinkedBedIntoSlot() {
        if (lockerId == null || worldObj == null || worldObj.isRemote) {
            return;
        }
        if (bedSlot != null) {
            return;
        }
        ensureShortId();
        bedSlot = createLinkedBedStack();
        bedOwed = false;
        markDirty();
        syncToClients();
        RobolCraftMod.LOG
            .info("Bed item placed in locker GUI slot ({}, {}, {}) {}", xCoord, yCoord, zCoord, getShortLabel());
    }

    public ItemStack createLinkedBedStack() {
        ItemStack bed = ItemWorkerBed.createLinked(lockerId, xCoord, yCoord, zCoord, worldObj.provider.dimensionId);
        if (bed.stackTagCompound == null) {
            bed.stackTagCompound = new NBTTagCompound();
        }
        int kind = isShortIdSupervisor() ? LockerLink.KIND_SUPERVISOR : LockerLink.KIND_WORKER;
        LockerLink.writeShortId(bed.stackTagCompound, kind, shortId);
        return bed;
    }

    public boolean isLinkedBedItem(ItemStack stack) {
        if (stack == null || stack.getItem() != CommonProxy.itemWorkerBed || lockerId == null) {
            return false;
        }
        UUID id = LockerLink.readIdFromStack(stack);
        return lockerId.equals(id);
    }

    /**
     * GUI "Reset bed": if linked bed is placed in world, break it and put the linked bed
     * ITEM into the GUI slot (no floor drop, no player inv).
     */
    public void resetBedIntoSlot(EntityPlayer player) {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (hasBedPos && verifyBedStillThere()) {
            int bx = bedX, by = bedY, bz = bedZ;
            int meta = worldObj.getBlockMetadata(bx, by, bz);
            int[] head = BlockWorkerBed.headCoords(bx, by, bz, meta);
            hasBedPos = false;
            bedOwed = true;
            worldObj.setBlockToAir(bx, by, bz);
            if (worldObj.getBlock(head[0], head[1], head[2]) == CommonProxy.blockWorkerBed) {
                worldObj.setBlockToAir(head[0], head[1], head[2]);
            }
        }
        putLinkedBedIntoSlot();
        if (player != null) {
            player.addChatMessage(new ChatComponentText("Bed reset into locker slot."));
        }
    }

    private void dropStackAtLocker(ItemStack stack) {
        EntityItem ei = new EntityItem(worldObj, xCoord + 0.5D, yCoord + 1.0D, zCoord + 0.5D, stack);
        ei.delayBeforeCanPickup = 10;
        worldObj.spawnEntityInWorld(ei);
    }

    /** Day work leash; {@code 0} = unlimited. Lazy-init from Config for old lockers. */
    public int getMaxWorkDistance() {
        if (maxWorkDistance < 0) {
            maxWorkDistance = clampMaxWorkDistance(Config.maxDistanceFromLocker);
        }
        return maxWorkDistance;
    }

    public void setMaxWorkDistance(int dist) {
        int clamped = clampMaxWorkDistance(dist);
        if (maxWorkDistance != clamped) {
            maxWorkDistance = clamped;
            markDirty();
            syncToClients();
        }
    }

    public static int clampMaxWorkDistance(int dist) {
        if (dist < 0) {
            return 0;
        }
        if (dist > MAX_WORK_DISTANCE_CAP) {
            return MAX_WORK_DISTANCE_CAP;
        }
        return dist;
    }

    /** Send current locker state to all tracking clients. */
    public void syncToClients() {
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            worldObj.markBlockForUpdate(xCoord, yCoord + 1, zCoord);
        }
    }

    /**
     * Worker this locker spawns. Addon lockers subclass this tile and override this method.
     * Keep the signature. The supervisor locker is the in-mod example.
     */
    protected EntityRobolCraft createWorkerEntity() {
        return new EntityRobolCraft(worldObj);
    }

    protected EntityRobolCraft spawnWorker(boolean unusedDayStart, byte outfit) {
        if (worldObj == null || worldObj.isRemote) {
            return null;
        }
        // v25: never double-spawn if a worker for this locker is already loaded
        EntityRobolCraft existing = findWorker();
        if (existing == null) {
            existing = findLoadedWorkerAtThisLocker();
        }
        if (existing != null && !existing.isDead && existing.getHealth() > 0.0F) {
            bindWorker(existing);
            existing.setOutfit(outfit);
            return existing;
        }
        EntityRobolCraft worker = createWorkerEntity();
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
        RobolCraftMod.LOG.info(
            "Spawned {} at locker ({}, {}, {}) dim={} outfit={}",
            worker.getClass()
                .getSimpleName(),
            xCoord,
            yCoord,
            zCoord,
            worldObj.provider.dimensionId,
            outfit);
        return worker;
    }

    @SuppressWarnings("unchecked")
    protected EntityRobolCraft findWorker() {
        if (workerUUID == null || worldObj == null) {
            return null;
        }
        if (workerEntityId >= 0) {
            Entity e = worldObj.getEntityByID(workerEntityId);
            if (e instanceof EntityRobolCraft && !e.isDead && workerUUID.equals(e.getUniqueID())) {
                return (EntityRobolCraft) e;
            }
            workerEntityId = -1;
        }
        // v25: scan all loaded entities (not 64-AABB) — workers roam to scanRadius 100+
        for (Object o : worldObj.loadedEntityList) {
            if (!(o instanceof EntityRobolCraft)) {
                continue;
            }
            EntityRobolCraft w = (EntityRobolCraft) o;
            if (!w.isDead && workerUUID.equals(w.getUniqueID())) {
                workerEntityId = w.getEntityId();
                return w;
            }
        }
        return null;
    }

    /** Any living worker already bound to this locker block (prevents duplicate spawn). */
    @SuppressWarnings("unchecked")
    private EntityRobolCraft findLoadedWorkerAtThisLocker() {
        if (worldObj == null) {
            return null;
        }
        for (Object o : worldObj.loadedEntityList) {
            if (!(o instanceof EntityRobolCraft)) {
                continue;
            }
            EntityRobolCraft w = (EntityRobolCraft) o;
            if (w.isDead || w.getHealth() <= 0.0F || !w.hasHomeLocker()) {
                continue;
            }
            if (w.getHomeDim() != worldObj.provider.dimensionId) {
                continue;
            }
            if (w.getHomeX() == xCoord && w.getHomeY() == yCoord && w.getHomeZ() == zCoord) {
                return w;
            }
            // Bottom TE vs standing on top half
            if (w.getHomeX() == xCoord && w.getHomeZ() == zCoord
                && (w.getHomeY() == yCoord || w.getHomeY() == yCoord + 1 || w.getHomeY() == yCoord - 1)) {
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
        tag.setBoolean("PlayerForcedStay", playerForcedStay);
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
        tag.setInteger("ShortId", shortId);
        tag.setBoolean("ShortIdSupervisor", shortIdIsSupervisor);
        if (bedSlot != null) {
            NBTTagCompound bedTag = new NBTTagCompound();
            bedSlot.writeToNBT(bedTag);
            tag.setTag("BedSlot", bedTag);
        }
        tag.setInteger("MaxWorkDistance", getMaxWorkDistance());

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
        if (tag.hasKey("PlayerForcedStay")) {
            playerForcedStay = tag.getBoolean("PlayerForcedStay");
        }

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
        if (tag.hasKey("ShortId")) {
            shortId = tag.getInteger("ShortId");
            shortIdIsSupervisor = tag.getBoolean("ShortIdSupervisor");
            if (shortId >= 1 && worldObj != null && !worldObj.isRemote) {
                com.angelika.robolcraft.npc.NpcWorldFile
                    .ensureId(worldObj, shortIdIsSupervisor ? "supervisor" : "worker", shortId);
            }
        }
        if (tag.hasKey("BedSlot")) {
            bedSlot = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("BedSlot"));
        } else {
            bedSlot = null;
        }
        if (tag.hasKey("MaxWorkDistance")) {
            maxWorkDistance = clampMaxWorkDistance(tag.getInteger("MaxWorkDistance"));
        } else {
            maxWorkDistance = -1; // migrate: use Config default on first get
        }

    }

    // --- IInventory: single linked-bed slot ---

    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? bedSlot : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        if (slot != 0 || bedSlot == null) {
            return null;
        }
        ItemStack out;
        if (bedSlot.stackSize <= count) {
            out = bedSlot;
            bedSlot = null;
        } else {
            out = bedSlot.splitStack(count);
            if (bedSlot.stackSize <= 0) {
                bedSlot = null;
            }
        }
        // v21: mark owed so a true loss can replace later, but tickBedOwnership will
        // NOT recreate while the unique bed is on the cursor, in a player inv, as an
        // item entity, or placed in the world (see linkedBedExistsElsewhere).
        if (bedSlot == null) {
            bedOwed = true;
        }
        markDirty();
        syncToClients();
        return out;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot != 0 || bedSlot == null) {
            return null;
        }
        ItemStack s = bedSlot;
        bedSlot = null;
        markDirty();
        syncToClients();
        return s;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot != 0) {
            return;
        }
        if (stack != null && !isLinkedBedItem(stack)) {
            return;
        }
        bedSlot = stack;
        if (bedSlot != null) {
            bedOwed = false;
        } else if (!linkedBedExistsElsewhere()) {
            // Slot emptied and bed not in player/world — owe a replacement
            bedOwed = true;
        }
        markDirty();
        syncToClients();
    }

    @Override
    public String getInventoryName() {
        return getShortLabel();
    }

    @Override
    public boolean hasCustomInventoryName() {
        return shortId >= 1;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this
            && player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot == 0 && isLinkedBedItem(stack);
    }
}
