package com.angelika.lockerworker.tileentity;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.LockerWorkerMod;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.entity.ai.EntityAIReturnToLocker;
import com.angelika.lockerworker.sound.ModSounds;
import com.angelika.lockerworker.util.WorkerSchedule;

/**
 * Stores assigned worker UUID / entity id for respawn binding.
 * Lives on the BOTTOM half of the locker only.
 *
 * <p>
 * v7: overnight the worker <b>enters</b> the locker ({@link #workerStored}) —
 * entity despawned, redstone waiting = 15 while stored. Released on leave-LOCKER
 * into WORK (day start) with optional {@code day_start} sound.
 */
public class TileEntityLocker extends TileEntity {

    private UUID workerUUID;
    private int workerEntityId = -1;
    private boolean pendingRespawn;
    private int respawnCooldown;

    /** Aggressive = worker attacks hostiles; peaceful = passive AI only. */
    private boolean aggressive;

    /**
     * Worker is inside the locker overnight (entity despawned). Distinct from
     * death pending-respawn — do not auto-respawn until schedule leaves LOCKER.
     */
    private boolean workerStored;

    private boolean lastWaitingPower;

    /**
     * Consecutive ticks the bound living worker has been within enter range during
     * LOCKER — failsafe if return AI is stuck / TE resolve race.
     */
    private int nearbyEnterTicks;

    /** Ticks to wait after worker death before respawning. */
    public static final int RESPAWN_DELAY_TICKS = 100;

    private static final float WORK_EXIT_CHANCE = 0.75F;
    private static final float DAY_START_CHANCE = 0.25F;

    public void onPlacedBy(EntityLivingBase placer) {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        // Night place: mark stored empty; first release on day start spawns the worker
        if (WorkerSchedule.isLocker(worldObj)) {
            workerStored = true;
            workerUUID = null;
            workerEntityId = -1;
            pendingRespawn = false;
            markDirty();
            updateRedstoneNeighborsIfNeeded(true);
            return;
        }
        spawnWorker(false);
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

    public boolean isWorkerStored() {
        return workerStored;
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
     * Waiting signal for redstone (strength 15): worker stored overnight in locker,
     * OR (legacy) living worker standing at locker for forced-stay outside LOCKER.
     */
    public boolean isWorkerWaiting() {
        if (workerStored) {
            return true;
        }
        EntityLockerWorker worker = findWorker();
        return worker != null && !worker.isDead && worker.isWaitingAtLocker();
    }

    /** Called when stay toggle / mode may affect redstone or clients. */
    public void onWorkerStayOrModeMaybeChanged() {
        updateRedstoneNeighborsIfNeeded(true);
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
        workerStored = false;
        markDirty();
        updateRedstoneNeighborsIfNeeded(true);
    }

    /** Called when the bound worker dies (not from locker destroy / night enter). */
    public void onWorkerDied() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (workerStored) {
            // Should not happen — stored workers are already despawned
            return;
        }
        pendingRespawn = true;
        respawnCooldown = RESPAWN_DELAY_TICKS;
        workerEntityId = -1;
        markDirty();
        updateRedstoneNeighborsIfNeeded(true);
    }

    /**
     * Night enter: despawn worker into locker. 75% {@code work_exit} at locker block.
     * Does not schedule death-respawn.
     */
    public void storeWorkerOvernight(EntityLockerWorker worker) {
        if (worldObj == null || worldObj.isRemote || worker == null) {
            return;
        }
        if (workerStored) {
            return;
        }
        workerUUID = worker.getUniqueID();
        workerEntityId = -1;
        pendingRespawn = false;
        workerStored = true;
        markDirty();

        maybePlayWorkExitAtLocker();

        worker.setDeadFromEnteringLocker();
        updateRedstoneNeighborsIfNeeded(true);
        LockerWorkerMod.LOG.debug("Worker entered locker overnight at ({}, {}, {})", xCoord, yCoord, zCoord);
    }

    private void maybePlayWorkExitAtLocker() {
        List<String> list = ModSounds.workExit();
        if (list == null || list.isEmpty()) {
            return;
        }
        Random rand = worldObj.rand;
        if (rand.nextFloat() >= WORK_EXIT_CHANCE) {
            return;
        }
        String name = list.get(rand.nextInt(list.size()));
        // Strip "lockerworker:" prefix — playSoundEffect wants domain:path style name
        float vol = Math.max(0.0F, Config.soundVolume);
        if (vol <= 0.0F) {
            return;
        }
        float pitch = 0.95F + rand.nextFloat() * 0.1F;
        worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, name, vol, pitch);
    }

    public void bindWorker(EntityLockerWorker worker) {
        if (worker == null) {
            return;
        }
        workerUUID = worker.getUniqueID();
        workerEntityId = worker.getEntityId();
        pendingRespawn = false;
        workerStored = false;
        worker.setHomeLocker(xCoord, yCoord, zCoord, worldObj.provider.dimensionId);
        markDirty();
    }

    /** Ticks in enter range before TE force-stores (covers AI stuck / TE null race). */
    private static final int NEARBY_ENTER_FAILSAFE_TICKS = 20;

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

        // Overnight storage: release when schedule leaves LOCKER into WORK/BREAK
        if (workerStored) {
            nearbyEnterTicks = 0;
            if (!WorkerSchedule.isLocker(worldObj)) {
                boolean morningWork = WorkerSchedule.isWork(worldObj);
                releaseStoredWorker(morningWork);
            }
            updateRedstoneNeighborsIfNeeded(false);
            return;
        }

