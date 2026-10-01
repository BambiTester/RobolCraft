package com.angelika.robolcraft.block;

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

import com.angelika.robolcraft.RobolCraftMod;
import com.angelika.robolcraft.inventory.GuiHandler;
import com.angelika.robolcraft.tileentity.TileEntitySupervisorLocker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Two-tall supervisor locker block (red steel). Metadata bit 0x8 marks the UPPER half (vanilla door style).
 * Bits 0–1 store horizontal facing (0=S, 1=W, 2=N, 3=E) for both halves.
 * Multi-icon faces per TEXTURE_UV_NOTES / UV_NOTES.
 *
 * <p>
 * <b>Redstone (v13):</b> locker does <b>not</b> emit power. Power <b>into</b> the
 * top or bottom half is the only forced-stay trigger (v15).
 * Shift-right-click: toggle forced stay. Left-click/punch: aggressive. Plain RC: ID.
 */
public class BlockSupervisorLocker extends BlockContainer {

    public static final int META_UPPER = 0x8;
    public static final int META_FACING_MASK = 0x3;

    @SideOnly(Side.CLIENT)
    private IIcon iconBottomFront;
    @SideOnly(Side.CLIENT)
    private IIcon iconTopFront;
    @SideOnly(Side.CLIENT)
    private IIcon iconTopFrontAggressive;
    @SideOnly(Side.CLIENT)
    private IIcon iconSide;
    @SideOnly(Side.CLIENT)
    private IIcon iconTop;
    @SideOnly(Side.CLIENT)
    private IIcon iconBottom;
    @SideOnly(Side.CLIENT)
    private IIcon iconBottomFace;

    public BlockSupervisorLocker() {
        super(Material.iron);
        setHardness(2.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setBlockName("supervisor_locker");
        // Fallback / inventory default; particles use locker_particle via registerBlockIcons
        setBlockTextureName(RobolCraftMod.MODID + ":supervisor_locker");
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
     * Front world side from facing meta (matches TileEntitySupervisorLocker spawn offsets):
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
        String mod = RobolCraftMod.MODID;
        iconBottomFront = reg.registerIcon(mod + ":supervisor_locker_bottom_front");
        iconTopFront = reg.registerIcon(mod + ":supervisor_locker_top_front");
        iconTopFrontAggressive = reg.registerIcon(mod + ":supervisor_locker_top_front_aggressive");
        iconSide = reg.registerIcon(mod + ":supervisor_locker_side");
        iconTop = reg.registerIcon(mod + ":supervisor_locker_top");
        iconBottom = reg.registerIcon(mod + ":supervisor_locker_bottom");
        iconBottomFace = reg.registerIcon(mod + ":supervisor_locker_bottom_face");
        // Break/hit particles — average steel
        blockIcon = reg.registerIcon(mod + ":supervisor_locker_particle");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return getIconFor(side, meta, false);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int meta = world.getBlockMetadata(x, y, z);
        boolean aggressiveFront = false;
        if (isUpper(meta) && side == getFrontSide(getFacing(meta))) {
            TileEntitySupervisorLocker te = getLockerTE(world, x, y, z, meta);
            aggressiveFront = te != null && te.isAggressive();
        }
        return getIconFor(side, meta, aggressiveFront);
    }

    @SideOnly(Side.CLIENT)
    private IIcon getIconFor(int side, int meta, boolean aggressiveFront) {
        boolean upper = isUpper(meta);
        int front = getFrontSide(getFacing(meta));

        // Vertical faces
        if (side == 0) { // DOWN
            if (upper) {
                return iconSide; // hidden against lower
            }
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
            if (upper) {
                if (aggressiveFront && iconTopFrontAggressive != null) {
                    return iconTopFrontAggressive;
                }
                return iconTopFront;
            }
            return iconBottomFront;
        }
        return iconSide;
    }

    // --- Redstone: no output (v13). Input force-stay checked on TE. ---

    @Override
    public boolean canProvidePower() {
        return false;
    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        return y < world.getHeight() - 1 && super.canPlaceBlockAt(world, x, y, z)
            && super.canPlaceBlockAt(world, x, y + 1, z);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        // Face TOWARD the player (front faces placer); vanilla yaw mapping faces away.
        int facing = MathHelper.floor_double((double) (placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        facing = (facing + 2) & META_FACING_MASK;
        // Keep existing BOTTOM TE; only update metadata, then auto-place TOP half
        world.setBlockMetadataWithNotify(x, y, z, facing, 2);
        world.setBlock(x, y + 1, z, this, facing | META_UPPER, 2);

        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntitySupervisorLocker) {
            ((TileEntitySupervisorLocker) te).onPlacedBy(placer);
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote) {
            TileEntitySupervisorLocker lockerTe = getLockerTE(world, x, y, z, meta);
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
            // Neighbors need power update when locker removed
            world.notifyBlocksOfNeighborChange(x, y, z, block);
            world.notifyBlocksOfNeighborChange(x, y + (isUpper(meta) ? -1 : 1), z, block);
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    /** Resolve the BOTTOM-half TileEntitySupervisorLocker from either half. */
    public static TileEntitySupervisorLocker getLockerTE(IBlockAccess world, int x, int y, int z, int meta) {
        int teY = isUpper(meta) ? y - 1 : y;
        TileEntity te = world.getTileEntity(x, teY, z);
        return te instanceof TileEntitySupervisorLocker ? (TileEntitySupervisorLocker) te : null;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        // Only the lower half owns the TE (upper shares space conceptually; Forge still
        // creates TE per block unless we return null for upper).
        if (isUpper(meta)) {
            return null;
        }
        return new TileEntitySupervisorLocker();
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        // Only lower half drops (avoid double drops when both halves break)
        return isUpper(meta) ? null : Item.getItemFromBlock(this);
    }

    @Override
    public void onBlockHarvested(World world, int x, int y, int z, int meta, EntityPlayer player) {
        // Survival: breaking the TOP must still drop the locker item (bottom owns the drop).
        if (isUpper(meta)) {
            if (world.getBlock(x, y - 1, z) == this) {
                if (!player.capabilities.isCreativeMode) {
                    int lowerMeta = world.getBlockMetadata(x, y - 1, z);
                    dropBlockAsItem(world, x, y - 1, z, lowerMeta & META_FACING_MASK, 0);
                }
                world.setBlockToAir(x, y - 1, z);
            }
        } else if (player.capabilities.isCreativeMode) {
            if (world.getBlock(x, y + 1, z) == this) {
                world.setBlockToAir(x, y + 1, z);
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        // v19: right-click opens locker GUI (top or bottom). Punch/shift bindings removed.
        if (world.isRemote) {
            return true;
        }
        int meta = world.getBlockMetadata(x, y, z);
        TileEntitySupervisorLocker te = getLockerTE(world, x, y, z, meta);
        if (te == null) {
            return false;
        }
        // Ensure TE coords are bottom half for GUI
        int gx = te.xCoord;
        int gy = te.yCoord;
        int gz = te.zCoord;
        player.openGui(
            com.angelika.robolcraft.RobolCraftMod.instance,
            GuiHandler.GUI_SUPERVISOR_LOCKER,
            world,
            gx,
            gy,
            gz);
        return true;
    }
}
