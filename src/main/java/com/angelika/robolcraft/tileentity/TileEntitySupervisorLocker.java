package com.angelika.robolcraft.tileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;

import com.angelika.robolcraft.Config;
import com.angelika.robolcraft.entity.EntityRobolCraft;
import com.angelika.robolcraft.entity.EntityShiftSupervisor;
import com.angelika.robolcraft.util.SupervisorReportMemory;

/**
 * Supervisor locker TE — same bed/UUID/force-stay cascade as worker locker,
 * spawns {@link EntityShiftSupervisor}. Report memory lives here (survives death).
 */
public class TileEntitySupervisorLocker extends TileEntityLocker {

    private final SupervisorReportMemory reportMemory = new SupervisorReportMemory();

    @Override
    protected EntityRobolCraft createWorkerEntity() {
        return new EntityShiftSupervisor(worldObj);
    }

    @Override
    protected boolean isSupervisorLocker() {
        return true;
    }

    public SupervisorReportMemory getReportMemory() {
        return reportMemory;
    }

    @Override
    protected void onLockerDestroyedExtra() {
        // Memory clears ONLY when this supervisor locker is destroyed
        reportMemory.clear();
    }

    @Override
    public void toggleAggressive(EntityPlayer player) {
        if (!Config.aggressiveModeAllowed) {
            if (isAggressiveRaw()) {
                setAggressive(false);
            }
            if (player != null && worldObj != null && !worldObj.isRemote) {
                player.addChatMessage(new ChatComponentText("Aggressive mode disabled in config."));
            }
            return;
        }
        setAggressive(!isAggressiveRaw());
        if (player != null && worldObj != null && !worldObj.isRemote) {
            player.addChatMessage(
                new ChatComponentText(
                    isAggressiveRaw() ? "Supervisor locker: AGGRESSIVE" : "Supervisor locker: peaceful"));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTTagCompound mem = new NBTTagCompound();
        reportMemory.writeToNBT(mem);
        tag.setTag("SupervisorMemory", mem);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("SupervisorMemory")) {
            reportMemory.readFromNBT(tag.getCompoundTag("SupervisorMemory"));
        }
    }
}
