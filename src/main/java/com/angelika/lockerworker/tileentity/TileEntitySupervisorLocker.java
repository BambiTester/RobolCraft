package com.angelika.lockerworker.tileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.entity.EntityShiftSupervisor;

/**
 * Supervisor locker TE — same bed/UUID/force-stay cascade as worker locker,
 * spawns {@link EntityShiftSupervisor}.
 */
public class TileEntitySupervisorLocker extends TileEntityLocker {

    @Override
    protected EntityLockerWorker createWorkerEntity() {
        return new EntityShiftSupervisor(worldObj);
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
}
