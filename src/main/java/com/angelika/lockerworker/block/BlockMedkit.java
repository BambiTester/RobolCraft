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
import net.minecraftforge.common.util.ForgeDirection;

import com.angelika.lockerworker.LockerWorkerMod;
import com.angelika.lockerworker.util.MedkitRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Wall-mounted vertical half-slab medkit. Metadata bits 0–1: front facing
 * (0=S, 1=W, 2=N, 3=E). Occupies the half of the cell flush against the support wall;
 * front (cross) faces outward toward the placer.
 */
public class BlockMedkit extends Block {

    public static final int META_FACING_MASK = 0x3;

    @SideOnly(Side.CLIENT)
    private IIcon iconFront;
    @SideOnly(Side.CLIENT)
    private IIcon iconSide;
    @SideOnly(Side.CLIENT)
    private IIcon iconBack;

    public BlockMedkit() {
        super(Material.iron);
        setHardness(1.5F);
        setResistance(8.0F);
        setStepSound(soundTypeMetal);
        setBlockName("medkit");
        setBlockTextureName(LockerWorkerMod.MODID + ":medkit_side");
        setCreativeTab(CreativeTabs.tabDecorations);
        setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.5F);
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

    public static int getBackSide(int facing) {
        switch (facing & META_FACING_MASK) {
            case 0:
                return 2;
            case 1:
                return 5;
            case 2:
                return 3;
            case 3:
                return 4;
            default:
                return 2;
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
    public boolean canPlaceBlockOnSide(World world, int x, int y, int z, int side) {
        // Sides only — vertical wall mount
        if (side < 2 || side > 5) {
            return false;
        }
        return canPlaceAgainst(world, x, y, z, side);
    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        return canPlaceAgainst(world, x, y, z, 2) || canPlaceAgainst(world, x, y, z, 3)
            || canPlaceAgainst(world, x, y, z, 4)
            || canPlaceAgainst(world, x, y, z, 5);
    }

    private static boolean canPlaceAgainst(World world, int x, int y, int z, int side) {
        int sx = x;
        int sy = y;
        int sz = z;
        // Support is opposite the clicked face direction (block we attach to)
        if (side == 2) {
            sz++; // clicked north face of support → support is south of us
        } else if (side == 3) {
            sz--;
        } else if (side == 4) {
            sx++;
        } else if (side == 5) {
            sx--;
        } else {
            return false;
        }
        return world.getBlock(sx, sy, sz)
            .isSideSolid(world, sx, sy, sz, ForgeDirection.getOrientation(side));
    }

    /**
     * {@code side} is the face of the neighbor that was clicked. Front faces that
     * direction (outward); AABB occupies the half flush to the support.
     */
    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int meta) {
        switch (side) {
            case 2:
                return 2; // front North
            case 3:
                return 0; // front South
            case 4:
                return 1; // front West
            case 5:
                return 3; // front East
            default:
                return 0;
        }
    }

    private void applyBounds(int facing) {
        switch (facing & META_FACING_MASK) {
            case 0: // front S — flush to north (wall behind at -Z)
                setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.5F);
                break;
            case 1: // front W — flush to east
                setBlockBounds(0.5F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
                break;
            case 2: // front N — flush to south
                setBlockBounds(0.0F, 0.0F, 0.5F, 1.0F, 1.0F, 1.0F);
                break;
            case 3: // front E — flush to west
                setBlockBounds(0.0F, 0.0F, 0.0F, 0.5F, 1.0F, 1.0F);
                break;
            default:
                setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.5F);
                break;
        }
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        applyBounds(getFacing(world.getBlockMetadata(x, y, z)));
    }

    @Override
    public void setBlockBoundsForItemRender() {
        // Inventory: south-front half slab silhouette
        setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.5F);
    }

    @Override
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List list,
        Entity entity) {
        setBlockBoundsBasedOnState(world, x, y, z);
        super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        setBlockBoundsBasedOnState(world, x, y, z);
        return AxisAlignedBB.getBoundingBox(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        return getCollisionBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        String mod = LockerWorkerMod.MODID;
        iconFront = reg.registerIcon(mod + ":medkit_front");
        iconSide = reg.registerIcon(mod + ":medkit_side");
        iconBack = reg.registerIcon(mod + ":medkit_back");
        blockIcon = iconSide;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        int facing = getFacing(meta);
        int front = getFrontSide(facing);
        int back = getBackSide(facing);
        if (side == front) {
            return iconFront != null ? iconFront : blockIcon;
        }
        if (side == back) {
            return iconBack != null ? iconBack : blockIcon;
        }
        return iconSide != null ? iconSide : blockIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        return getIcon(side, world.getBlockMetadata(x, y, z));
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (!world.isRemote) {
            MedkitRegistry reg = MedkitRegistry.get(world);
            if (reg != null) {
                reg.add(x, y, z);
            }
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote) {
            MedkitRegistry reg = MedkitRegistry.get(world);
            if (reg != null) {
                reg.remove(x, y, z);
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }
}
