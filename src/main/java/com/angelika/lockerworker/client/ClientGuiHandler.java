package com.angelika.lockerworker.client;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.angelika.lockerworker.client.gui.GuiLocker;
import com.angelika.lockerworker.client.gui.GuiSupervisorLocker;
import com.angelika.lockerworker.inventory.GuiHandler;
import com.angelika.lockerworker.tileentity.TileEntityLocker;
import com.angelika.lockerworker.tileentity.TileEntitySupervisorLocker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client-side GUI half; keeps client GUI classes off the dedicated-server path. */
@SideOnly(Side.CLIENT)
public final class ClientGuiHandler extends GuiHandler {

    @Override
    @SideOnly(Side.CLIENT)
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (id == GUI_WORKER_LOCKER && te instanceof TileEntityLocker && !(te instanceof TileEntitySupervisorLocker)) {
            return new GuiLocker(player.inventory, (TileEntityLocker) te);
        }
        if (id == GUI_SUPERVISOR_LOCKER && te instanceof TileEntitySupervisorLocker) {
            return new GuiSupervisorLocker(player.inventory, (TileEntitySupervisorLocker) te);
        }
        return null;
    }
}
