package com.angelika.lockerworker.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.angelika.lockerworker.LockerWorkerMod;
import com.angelika.lockerworker.util.TrashcanRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Craftable break-time hangout block. Icons: Martyna trashcan_front / side / top /
 * bottom under {@code assets/lockerworker/textures/blocks/}.
 * Metadata bits 0–1: horizontal facing (0=S, 1=W, 2=N, 3=E), front faces the placer.
 */
public class BlockTrashcan extends Block {

    public static final int META_FACING_MASK = 0x3;

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

    public static int getFacing(int meta) {
        return meta & META_FACING_MASK;
    }

    /** Front world side: 0=S(3), 1=W(4), 2=N(2), 3=E(5). */
    public static int getFrontSide(int facing) {
        switch (facing & META_FACING_MASK) {
            case 0:
                return 3;
            case 1:
                return 4;
            case 2:
                return 2;
            case 3:
                return 5;
            default:
                return 3;
        }
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
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        // Face TOWARD the player (same convention as lockers)
        int facing = MathHelper.floor_double((double) (placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        facing = (facing + 2) & META_FACING_MASK;
        world.setBlockMetadataWithNotify(x, y, z, facing, 2);
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
        // Inventory / default: south face shows front
        int front = getFrontSide(getFacing(meta));
        if (side == front) {
            return iconFront != null ? iconFront : blockIcon;
        }
        return iconSide != null ? iconSide : blockIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        return getIcon(side, world.getBlockMetadata(x, y, z));
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

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (!world.isRemote) {
            TrashcanRegistry reg = TrashcanRegistry.get(world);
            if (reg != null) {
                reg.add(x, y, z);
            }
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote) {
            TrashcanRegistry reg = TrashcanRegistry.get(world);
            if (reg != null) {
                reg.remove(x, y, z);
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
