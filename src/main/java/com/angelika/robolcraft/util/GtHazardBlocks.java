package com.angelika.robolcraft.util;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * Detects GTNH footing that hurts workers: exposed cables / steam & fluid pipes
 * ({@code BaseMetaPipeEntity} and subclasses), without a compile-time GT import.
 */
public final class GtHazardBlocks {

    private GtHazardBlocks() {}

    /** True if the block cell is a GregTech pipe or cable tile. */
    public static boolean isPipeOrCable(World world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) {
            return false;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        return GregTechMachineLookup.isPipeTileEntity(te);
    }

    /**
     * True if standing at (x,y,z) (feet block coords) would put the worker on/in
     * exposed GT pipes or cables (feet cell or floor below).
     */
    public static boolean isHazardousFooting(World world, int x, int y, int z) {
        return isPipeOrCable(world, x, y, z) || isPipeOrCable(world, x, y - 1, z);
    }

    /** Entity-position helper (feet at floor). */
    public static boolean isHazardousAtEntity(World world, double posX, double posY, double posZ) {
        int x = MathHelper.floor_double(posX);
        int y = MathHelper.floor_double(posY);
        int z = MathHelper.floor_double(posZ);
        return isHazardousFooting(world, x, y, z);
    }
}
