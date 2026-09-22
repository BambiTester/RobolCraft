package com.angelika.lockerworker.tileentity;

import java.util.List;
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

/**
 * Stores assigned worker UUID / entity id for respawn binding.
 * Lives on the BOTTOM half of the locker only.
 *
 * <p>
 * v3: aggressive mode (NBT + client sync for front texture), redstone waiting
 * via {@link #isWorkerWaiting()} — strength 15 when waiting, 0 otherwise
 * (see {@link com.angelika.lockerworker.block.BlockLocker}).
 */
public class TileEntityLocker extends TileEntity {

    private UUID workerUUID;
    private int workerEntityId = -1;
    private boolean pendingRespawn;
    private int respawnCooldown;

    /** Aggressive = worker attacks hostiles; peaceful = passive AI only. */
    private boolean aggressive;

    private boolean lastWaitingPower;

    /** Ticks to wait after worker death before respawning. */
    public static final int RESPAWN_DELAY_TICKS = 100;

    public void onPlacedBy(EntityLivingBase placer) {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        spawnWorker();
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

    public boolean isAggressive() {
        return aggressive && Config.aggressiveModeAllowed;
    }

    public boolean isAggressiveRaw() {
        return aggressive;
    }

    public void setAggressive(boolean value) {
        boolean next = value && Config.aggressiveModeAllowed;
        if (aggressive == next) {
            // Still clear if config forbids
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
     * Worker is “waiting at locker”: night standing OR forcedStay standing
     * (close to stand position). Used for redstone — strength 15 when true, 0 else.
     */
    public boolean isWorkerWaiting() {
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
        markDirty();
        updateRedstoneNeighborsIfNeeded(true);
    }

    /** Called when the bound worker dies (not from locker destroy). */
    public void onWorkerDied() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        pendingRespawn = true;
        respawnCooldown = RESPAWN_DELAY_TICKS;
        workerEntityId = -1;
        markDirty();
        updateRedstoneNeighborsIfNeeded(true);
    }

    public void bindWorker(EntityLockerWorker worker) {
        if (worker == null) {
            return;
        }
        workerUUID = worker.getUniqueID();
        workerEntityId = worker.getEntityId();
        pendingRespawn = false;
        worker.setHomeLocker(xCoord, yCoord, zCoord, worldObj.provider.dimensionId);
        markDirty();
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
        if (pendingRespawn) {
            if (respawnCooldown > 0) {
                respawnCooldown--;
            } else {
                spawnWorker();
                pendingRespawn = false;
            }
        } else if (workerUUID != null && findWorker() == null) {
            // Worker missing (unloaded / despawned unexpectedly) — schedule respawn
            pendingRespawn = true;
            respawnCooldown = RESPAWN_DELAY_TICKS;
        }
        updateRedstoneNeighborsIfNeeded(false);
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
        }
    }

    private void spawnWorker() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        EntityLockerWorker worker = new EntityLockerWorker(worldObj);
        // Stand in front of locker — offset by facing if available
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
        LockerWorkerMod.LOG.info(
            "Spawned LockerWorker at locker ({}, {}, {}) dim={}",
            xCoord,
            yCoord,
            zCoord,
            worldObj.provider.dimensionId);
        updateRedstoneNeighborsIfNeeded(true);
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
        // Fallback: search nearby loaded entities
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
    }
}
