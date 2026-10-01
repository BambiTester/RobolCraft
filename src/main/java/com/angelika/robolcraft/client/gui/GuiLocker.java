package com.angelika.robolcraft.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.angelika.robolcraft.RobolCraftMod;
import com.angelika.robolcraft.inventory.ContainerLocker;
import com.angelika.robolcraft.network.PacketHandler;
import com.angelika.robolcraft.network.PacketSetWorkDistance;
import com.angelika.robolcraft.tileentity.TileEntityLocker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Worker locker GUI — custom background (no 3x3). Left: Stay / Aggressive.
 * Right: Reset bed + single bed slot + work-range field. Short ID on top-right.
 */
@SideOnly(Side.CLIENT)
public class GuiLocker extends GuiContainer {

    private static final ResourceLocation TEX = new ResourceLocation(RobolCraftMod.MODID, "textures/gui/locker.png");

    /** Left column (GUI-local); compact so 3 supervisor buttons stay above inventory. */
    protected static final int LEFT_BTN_X = 12;
    protected static final int LEFT_BTN_W = 62;
    /** Right Reset-bed button; bed slot centered under it (ContainerLocker.BED_SLOT_*). */
    protected static final int RIGHT_BTN_X = 102;
    protected static final int RIGHT_BTN_W = 62;
    /** First button Y — below title row, above player inventory (y=84). */
    protected static final int TOP_BTN_Y = 16;
    protected static final int BTN_H = 14;
    protected static final int BTN_GAP = 2;

    /** Work-range field under bed slot (slot y=34); room before player inv y=84. */
    protected static final int WORK_RANGE_LABEL_Y = 54;
    protected static final int WORK_RANGE_FIELD_X = 108;
    protected static final int WORK_RANGE_FIELD_Y = 64;
    protected static final int WORK_RANGE_FIELD_W = 48;
    protected static final int WORK_RANGE_FIELD_H = 12;

    protected final TileEntityLocker locker;
    protected GuiButton btnStay;
    protected GuiButton btnAggressive;
    protected GuiButton btnResetBed;
    protected GuiTextField workRangeField;
    private int lastSyncedWorkRange = Integer.MIN_VALUE;

    public GuiLocker(InventoryPlayer inv, TileEntityLocker locker) {
        super(new ContainerLocker(inv, locker));
        this.locker = locker;
        this.ySize = 166;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        int gx = (this.width - this.xSize) / 2;
        int gy = (this.height - this.ySize) / 2;
        this.buttonList.clear();
        int y0 = gy + TOP_BTN_Y;
        btnStay = new GuiLockerButton(1, gx + LEFT_BTN_X, y0, LEFT_BTN_W, BTN_H, stayLabel());
        btnAggressive = new GuiLockerButton(
            0,
            gx + LEFT_BTN_X,
            y0 + BTN_H + BTN_GAP,
            LEFT_BTN_W,
            BTN_H,
            aggressiveLabel());
        btnResetBed = new GuiLockerButton(2, gx + RIGHT_BTN_X, y0, RIGHT_BTN_W, BTN_H, "Reset bed");
        this.buttonList.add(btnStay);
        this.buttonList.add(btnAggressive);
        this.buttonList.add(btnResetBed);
        workRangeField = new GuiTextField(
            this.fontRendererObj,
            gx + WORK_RANGE_FIELD_X,
            gy + WORK_RANGE_FIELD_Y,
            WORK_RANGE_FIELD_W,
            WORK_RANGE_FIELD_H);
        workRangeField.setMaxStringLength(4);
        workRangeField.setEnableBackgroundDrawing(true);
        workRangeField.setTextColor(0xFFFFFF);
        syncWorkRangeFieldFromTE(true);
        refreshButtonLabels();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        commitWorkRange();
        super.onGuiClosed();
    }

    /** Same source as Aggressive: TE-synced field (not unsynced entity NBT). */
    private String aggressiveLabel() {
        return locker.isAggressiveRaw() ? "Aggressive" : "Passive";
    }

    private String stayLabel() {
        return locker.isPlayerForcedStay() ? "Stay: ON" : "Stay: OFF";
    }

    protected void refreshButtonLabels() {
        if (btnAggressive != null) {
            btnAggressive.displayString = aggressiveLabel();
        }
        if (btnStay != null) {
            btnStay.displayString = stayLabel();
        }
    }

    private void syncWorkRangeFieldFromTE(boolean force) {
        if (workRangeField == null) {
            return;
        }
        int v = locker.getMaxWorkDistance();
        if (force || v != lastSyncedWorkRange) {
            // Don't stomp while the player is typing
            if (force || !workRangeField.isFocused()) {
                workRangeField.setText(Integer.toString(v));
                lastSyncedWorkRange = v;
            }
        }
    }

    private void commitWorkRange() {
        if (workRangeField == null || this.mc == null || this.mc.playerController == null) {
            return;
        }
        String raw = workRangeField.getText()
            .trim();
        int value;
        try {
            value = raw.isEmpty() ? 0 : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            value = locker.getMaxWorkDistance();
            workRangeField.setText(Integer.toString(value));
            return;
        }
        value = TileEntityLocker.clampMaxWorkDistance(value);
        workRangeField.setText(Integer.toString(value));
        if (value != lastSyncedWorkRange) {
            lastSyncedWorkRange = value;
            // Real int packet — enchant-button is one byte and cannot carry 10000+distance
            PacketHandler.INSTANCE.sendToServer(new PacketSetWorkDistance(this.inventorySlots.windowId, value));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null) {
            return;
        }
        commitWorkRange();
        // Server toggles TE flag + syncToClients; updateScreen/initGui refresh from TE state
        this.mc.playerController.sendEnchantPacket(this.inventorySlots.windowId, button.id);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        refreshButtonLabels();
        syncWorkRangeFieldFromTE(false);
        if (workRangeField != null) {
            workRangeField.updateCursorCounter();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (workRangeField != null && workRangeField.textboxKeyTyped(typedChar, keyCode)) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                commitWorkRange();
                workRangeField.setFocused(false);
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (workRangeField != null) {
            boolean wasFocused = workRangeField.isFocused();
            workRangeField.mouseClicked(mouseX, mouseY, button);
            if (wasFocused && !workRangeField.isFocused()) {
                commitWorkRange();
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = locker.getShortLabel();
        int tw = this.fontRendererObj.getStringWidth(title);
        this.fontRendererObj.drawString(title, this.xSize - 8 - tw, 6, 0x404040);
        this.fontRendererObj.drawString("Work range", WORK_RANGE_FIELD_X - 2, WORK_RANGE_LABEL_Y, 0x404040);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GL11.glColor4f(1F, 1F, 1F, 1F);
        this.mc.getTextureManager()
            .bindTexture(TEX);
        int gx = (this.width - this.xSize) / 2;
        int gy = (this.height - this.ySize) / 2;
        this.drawTexturedModalRect(gx, gy, 0, 0, this.xSize, this.ySize);
        if (workRangeField != null) {
            workRangeField.drawTextBox();
        }
    }
}
