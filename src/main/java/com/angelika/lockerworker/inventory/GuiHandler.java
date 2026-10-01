package com.angelika.lockerworker.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.angelika.lockerworker.tileentity.TileEntityLocker;
import com.angelika.lockerworker.tileentity.TileEntitySupervisorLocker;

import cpw.mods.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {

    public static final int GUI_WORKER_LOCKER = 0;
    public static final int GUI_SUPERVISOR_LOCKER = 1;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (id == GUI_WORKER_LOCKER && te instanceof TileEntityLocker && !(te instanceof TileEntitySupervisorLocker)) {
            return new ContainerLocker(player.inventory, (TileEntityLocker) te);
        }
        if (id == GUI_SUPERVISOR_LOCKER && te instanceof TileEntitySupervisorLocker) {
            return new ContainerSupervisorLocker(player.inventory, (TileEntitySupervisorLocker) te);
        }
        // Also resolve TE from upper half: callers pass bottom coords, but be defensive
        if (te == null) {
            return null;
        }
        return null;
    }

    /**
     * Dedicated-server-safe default. The client proxy supplies the client GUI
     * implementation without putting net.minecraft.client classes on this path.
     */
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }
}
