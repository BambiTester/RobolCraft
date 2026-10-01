package com.angelika.lockerworker.item;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.angelika.lockerworker.CommonProxy;
import com.angelika.lockerworker.LockerWorkerMod;
import com.angelika.lockerworker.block.BlockWorkerBed;
import com.angelika.lockerworker.tileentity.TileEntityLocker;
import com.angelika.lockerworker.tileentity.TileEntityWorkerBed;
import com.angelika.lockerworker.util.LockerLink;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Linked worker bed item (vanilla 2-block place rules). NBT carries durable locker UUID.
 */
public class ItemWorkerBed extends Item {

    public ItemWorkerBed() {
        setUnlocalizedName("worker_bed");
        setTextureName(LockerWorkerMod.MODID + ":worker_bed_item");
        setCreativeTab(net.minecraft.creativetab.CreativeTabs.tabDecorations);
        setMaxStackSize(1);
    }

    /** Build a bed stack linked to the given locker. */
    public static ItemStack createLinked(UUID lockerId, int lockerX, int lockerY, int lockerZ, int lockerDim) {
        ItemStack stack = new ItemStack(CommonProxy.itemWorkerBed, 1, 0);
        NBTTagCompound tag = new NBTTagCompound();
        LockerLink.writeId(tag, lockerId);
        LockerLink.writeHint(tag, lockerX, lockerY, lockerZ, lockerDim);
        stack.setTagCompound(tag);
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        itemIcon = reg.registerIcon(LockerWorkerMod.MODID + ":worker_bed_item");
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        if (stack != null && stack.hasTagCompound() && LockerLink.hasShortId(stack.getTagCompound())) {
            int kind = LockerLink.readShortKind(stack.getTagCompound());
            int num = LockerLink.readShortNum(stack.getTagCompound());
            return LockerLink.formatBedItemDisplayName(kind, num);
        }
        // Unlinked (or linked UUID without short id yet): plain "Worker Bed" from lang
        return super.getItemStackDisplayName(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        if (stack != null && stack.hasTagCompound() && LockerLink.hasShortId(stack.getTagCompound())) {
            list.add(LockerLink.formatChatId(stack.getTagCompound()));
        } else {
            UUID id = LockerLink.readIdFromStack(stack);
            if (id != null) {
                list.add(LockerLink.formatChatId(id));
            } else {
                list.add("Unlinked worker bed");
            }
        }
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        if (side != 1) {
            return false;
        }
        ++y;
        BlockWorkerBed bed = CommonProxy.blockWorkerBed;
        if (bed == null) {
            return false;
        }
        int facing = MathHelper.floor_double((double) (player.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
        byte ox = 0;
        byte oz = 0;
        if (facing == 0) {
            oz = 1;
        } else if (facing == 1) {
            ox = -1;
        } else if (facing == 2) {
            oz = -1;
        } else if (facing == 3) {
            ox = 1;
        }

        if (!player.canPlayerEdit(x, y, z, side, stack) || !player.canPlayerEdit(x + ox, y, z + oz, side, stack)) {
            return false;
        }
        if (!world.isAirBlock(x, y, z) || !world.isAirBlock(x + ox, y, z + oz)) {
            return false;
        }
        if (!World.doesBlockHaveSolidTopSurface(world, x, y - 1, z)
            || !World.doesBlockHaveSolidTopSurface(world, x + ox, y - 1, z + oz)) {
            return false;
        }

        // Feet at (x,y,z), head at offset — same as vanilla ItemBed
        world.setBlock(x, y, z, bed, facing, 3);
        if (world.getBlock(x, y, z) == bed) {
            world.setBlock(x + ox, y, z + oz, bed, facing | BlockWorkerBed.META_HEAD, 3);
        }

        UUID lockerId = LockerLink.readIdFromStack(stack);
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityWorkerBed) {
            TileEntityWorkerBed bedTe = (TileEntityWorkerBed) te;
            bedTe.setLockerId(lockerId);
            if (stack.hasTagCompound() && LockerLink.hasHint(stack.getTagCompound())) {
                NBTTagCompound tag = stack.getTagCompound();
                bedTe.setLockerHint(
                    tag.getInteger(LockerLink.NBT_HINT_X),
                    tag.getInteger(LockerLink.NBT_HINT_Y),
                    tag.getInteger(LockerLink.NBT_HINT_Z),
                    tag.getInteger(LockerLink.NBT_HINT_DIM));
            }
            bedTe.linkToLocker(world);
        }

        // Notify locker of bed feet position
        if (lockerId != null) {
            TileEntityLocker locker = findLocker(world, stack, lockerId);
            if (locker != null) {
                locker.onLinkedBedPlaced(x, y, z, world.provider.dimensionId);
            }
        }

        --stack.stackSize;
        return true;
    }

    private static TileEntityLocker findLocker(World world, ItemStack stack, UUID lockerId) {
        if (stack != null && stack.hasTagCompound() && LockerLink.hasHint(stack.getTagCompound())) {
            NBTTagCompound tag = stack.getTagCompound();
            int hx = tag.getInteger(LockerLink.NBT_HINT_X);
            int hy = tag.getInteger(LockerLink.NBT_HINT_Y);
            int hz = tag.getInteger(LockerLink.NBT_HINT_Z);
            int hd = tag.getInteger(LockerLink.NBT_HINT_DIM);
            if (world.provider.dimensionId == hd) {
                TileEntity te = world.getTileEntity(hx, hy, hz);
                if (te instanceof TileEntityLocker) {
                    TileEntityLocker locker = (TileEntityLocker) te;
                    if (lockerId.equals(locker.getLockerId())) {
                        return locker;
                    }
                }
            }
        }
        return TileEntityLocker.findByLockerId(world, lockerId);
    }
}
