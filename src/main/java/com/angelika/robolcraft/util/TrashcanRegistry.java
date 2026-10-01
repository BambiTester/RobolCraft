package com.angelika.robolcraft.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;

import com.angelika.robolcraft.block.BlockTrashcan;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Per-dimension registry of trashcan block positions for BREAK AI.
 * Avoids O(radius³) cube scans when {@code trashcanSearchRadius} is large (1000+).
 *
 * <p>
 * Updated on place/break of {@link BlockTrashcan}, and by scanning loaded chunks
 * (discovering cans placed before this registry existed).
 */
public class TrashcanRegistry extends WorldSavedData {

    public static final String DATA_NAME = "robolcraft_trashcans";

    private static boolean handlerRegistered;

    /** Packed positions: see {@link #pack(int, int, int)}. */
    private final LinkedHashSet<Long> positions = new LinkedHashSet<Long>();

    public TrashcanRegistry(String name) {
        super(name);
    }

    public TrashcanRegistry() {
        super(DATA_NAME);
    }

    public static void registerChunkHandler() {
        if (handlerRegistered) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(new ChunkLoadListener());
        handlerRegistered = true;
    }

    public static TrashcanRegistry get(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        MapStorage storage = world.perWorldStorage;
        TrashcanRegistry data = (TrashcanRegistry) storage.loadData(TrashcanRegistry.class, DATA_NAME);
        if (data == null) {
            data = new TrashcanRegistry(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public void add(int x, int y, int z) {
        if (positions.add(Long.valueOf(pack(x, y, z)))) {
            markDirty();
        }
    }

    public void remove(int x, int y, int z) {
        if (positions.remove(Long.valueOf(pack(x, y, z)))) {
            markDirty();
        }
    }

    /**
     * Nearest registered trashcan to {@code (ox,oy,oz)} within {@code radiusBlocks}.
     * {@code radiusBlocks <= 0} means unlimited (any can in this dimension's registry).
     * Verifies the block is still a trashcan; drops stale entries.
     *
     * @return {@code int[]{x,y,z}} or {@code null} if none
     */
    public int[] findNearest(World world, double ox, double oy, double oz, int radiusBlocks) {
        if (world == null) {
            return null;
        }
        double maxDistSq;
        if (radiusBlocks <= 0) {
            maxDistSq = Double.MAX_VALUE;
        } else {
            double r = (double) radiusBlocks;
            maxDistSq = r * r;
        }

        int bestX = 0;
        int bestY = 0;
        int bestZ = 0;
        double bestDist = Double.MAX_VALUE;
        boolean found = false;
        boolean dirty = false;

        Iterator<Long> it = positions.iterator();
        while (it.hasNext()) {
            long key = it.next()
                .longValue();
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);

            // Only verify if the chunk is loaded — otherwise keep the entry for later.
            if (!world.blockExists(x, y, z)) {
                double ddx = (x + 0.5D) - ox;
                double ddy = (y + 0.5D) - oy;
                double ddz = (z + 0.5D) - oz;
                double d = ddx * ddx + ddy * ddy + ddz * ddz;
                if (d <= maxDistSq && d < bestDist) {
                    bestDist = d;
                    bestX = x;
                    bestY = y;
                    bestZ = z;
                    found = true;
                }
                continue;
            }

            if (!(world.getBlock(x, y, z) instanceof BlockTrashcan)) {
                it.remove();
                dirty = true;
                continue;
            }

            double ddx = (x + 0.5D) - ox;
            double ddy = (y + 0.5D) - oy;
            double ddz = (z + 0.5D) - oz;
            double d = ddx * ddx + ddy * ddy + ddz * ddz;
            if (d <= maxDistSq && d < bestDist) {
                bestDist = d;
                bestX = x;
                bestY = y;
                bestZ = z;
                found = true;
            }
        }

        if (dirty) {
            markDirty();
        }
        if (!found) {
            return null;
        }
        return new int[] { bestX, bestY, bestZ };
    }

    /** Scan one chunk's non-empty sections for trashcans and register them. */
    public void scanChunk(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        int baseX = chunk.xPosition << 4;
        int baseZ = chunk.zPosition << 4;
        ExtendedBlockStorage[] storages = chunk.getBlockStorageArray();
        if (storages == null) {
            return;
        }
        boolean dirty = false;
        for (int si = 0; si < storages.length; si++) {
            ExtendedBlockStorage storage = storages[si];
            if (storage == null || storage.isEmpty()) {
                continue;
            }
            int baseY = si << 4;
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        if (!(storage.getBlockByExtId(lx, ly, lz) instanceof BlockTrashcan)) {
                            continue;
                        }
                        if (positions.add(Long.valueOf(pack(baseX + lx, baseY + ly, baseZ + lz)))) {
                            dirty = true;
                        }
                    }
                }
            }
        }
        if (dirty) {
            markDirty();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        positions.clear();
        NBTTagList list = tag.getTagList("Cans", 10); // 10 = compound
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            positions.add(Long.valueOf(pack(e.getInteger("x"), e.getInteger("y"), e.getInteger("z"))));
        }
        // Legacy flat int array form: [x0,y0,z0, x1,y1,z1, ...]
        if (tag.hasKey("Pos") && positions.isEmpty()) {
            int[] flat = tag.getIntArray("Pos");
            for (int i = 0; i + 2 < flat.length; i += 3) {
                positions.add(Long.valueOf(pack(flat[i], flat[i + 1], flat[i + 2])));
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        List<Integer> flat = new ArrayList<Integer>(positions.size() * 3);
        for (Long keyObj : positions) {
            long key = keyObj.longValue();
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);
            NBTTagCompound e = new NBTTagCompound();
            e.setInteger("x", x);
            e.setInteger("y", y);
            e.setInteger("z", z);
            list.appendTag(e);
            flat.add(Integer.valueOf(x));
            flat.add(Integer.valueOf(y));
            flat.add(Integer.valueOf(z));
        }
        tag.setTag("Cans", list);
        int[] arr = new int[flat.size()];
        for (int i = 0; i < flat.size(); i++) {
            arr[i] = flat.get(i)
                .intValue();
        }
        tag.setIntArray("Pos", arr);
    }

    /** Pack signed block coords into a long (26-bit xz, 12-bit y) — same scheme as later BlockPos. */
    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | ((long) z & 0x3FFFFFFL);
    }

    static int unpackX(long packed) {
        long v = packed >> 38;
        return (int) (v << 38 >> 38);
    }

    static int unpackY(long packed) {
        return (int) ((packed >> 26) & 0xFFFL);
    }

    static int unpackZ(long packed) {
        long v = packed << 38 >> 38;
        return (int) v;
    }

    /** Forge listener: discover trashcans as chunks load (covers pre-v28 placements). */
    public static final class ChunkLoadListener {

        @SubscribeEvent
        public void onChunkLoad(ChunkEvent.Load event) {
            if (event.world == null || event.world.isRemote || event.getChunk() == null) {
                return;
            }
            TrashcanRegistry reg = TrashcanRegistry.get(event.world);
            if (reg != null) {
                reg.scanChunk(event.getChunk());
            }
        }
    }
}
