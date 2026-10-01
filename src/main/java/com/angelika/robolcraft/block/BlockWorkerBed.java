package com.angelika.robolcraft.block;

import java.util.Iterator;
import java.util.Random;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Direction;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import com.angelika.robolcraft.CommonProxy;
import com.angelika.robolcraft.RobolCraftMod;
import com.angelika.robolcraft.entity.EntityRobolCraft;
import com.angelika.robolcraft.item.ItemWorkerBed;
import com.angelika.robolcraft.tileentity.TileEntityLocker;
import com.angelika.robolcraft.tileentity.TileEntityWorkerBed;
import com.angelika.robolcraft.util.LockerLink;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Worker bed — vanilla 2-block bed model/behavior (render type 14) with custom textures
 * and durable locker UUID link on the feet-half TE.
 */
public class BlockWorkerBed extends BlockContainer {

    /** Same layout as {@link net.minecraft.block.BlockBed#field_149981_a}. */
    public static final int[][] OFFSETS = new int[][] { { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 0 } };

    public static final int META_HEAD = 0x8;
    public static final int META_OCCUPIED = 0x4;
    public static final int META_DIR_MASK = 0x3;

    @SideOnly(Side.CLIENT)
    private IIcon[] iconTop;
    @SideOnly(Side.CLIENT)
    private IIcon[] iconEnd;
    @SideOnly(Side.CLIENT)
    private IIcon[] iconSide;

    public BlockWorkerBed() {
        super(Material.cloth);
        setHardness(0.2F);
        setBlockName("worker_bed");
        setBlockTextureName(RobolCraftMod.MODID + ":bed");
        setCreativeTab(null); // obtained from locker place, not creative dup without link
        setBounds();
    }

