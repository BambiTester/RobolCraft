package com.angelika.lockerworker.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import com.angelika.lockerworker.inventory.ContainerSupervisorLocker;
import com.angelika.lockerworker.tileentity.TileEntitySupervisorLocker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Supervisor locker GUI — worker layout plus Report as the third left button.
 * All three buttons share the compact v21 metrics so Report stays above inventory.
 */
@SideOnly(Side.CLIENT)
public class GuiSupervisorLocker extends GuiLocker {

    private GuiButton btnReport;

    public GuiSupervisorLocker(InventoryPlayer inv, TileEntitySupervisorLocker locker) {
        super(inv, locker);
        this.inventorySlots = new ContainerSupervisorLocker(inv, locker);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        int gx = (this.width - this.xSize) / 2;
        int gy = (this.height - this.ySize) / 2;
        int yReport = gy + TOP_BTN_Y + 2 * (BTN_H + BTN_GAP);
        btnReport = new GuiLockerButton(3, gx + LEFT_BTN_X, yReport, LEFT_BTN_W, BTN_H, "Report");
        this.buttonList.add(btnReport);
    }
}
