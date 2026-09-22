package com.angelika.lockerworker.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.angelika.lockerworker.LockerWorkerMod;
import com.angelika.lockerworker.tileentity.TileEntityLocker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Two-tall locker block. Metadata bit 0x8 marks the UPPER half (vanilla door style).
 * Bits 0–1 store horizontal facing (0=S, 1=W, 2=N, 3=E) for both halves.
 * Multi-icon faces per TEXTURE_UV_NOTES / UV_NOTES.
 */
public class BlockLocker extends BlockContainer {

    public static final int META_UPPER = 0x8;
    public static final int META_FACING_MASK = 0x3;

    @SideOnly(Side.CLIENT)
    private IIcon iconBottomFront;
    @SideOnly(Side.CLIENT)
    private IIcon iconTopFront;
    @SideOnly(Side.CLIENT)
    private IIcon iconSide;
    @SideOnly(Side.CLIENT)
    private IIcon iconTop;
    @SideOnly(Side.CLIENT)
    private IIcon iconBottom;
    @SideOnly(Side.CLIENT)
    private IIcon iconBottomFace;

    public BlockLocker() {
        super(Material.iron);
        setHardness(2.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setBlockName("locker");
        // Fallback / inventory default; particles use locker_particle via registerBlockIcons
        setBlockTextureName(LockerWorkerMod.MODID + ":locker");
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.tabDecorations);
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
        // 0 = standard cube; multi-icon faces via getIcon
        return 0;
    }

    public static boolean isUpper(int meta) {
        return (meta & META_UPPER) != 0;
    }

    public static int getFacing(int meta) {
        return meta & META_FACING_MASK;
    }

    /**
     * Front world side from facing meta (matches TileEntityLocker spawn offsets):
     * 0=South(3), 1=West(4), 2=North(2), 3=East(5).
     */
    public static int getFrontSide(int facing) {
        switch (facing & META_FACING_MASK) {
            case 0:
                return 3; // South
            case 1:
                return 4; // West
            case 2:
                return 2; // North
            case 3:
                return 5; // East
            default:
                return 3;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        String mod = LockerWorkerMod.MODID;
        iconBottomFront = reg.registerIcon(mod + ":locker_bottom_front");
        iconTopFront = reg.registerIcon(mod + ":locker_top_front");
        iconSide = reg.registerIcon(mod + ":locker_side");
        iconTop = reg.registerIcon(mod + ":locker_top");
        iconBottom = reg.registerIcon(mod + ":locker_bottom");
        iconBottomFace = reg.registerIcon(mod + ":locker_bottom_face");
        // Break/hit particles — average steel
        blockIcon = reg.registerIcon(mod + ":locker_particle");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        boolean upper = isUpper(meta);
        int front = getFrontSide(getFacing(meta));

        // Vertical faces
        if (side == 0) { // DOWN
            if (upper) {
                return iconSide; // hidden against lower
            }
            // Prefer dedicated underside; bottom_face is the visible floor face
            return iconBottomFace != null ? iconBottomFace : iconBottom;
        }
        if (side == 1) { // UP
            if (upper) {
                return iconTop;
            }
            return iconBottom != null ? iconBottom : iconSide; // hidden against upper
        }

        // Horizontal: front door vs sides/back
        if (side == front) {
            return upper ? iconTopFront : iconBottomFront;
        }
        return iconSide;
    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        return y < world.getHeight() - 1 && super.canPlaceBlockAt(world, x, y, z)
            && super.canPlaceBlockAt(world, x, y + 1, z);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        int facing = MathHelper.floor_double((double) (placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        // Keep existing BOTTOM TE; only update metadata, then auto-place TOP half
        world.setBlockMetadataWithNotify(x, y, z, facing, 2);
        world.setBlock(x, y + 1, z, this, facing | META_UPPER, 2);

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityLocker) {
            ((TileEntityLocker) te).onPlacedBy(placer);
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote) {
            TileEntityLocker lockerTe = getLockerTE(world, x, y, z, meta);
            if (lockerTe != null) {
                lockerTe.killAssignedWorker();
            }
            // Destroy the other half
            if (isUpper(meta)) {
                if (world.getBlock(x, y - 1, z) == this) {
                    world.setBlockToAir(x, y - 1, z);
                }
            } else {
                if (world.getBlock(x, y + 1, z) == this) {
                    world.setBlockToAir(x, y + 1, z);
                }
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    /** Resolve the BOTTOM-half TileEntityLocker from either half. */
    public static TileEntityLocker getLockerTE(IBlockAccess world, int x, int y, int z, int meta) {
        int teY = isUpper(meta) ? y - 1 : y;
        TileEntity te = world.getTileEntity(x, teY, z);
        return te instanceof TileEntityLocker ? (TileEntityLocker) te : null;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        // Only the lower half owns the TE (upper shares space conceptually; Forge still
        // creates TE per block unless we return null for upper).
        if (isUpper(meta)) {
            return null;
        }
        return new TileEntityLocker();
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        // Only lower half drops the item (avoid double drops)
        return isUpper(meta) ? null : Item.getItemFromBlock(this);
    }

    @Override
    public void onBlockHarvested(World world, int x, int y, int z, int meta, EntityPlayer player) {
        // Creative-mode: ensure both halves cleared without double TE cleanup issues
        if (player.capabilities.isCreativeMode && isUpper(meta)) {
            if (world.getBlock(x, y - 1, z) == this) {
                world.setBlockToAir(x, y - 1, z);
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        // Not player-interactable for trading; optional future GUI.
        return false;
    }
}