    private void setBounds() {
        setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.5625F, 1.0F);
    }

    public static boolean isHead(int meta) {
        return (meta & META_HEAD) != 0;
    }

    public static boolean isOccupied(int meta) {
        return (meta & META_OCCUPIED) != 0;
    }

    public static int getDirection(int meta) {
        return meta & META_DIR_MASK;
    }

    public static void setOccupied(World world, int x, int y, int z, boolean occupied) {
        int meta = world.getBlockMetadata(x, y, z);
        if (occupied) {
            meta |= META_OCCUPIED;
        } else {
            meta &= ~META_OCCUPIED;
        }
        world.setBlockMetadataWithNotify(x, y, z, meta, 4);
    }

    /** Resolve feet-half TE from either half. */
    public static TileEntityWorkerBed getBedTE(IBlockAccess world, int x, int y, int z) {
        int meta = world.getBlockMetadata(x, y, z);
        if (isHead(meta)) {
            int dir = getDirection(meta);
            x -= OFFSETS[dir][0];
            z -= OFFSETS[dir][1];
        }
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TileEntityWorkerBed ? (TileEntityWorkerBed) te : null;
    }

    /** Head-block coords from feet. */
    public static int[] headCoords(int feetX, int feetY, int feetZ, int meta) {
        int dir = getDirection(meta);
        return new int[] { feetX + OFFSETS[dir][0], feetY, feetZ + OFFSETS[dir][1] };
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        // v15: shift-right-click chats linked locker ID (does not sleep)
        if (player.isSneaking()) {
            TileEntityWorkerBed bedTe = getBedTE(world, x, y, z);
            UUID id = bedTe != null ? bedTe.getLockerId() : null;
            TileEntityLocker locker = id != null ? TileEntityLocker.findByLockerId(world, id) : null;
            String line = locker != null ? locker.formatChatIdLine() : LockerLink.formatChatId(id);
            player.addChatMessage(new ChatComponentText(line));
            return true;
        }
        int meta = world.getBlockMetadata(x, y, z);
        if (!isHead(meta)) {
            int dir = getDirection(meta);
            x += OFFSETS[dir][0];
            z += OFFSETS[dir][1];
            if (world.getBlock(x, y, z) != this) {
                return true;
            }
            meta = world.getBlockMetadata(x, y, z);
        }

        if (world.provider.canRespawnHere() && world.getBiomeGenForCoords(x, z) != BiomeGenBase.hell) {
            if (isOccupied(meta)) {
                // Occupied by player?
                EntityPlayer sleeper = findSleepingPlayer(world, x, y, z);
                if (sleeper != null) {
                    player.addChatComponentMessage(new ChatComponentTranslation("tile.bed.occupied"));
                    return true;
                }
                // Occupied by worker — player waits (message)
                if (isOccupiedByWorker(world, x, y, z)) {
                    player.addChatComponentMessage(new ChatComponentTranslation("tile.bed.occupied"));
                    return true;
                }
                setOccupied(world, x, y, z, false);
            }

            EntityPlayer.EnumStatus status = player.sleepInBedAt(x, y, z);
            if (status == EntityPlayer.EnumStatus.OK) {
                setOccupied(world, x, y, z, true);
                // Also mark feet half occupied bit for consistency
                int dir = getDirection(meta);
                int fx = x - OFFSETS[dir][0];
                int fz = z - OFFSETS[dir][1];
                if (world.getBlock(fx, y, fz) == this) {
                    setOccupied(world, fx, y, fz, true);
                }
                return true;
            }
            if (status == EntityPlayer.EnumStatus.NOT_POSSIBLE_NOW) {
                player.addChatComponentMessage(new ChatComponentTranslation("tile.bed.noSleep"));
            } else if (status == EntityPlayer.EnumStatus.NOT_SAFE) {
                player.addChatComponentMessage(new ChatComponentTranslation("tile.bed.notSafe"));
            }
            return true;
        }
        // Nether-like: explode like vanilla bed
        double cx = x + 0.5D;
        double cy = y + 0.5D;
        double cz = z + 0.5D;
        world.setBlockToAir(x, y, z);
        int dir = getDirection(meta);
        int ox = x + OFFSETS[dir][0];
        int oz = z + OFFSETS[dir][1];
        if (world.getBlock(ox, y, oz) == this) {
            world.setBlockToAir(ox, y, oz);
            cx = (cx + ox + 0.5D) / 2.0D;
            cy = (cy + y + 0.5D) / 2.0D;
            cz = (cz + oz + 0.5D) / 2.0D;
        }
        world.newExplosion((Entity) null, x + 0.5F, y + 0.5F, z + 0.5F, 5.0F, true, true);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static EntityPlayer findSleepingPlayer(World world, int x, int y, int z) {
        Iterator<EntityPlayer> it = world.playerEntities.iterator();
        while (it.hasNext()) {
            EntityPlayer p = it.next();
            if (p.isPlayerSleeping()) {
                ChunkCoordinates c = p.playerLocation;
                if (c != null && c.posX == x && c.posY == y && c.posZ == z) {
                    return p;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static boolean isOccupiedByWorker(World world, int x, int y, int z) {
        // Head block coords; workers store sleep at feet or head — check nearby
        java.util.List<EntityRobolCraft> list = world.getEntitiesWithinAABB(
            EntityRobolCraft.class,
            net.minecraft.util.AxisAlignedBB.getBoundingBox(x - 1, y - 1, z - 1, x + 2, y + 2, z + 2));
        for (EntityRobolCraft w : list) {
            if (w != null && w.isLyingInBed()) {
                return true;
            }
        }
        return false;
    }

    /** True if a player is currently sleeping in this bed (head cell). */
    public static boolean isPlayerOccupying(World world, int headX, int headY, int headZ) {
        return findSleepingPlayer(world, headX, headY, headZ) != null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (side == 0) {
            return Blocks.planks.getBlockTextureFromSide(side);
        }
        int dir = getDirection(meta);
        int remapped = Direction.bedDirection[dir][side];
        int head = isHead(meta) ? 1 : 0;
        if ((head == 1 && remapped == 2) || (head == 0 && remapped == 3)) {
            return iconEnd[head];
        }
        if (remapped == 5 || remapped == 4) {
            return iconSide[head];
        }
        return iconTop[head];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        String mod = RobolCraftMod.MODID;
        iconTop = new IIcon[] { reg.registerIcon(mod + ":bed_feet_top"), reg.registerIcon(mod + ":bed_head_top") };
        iconEnd = new IIcon[] { reg.registerIcon(mod + ":bed_feet_end"), reg.registerIcon(mod + ":bed_head_end") };
        iconSide = new IIcon[] { reg.registerIcon(mod + ":bed_feet_side"), reg.registerIcon(mod + ":bed_head_side") };
        blockIcon = iconTop[0];
    }

    @Override
    public int getRenderType() {
        return 14; // vanilla bed
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        setBounds();
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        int meta = world.getBlockMetadata(x, y, z);
        int dir = getDirection(meta);
        if (isHead(meta)) {
            if (world.getBlock(x - OFFSETS[dir][0], y, z - OFFSETS[dir][1]) != this) {
                world.setBlockToAir(x, y, z);
            }
        } else if (world.getBlock(x + OFFSETS[dir][0], y, z + OFFSETS[dir][1]) != this) {
            world.setBlockToAir(x, y, z);
            if (!world.isRemote) {
                dropBlockAsItem(world, x, y, z, meta, 0);
            }
        }
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        return isHead(meta) ? null : CommonProxy.itemWorkerBed;
    }

    @Override
    public void dropBlockAsItemWithChance(World world, int x, int y, int z, int meta, float chance, int fortune) {
        if (!isHead(meta)) {
            // v19: linked bed returns to locker GUI slot — never floor-drop when locker exists
            if (!world.isRemote && world.getGameRules()
                .getGameRuleBooleanValue("doTileDrops")) {
                TileEntityWorkerBed te = getBedTE(world, x, y, z);
                UUID id = te != null ? te.getLockerId() : null;
                TileEntityLocker locker = id != null ? TileEntityLocker.findByLockerId(world, id) : null;
                if (locker != null) {
                    // onLinkedBedRemoved already put item in slot; skip floor drop
                    return;
                }
                ItemStack stack = createDropStack(world, x, y, z);
                dropBlockAsItem(world, x, y, z, stack);
            }
        }
    }

    private ItemStack createDropStack(World world, int x, int y, int z) {
        TileEntityWorkerBed te = getBedTE(world, x, y, z);
        UUID id = te != null ? te.getLockerId() : null;
        if (id != null) {
            int hx = x, hy = y, hz = z, hd = world.provider.dimensionId;
            if (te.hasLockerHint()) {
                hx = te.getHintX();
                hy = te.getHintY();
                hz = te.getHintZ();
                hd = te.getHintDim();
            }
            return ItemWorkerBed.createLinked(id, hx, hy, hz, hd);
        }
        ItemStack stack = new ItemStack(CommonProxy.itemWorkerBed, 1, 0);
        stack.setTagCompound(new NBTTagCompound());
        return stack;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote && !isHead(meta)) {
            TileEntityWorkerBed te = getBedTE(world, x, y, z);
            UUID id = te != null ? te.getLockerId() : null;
            if (id != null) {
                TileEntityLocker locker = TileEntityLocker.findByLockerId(world, id);
                if (locker != null) {
                    locker.onLinkedBedRemoved();
                }
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        if (isHead(meta)) {
            return null;
        }
        return new TileEntityWorkerBed();
    }

    @Override
    public int getMobilityFlag() {
        return 1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Item getItem(World world, int x, int y, int z) {
        return CommonProxy.itemWorkerBed;
    }

    @Override
    public void onBlockHarvested(World world, int x, int y, int z, int meta, EntityPlayer player) {
        if (player.capabilities.isCreativeMode && isHead(meta)) {
            int dir = getDirection(meta);
            int fx = x - OFFSETS[dir][0];
            int fz = z - OFFSETS[dir][1];
            if (world.getBlock(fx, y, fz) == this) {
                world.setBlockToAir(fx, y, fz);
            }
        }
    }
}
