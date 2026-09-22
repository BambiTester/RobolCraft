package com.angelika.lockerworker.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.angelika.lockerworker.LockerWorkerMod;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Craftable break-time hangout block. Icons: Martyna trashcan_front / side / top /
 * bottom under {@code assets/lockerworker/textures/blocks/}.
 */
public class BlockTrashcan extends Block {

    @SideOnly(Side.CLIENT)
    private IIcon iconFront;
    @SideOnly(Side.CLIENT)
    private IIcon iconSide;
    @SideOnly(Side.CLIENT)
    private IIcon iconTop;
    @SideOnly(Side.CLIENT)
    private IIcon iconBottom;

    public BlockTrashcan() {
        super(Material.iron);
        setHardness(1.5F);
        setResistance(8.0F);
        setStepSound(soundTypeMetal);
        setBlockName("trashcan");
        setBlockTextureName(LockerWorkerMod.MODID + ":trashcan_side");
        setCreativeTab(CreativeTabs.tabDecorations);
        // Trash-can footprint: inset slightly from full cube
        setBlockBounds(0.125F, 0.0F, 0.125F, 0.875F, 0.875F, 0.875F);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        String mod = LockerWorkerMod.MODID;
        iconFront = reg.registerIcon(mod + ":trashcan_front");
        iconSide = reg.registerIcon(mod + ":trashcan_side");
        iconTop = reg.registerIcon(mod + ":trashcan_top");
        iconBottom = reg.registerIcon(mod + ":trashcan_bottom");
        blockIcon = iconSide;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (side == 0) {
            return iconBottom != null ? iconBottom : blockIcon;
        }
        if (side == 1) {
            return iconTop != null ? iconTop : blockIcon;
        }
        // South (3) = front face for inventory / default
        if (side == 3) {
            return iconFront != null ? iconFront : blockIcon;
        }
        return iconSide != null ? iconSide : blockIcon;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        setBlockBounds(0.125F, 0.0F, 0.125F, 0.875F, 0.875F, 0.875F);
    }

    @Override
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List list,
        Entity entity) {
        setBlockBoundsBasedOnState(world, x, y, z);
        super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return AxisAlignedBB.getBoundingBox(x + 0.125D, y, z + 0.125D, x + 0.875D, y + 0.875D, z + 0.875D);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        return getCollisionBoundingBoxFromPool(world, x, y, z);
    }
}
