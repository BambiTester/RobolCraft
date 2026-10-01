package com.angelika.lockerworker.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.angelika.lockerworker.CommonProxy;
import com.angelika.lockerworker.tileentity.TileEntityLocker;

public class ContainerLocker extends Container {

    public final TileEntityLocker locker;
    private final int bedSlotIndex;

    /**
     * Bed slot centered under the Reset-bed button (button at x=98 w=70 → center
     * 133; slot 18 wide → x=124). Y directly under the compact Reset button (y=34).
     */
    public static final int BED_SLOT_X = 124;
    public static final int BED_SLOT_Y = 34;

    public ContainerLocker(InventoryPlayer playerInv, TileEntityLocker locker) {
        this.locker = locker;
        this.addSlotToContainer(new Slot(locker, 0, BED_SLOT_X, BED_SLOT_Y) {

            @Override
            public boolean isItemValid(ItemStack stack) {
                return locker.isLinkedBedItem(stack);
            }

            @Override
            public int getSlotStackLimit() {
                return 1;
            }
        });
        bedSlotIndex = 0;

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return locker.isUseableByPlayer(player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack result = null;
        Slot slot = (Slot) this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            result = stack.copy();
            if (index == bedSlotIndex) {
                if (!this.mergeItemStack(stack, 1, this.inventorySlots.size(), true)) {
                    return null;
                }
            } else {
                if (!(stack.getItem() == CommonProxy.itemWorkerBed) || !locker.isLinkedBedItem(stack)) {
                    return null;
                }
                if (!this.mergeItemStack(stack, 0, 1, false)) {
                    return null;
                }
            }
            if (stack.stackSize == 0) {
                slot.putStack(null);
            } else {
                slot.onSlotChanged();
            }
        }
        return result;
    }

    /**
     * GUI buttons via enchant-packet abuse (vanilla 1.7.10 pattern).
     * 0 = toggle aggressive, 1 = toggle stay, 2 = reset bed.
     * Subclasses may handle higher ids (e.g. supervisor Report = 3).
     * Work-range uses {@link com.angelika.lockerworker.network.PacketSetWorkDistance}
     * (enchant button is one byte — cannot carry 10000+distance).
     */
    @Override
    public boolean enchantItem(EntityPlayer player, int button) {
        if (locker.getWorldObj() == null || locker.getWorldObj().isRemote) {
            return false;
        }
        if (button == 0) {
            locker.toggleAggressive(player);
            return true;
        }
        if (button == 1) {
            locker.toggleWorkerForcedStay(player);
            return true;
        }
        if (button == 2) {
            locker.resetBedIntoSlot(player);
            return true;
        }
        return false;
    }
}
