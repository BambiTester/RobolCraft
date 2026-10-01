package com.angelika.robolcraft.tileentity;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.angelika.robolcraft.util.LockerLink;

/**
 * Feet-half TE for {@link com.angelika.robolcraft.block.BlockWorkerBed}.
 * Stores durable locker UUID (+ optional locker coord hint).
 */
public class TileEntityWorkerBed extends TileEntity {

    private UUID lockerId;
    private int hintX;
    private int hintY;
    private int hintZ;
    private int hintDim;
    private boolean hasHint;

    public UUID getLockerId() {
        return lockerId;
    }

    public void setLockerId(UUID id) {
        this.lockerId = id;
        markDirty();
    }

    public boolean hasLockerHint() {
        return hasHint;
    }

    public int getHintX() {
        return hintX;
    }

    public int getHintY() {
        return hintY;
    }

    public int getHintZ() {
        return hintZ;
    }

    public int getHintDim() {
        return hintDim;
    }

    public void setLockerHint(int x, int y, int z, int dim) {
        this.hintX = x;
        this.hintY = y;
        this.hintZ = z;
        this.hintDim = dim;
        this.hasHint = true;
        markDirty();
    }

    /** Refresh hint from live locker if found. */
    public void linkToLocker(World world) {
        if (world == null || world.isRemote || lockerId == null) {
            return;
        }
        TileEntityLocker locker = null;
        if (hasHint && world.provider.dimensionId == hintDim) {
            TileEntity te = world.getTileEntity(hintX, hintY, hintZ);
            if (te instanceof TileEntityLocker && lockerId.equals(((TileEntityLocker) te).getLockerId())) {
                locker = (TileEntityLocker) te;
            }
        }
        if (locker == null) {
            locker = TileEntityLocker.findByLockerId(world, lockerId);
        }
        if (locker != null) {
            setLockerHint(locker.xCoord, locker.yCoord, locker.zCoord, world.provider.dimensionId);
        }
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
        if (lockerId != null) {
            LockerLink.writeId(tag, lockerId);
        }
        tag.setBoolean("HasHint", hasHint);
        if (hasHint) {
            LockerLink.writeHint(tag, hintX, hintY, hintZ, hintDim);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        lockerId = LockerLink.readId(tag);
        hasHint = tag.getBoolean("HasHint");
        if (hasHint && LockerLink.hasHint(tag)) {
            hintX = tag.getInteger(LockerLink.NBT_HINT_X);
            hintY = tag.getInteger(LockerLink.NBT_HINT_Y);
            hintZ = tag.getInteger(LockerLink.NBT_HINT_Z);
            hintDim = tag.getInteger(LockerLink.NBT_HINT_DIM);
        }
    }
}
