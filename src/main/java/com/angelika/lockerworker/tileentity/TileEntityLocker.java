package com.angelika.lockerworker.tileentity;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import com.angelika.lockerworker.LockerWorkerMod;
import com.angelika.lockerworker.entity.EntityLockerWorker;

/**
 * Stores assigned worker UUID / entity id for respawn binding.
 * Lives on the BOTTOM half of the locker only.
 */
public class TileEntityLocker extends TileEntity {

    private UUID workerUUID;
    private int workerEntityId = -1;
    private boolean pendingRespawn;
    private int respawnCooldown;

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
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (workerUUID != null) {
            tag.setLong("WorkerUUIDMost", workerUUID.getMostSignificantBits());
            tag.setLong("WorkerUUIDLeast", workerUUID.getLeastSignificantBits());
        }
        tag.setInteger("WorkerEntityId", workerEntityId);
        tag.setBoolean("PendingRespawn", pendingRespawn);
        tag.setInteger("RespawnCooldown", respawnCooldown);
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
    }
}