        // Failsafe: LOCKER + bound worker within enter range for a few ticks → store anyway
        if (WorkerSchedule.isLocker(worldObj)) {
            EntityLockerWorker living = findWorker();
            if (living != null && !living.isDead && isWorkerInEnterRange(living)) {
                nearbyEnterTicks++;
                if (nearbyEnterTicks >= NEARBY_ENTER_FAILSAFE_TICKS) {
                    nearbyEnterTicks = 0;
                    storeWorkerOvernight(living);
                    updateRedstoneNeighborsIfNeeded(false);
                    return;
                }
            } else {
                nearbyEnterTicks = 0;
            }
        } else {
            nearbyEnterTicks = 0;
        }

        if (pendingRespawn) {
            if (respawnCooldown > 0) {
                respawnCooldown--;
            } else {
                // Don't death-respawn during LOCKER — wait until day (store empty overnight)
                if (WorkerSchedule.isLocker(worldObj)) {
                    workerStored = true;
                    pendingRespawn = false;
                    markDirty();
                } else {
                    spawnWorker(false);
                    pendingRespawn = false;
                }
            }
        } else if (workerUUID != null && findWorker() == null) {
            // Missing unexpectedly (chunk unload etc.) — schedule respawn, not overnight store
            pendingRespawn = true;
            respawnCooldown = RESPAWN_DELAY_TICKS;
        }
        updateRedstoneNeighborsIfNeeded(false);
    }

    /** Same enter range as return AI (~1.5 blocks / Chebyshev ≤1). */
    private boolean isWorkerInEnterRange(EntityLockerWorker worker) {
        double lockerX = xCoord + 0.5;
        double lockerZ = zCoord + 0.5;
        double dx = worker.posX - lockerX;
        double dz = worker.posZ - lockerZ;
        if (dx * dx + dz * dz <= EntityAIReturnToLocker.ENTER_RANGE_SQ) {
            return true;
        }
        int bx = net.minecraft.util.MathHelper.floor_double(worker.posX);
        int bz = net.minecraft.util.MathHelper.floor_double(worker.posZ);
        int cheb = Math.max(Math.abs(bx - xCoord), Math.abs(bz - zCoord));
        return cheb <= 1;
    }

    /**
     * @param playDayStart if true (releasing into WORK), 25% day_start on the new worker
     */
    private void releaseStoredWorker(boolean playDayStart) {
        workerStored = false;
        pendingRespawn = false;
        EntityLockerWorker worker = spawnWorker(playDayStart);
        markDirty();
        updateRedstoneNeighborsIfNeeded(true);
        if (worker != null) {
            LockerWorkerMod.LOG.debug("Worker left locker for day at ({}, {}, {})", xCoord, yCoord, zCoord);
        }
    }

    private void updateRedstoneNeighborsIfNeeded(boolean force) {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        boolean waiting = isWorkerWaiting();
        if (force || waiting != lastWaitingPower) {
            lastWaitingPower = waiting;
            Block block = com.angelika.lockerworker.CommonProxy.blockLocker;
            if (block != null) {
                worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord, zCoord, block);
                worldObj.notifyBlocksOfNeighborChange(xCoord, yCoord + 1, zCoord, block);
            }
        }
    }

    private void syncToClient() {
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            worldObj.markBlockForUpdate(xCoord, yCoord + 1, zCoord);
        }
    }

    /**
     * @param triggerDayStart 25% day_start oneshot after spawn (morning release)
     * @return spawned worker or null
     */
    private EntityLockerWorker spawnWorker(boolean triggerDayStart) {
        if (worldObj == null || worldObj.isRemote) {
            return null;
        }
        EntityLockerWorker worker = new EntityLockerWorker(worldObj);
        int meta = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
        int facing = BlockLockerFacing(meta);
        double ox = 0.5;
        double oz = 0.5;
        switch (facing) {
            case 0:
                oz = 1.5;
                break; // south
            case 1:
                ox = -0.5;
                break; // west
            case 2:
                oz = -0.5;
                break; // north
            case 3:
                ox = 1.5;
                break; // east
            default:
                oz = 1.5;
                break;
        }
        worker.setLocationAndAngles(xCoord + ox, yCoord, zCoord + oz, 0.0F, 0.0F);
        worker.setHomeLocker(xCoord, yCoord, zCoord, worldObj.provider.dimensionId);
        worldObj.spawnEntityInWorld(worker);
        bindWorker(worker);
        if (triggerDayStart && worldObj.rand.nextFloat() < DAY_START_CHANCE) {
            worker.triggerOneshotSound(EntityLockerWorker.ONESHOT_DAY_START);
        }
        LockerWorkerMod.LOG.info(
            "Spawned LockerWorker at locker ({}, {}, {}) dim={}",
            xCoord,
            yCoord,
            zCoord,
            worldObj.provider.dimensionId);
        updateRedstoneNeighborsIfNeeded(true);
        return worker;
    }

    private static int BlockLockerFacing(int meta) {
        return meta & 0x3;
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
        tag.setBoolean("WorkerStored", workerStored);
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
        workerStored = tag.getBoolean("WorkerStored");
    }
}
