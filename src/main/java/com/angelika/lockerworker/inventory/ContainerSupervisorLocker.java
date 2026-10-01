package com.angelika.lockerworker.inventory;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import com.angelika.lockerworker.tileentity.TileEntitySupervisorLocker;
import com.angelika.lockerworker.util.SupervisorReportMemory;

/** Supervisor locker container — same bed slot; button 3 dumps report memory to chat. */
public class ContainerSupervisorLocker extends ContainerLocker {

    public ContainerSupervisorLocker(InventoryPlayer playerInv, TileEntitySupervisorLocker locker) {
        super(playerInv, locker);
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int button) {
        if (button == 3) {
            if (locker.getWorldObj() == null || locker.getWorldObj().isRemote || player == null) {
                return false;
            }
            if (!(locker instanceof TileEntitySupervisorLocker)) {
                return false;
            }
            TileEntitySupervisorLocker te = (TileEntitySupervisorLocker) locker;
            SupervisorReportMemory mem = te.getReportMemory();
            List<IChatComponent> lines = mem.formatAllChatComponents(locker.getWorldObj(), null);
            if (lines.isEmpty()) {
                player.addChatMessage(new ChatComponentText("No reports on file, boss."));
            } else {
                for (IChatComponent line : lines) {
                    player.addChatMessage(line);
                }
            }
            return true;
        }
        return super.enchantItem(player, button);
    }
}
