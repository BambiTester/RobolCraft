package com.angelika.lockerworker.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.angelika.lockerworker.Config;

/**
 * Detect GregTech <b>processing machines</b> near a position using <b>reflection only</b>.
 * Intentionally has <b>zero</b> compile-time {@code import gregtech.*} so loading this
 * class (via entity AI during client preInit entity registration) does not force GT API
 * class resolution during BLS splash / early client discover.
 *
 * <p>
 * GT classes are resolved on first in-world machine scan (long after splash).
 *
 * <p>
 * Detection order (same as before):
 * <ol>
 * <li>Prefilter: block == GregTechAPI.sBlockMachines (reflect) or BlockMachines by name</li>
 * <li>TE implements IGregTechTileEntity (by Class.isInstance) + canAccessData + getMetaTileEntity != null</li>
 * <li>Reject pipe TE by class name ({@code BaseMetaPipeEntity})</li>
 * <li>Reject pipe/frame meta-tile ID ranges</li>
 * <li>Accept only if meta-tile ID is in {@link ProcessingMachineIds}</li>
 * </ol>
 *
 * @see ProcessingMachineIds
 */
public final class GregTechMachineLookup {

    private static final String PIPE_TE_SIMPLE_NAME = "BaseMetaPipeEntity";
    private static final String PIPE_TE_FQCN = "gregtech.api.metatileentity.BaseMetaPipeEntity";
    private static final String BLOCK_MACHINES_FQCN = "gregtech.common.blocks.BlockMachines";
    private static final String GREGTECH_API_FQCN = "gregtech.api.GregTechAPI";
    private static final String IGTE_FQCN = "gregtech.api.interfaces.tileentity.IGregTechTileEntity";

    /** Lazy holder: GT reflection resolved on first scan, not at class init. */
    private static final class GtReflect {

        static final Block S_BLOCK_MACHINES;
        static final Class<?> IGTE_CLASS;
        static final Method CAN_ACCESS_DATA;
        static final Method GET_META_TILE_ENTITY;
        static final Method GET_META_TILE_ID;
        static final boolean AVAILABLE;

        static {
            Block sBlock = null;
            Class<?> igt = null;
            Method canAccess = null;
            Method getMte = null;
            Method getId = null;
            boolean ok = false;
            try {
                Class<?> api = Class.forName(GREGTECH_API_FQCN);
                Field f = api.getField("sBlockMachines");
                Object raw = f.get(null);
                if (raw instanceof Block) {
                    sBlock = (Block) raw;
                }
                igt = Class.forName(IGTE_FQCN);
                canAccess = igt.getMethod("canAccessData");
                getMte = igt.getMethod("getMetaTileEntity");
                getId = igt.getMethod("getMetaTileID");
                ok = true;
            } catch (Throwable t) {
                // GT missing or API mismatch — scans become no-ops
                ok = false;
            }
            S_BLOCK_MACHINES = sBlock;
            IGTE_CLASS = igt;
            CAN_ACCESS_DATA = canAccess;
            GET_META_TILE_ENTITY = getMte;
            GET_META_TILE_ID = getId;
            AVAILABLE = ok;
        }
    }

    /**
     * v25: shared per-chunk-cell scan cache so nearby workers reuse one cube scan
     * within {@link com.angelika.lockerworker.Config#machineScanIntervalTicks}.
     * Feel (radius/interval) unchanged; random pick uses the cached list (no 2nd cube).
     */
    private static final class ScanCacheEntry {

        final List<int[]> machines;
        final long expireAt;
        final int cy;

        ScanCacheEntry(List<int[]> machines, long expireAt, int cy) {
            this.machines = machines;
            this.expireAt = expireAt;
            this.cy = cy;
        }
    }

    /** dim + cellX + cellZ + radius + supervisorBit */
    private static final Map<Long, ScanCacheEntry> SCAN_CACHE = new HashMap<Long, ScanCacheEntry>();
    private static final int CACHE_CELL = 32;
    private static long lastCacheCleanup;

    private static long scanCacheKey(World world, int cx, int cz, int radius, boolean supervisor) {
        int cellX = cx >= 0 ? cx / CACHE_CELL : (cx - CACHE_CELL + 1) / CACHE_CELL;
        int cellZ = cz >= 0 ? cz / CACHE_CELL : (cz - CACHE_CELL + 1) / CACHE_CELL;
        long dim = world.provider.dimensionId & 0xFFFFL;
        long key = (dim << 48) ^ (((long) cellX & 0xFFFFFL) << 28)
            ^ (((long) cellZ & 0xFFFFFL) << 8)
            ^ (radius & 0xFFL);
        if (supervisor) {
            key ^= 1L << 63;
        }
        return key;
    }

    private static void maybeCleanupCache(long now) {
        if (now - lastCacheCleanup < 200) {
            return;
        }
        lastCacheCleanup = now;
        Iterator<Map.Entry<Long, ScanCacheEntry>> it = SCAN_CACHE.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ScanCacheEntry> e = it.next();
            if (e.getValue().expireAt <= now) {
                it.remove();
            }
        }
    }

    private static List<int[]> getCachedOrScan(World world, int cx, int cy, int cz, int radius, boolean supervisor) {
        long now = world.getTotalWorldTime();
        maybeCleanupCache(now);
        long key = scanCacheKey(world, cx, cz, radius, supervisor);
        ScanCacheEntry hit = SCAN_CACHE.get(key);
        if (hit != null && hit.expireAt > now && Math.abs(hit.cy - cy) <= 8) {
            return hit.machines;
        }
        List<int[]> found = supervisor ? scanSupervisorMachines(world, cx, cy, cz, radius)
            : scanProcessingMachines(world, cx, cy, cz, radius);
        // TTL matches configured scan interval so feel stays the same
        int ttl = Math.max(10, Config.machineScanIntervalTicks);
        SCAN_CACHE.put(key, new ScanCacheEntry(found, now + ttl, cy));
        return found;
    }

    private GregTechMachineLookup() {}

    /**
     * @return true if the block at xyz is a whitelisted GT single-block processing machine
     *         (not a pipe, cable, hatch, hull, or other non-listed MTE).
     */
    public static boolean isGregTechMachine(World world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        Block block = world.getBlock(x, y, z);
        if (!isMachinesBlock(block)) {
            return false;
        }

        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null) {
            return false;
        }
        if (isPipeTileEntity(te)) {
            return false;
        }
        if (!GtReflect.AVAILABLE || GtReflect.IGTE_CLASS == null) {
            return false;
        }
        if (!GtReflect.IGTE_CLASS.isInstance(te)) {
            return false;
        }

        try {
            Object can = GtReflect.CAN_ACCESS_DATA.invoke(te);
            if (!(can instanceof Boolean) || !((Boolean) can).booleanValue()) {
                return false;
            }
            Object mte = GtReflect.GET_META_TILE_ENTITY.invoke(te);
            if (mte == null) {
                return false;
            }
            Object idObj = GtReflect.GET_META_TILE_ID.invoke(te);
            if (!(idObj instanceof Integer)) {
                return false;
            }
            final int metaTileId = ((Integer) idObj).intValue();
            if (ProcessingMachineIds.isPipeOrFrameId(metaTileId)) {
                return false;
            }
            return ProcessingMachineIds.contains(metaTileId);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isMachinesBlock(Block block) {
        if (block == null) {
            return false;
        }
        // Touch GtReflect only when checking — first call resolves GT classes.
        if (GtReflect.S_BLOCK_MACHINES != null && block == GtReflect.S_BLOCK_MACHINES) {
            return true;
        }
        return BLOCK_MACHINES_FQCN.equals(
            block.getClass()
                .getName());
    }

    /**
     * True for GregTech pipe/cable tile entities without importing BaseMetaPipeEntity.
     */
    public static boolean isPipeTileEntity(TileEntity te) {
        if (te == null) {
            return false;
        }
        Class<?> clazz = te.getClass();
        while (clazz != null && clazz != Object.class) {
            String simple = clazz.getSimpleName();
            String name = clazz.getName();
            if (PIPE_TE_SIMPLE_NAME.equals(simple) || PIPE_TE_FQCN.equals(name)) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    /**
     * Find nearest whitelisted GT processing machine within radius (cube scan, throttled by caller).
     *
     * @return int[]{x,y,z} or null if none found
     */
    public static int[] findNearestMachine(World world, int cx, int cy, int cz, int radius) {
        if (world == null || radius <= 0) {
            return null;
        }
        List<int[]> all = findMachines(world, cx, cy, cz, radius);
        int bestDistSq = Integer.MAX_VALUE;
        int[] best = null;
        for (int i = 0; i < all.size(); i++) {
            int[] m = all.get(i);
            int dx = m[0] - cx;
            int dy = m[1] - cy;
            int dz = m[2] - cz;
            int distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = m;
            }
        }
        return best;
    }

    /**
     * Collect all whitelisted GT processing machines within radius (same cube scan as
     * {@link #findNearestMachine}). Caller should throttle.
     *
     * @return mutable list of int[]{x,y,z}; empty if none (never null)
     */
    public static List<int[]> findMachines(World world, int cx, int cy, int cz, int radius) {
        if (world == null || radius <= 0) {
            return new ArrayList<int[]>();
        }
        return getCachedOrScan(world, cx, cy, cz, radius, false);
    }

    private static List<int[]> scanProcessingMachines(World world, int cx, int cy, int cz, int radius) {
        List<int[]> found = new ArrayList<int[]>();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - 2; y <= cy + 4; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if (!world.blockExists(x, y, z)) {
                        continue;
                    }
                    if (!isGregTechMachine(world, x, y, z)) {
                        continue;
                    }
                    found.add(new int[] { x, y, z });
                }
            }
        }
        return found;
    }

    /**
     * Pick a random machine from {@link #findMachines}, optionally excluding one xyz.
     *
     * @param excludeXyz int[]{x,y,z} to skip, or null to allow any
     * @return int[]{x,y,z} or null if none available after exclusion
     */
    public static int[] findRandomMachine(World world, int cx, int cy, int cz, int radius, int[] excludeXyz,
        Random rand) {
        List<int[]> all = findMachines(world, cx, cy, cz, radius);
        if (all.isEmpty()) {
            return null;
        }
        if (excludeXyz != null && excludeXyz.length >= 3) {
            List<int[]> others = new ArrayList<int[]>(all.size());
            for (int i = 0; i < all.size(); i++) {
                int[] m = all.get(i);
                if (m[0] != excludeXyz[0] || m[1] != excludeXyz[1] || m[2] != excludeXyz[2]) {
                    others.add(m);
                }
            }
            if (!others.isEmpty()) {
                all = others;
            }
            // If only the excluded machine exists, keep it rather than returning null
        }
        if (rand == null) {
            return all.get(0);
        }
        return all.get(rand.nextInt(all.size()));
    }

    /** Supervisor whitelist (processing + generators). */
    public static boolean isSupervisorMachine(World world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        Block block = world.getBlock(x, y, z);
        if (!isMachinesBlock(block)) {
            return false;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null || isPipeTileEntity(te)) {
            return false;
        }
        if (!GtReflect.AVAILABLE || GtReflect.IGTE_CLASS == null) {
            return false;
        }
        if (!GtReflect.IGTE_CLASS.isInstance(te)) {
            return false;
        }
        try {
            Object can = GtReflect.CAN_ACCESS_DATA.invoke(te);
            if (!(can instanceof Boolean) || !((Boolean) can).booleanValue()) {
                return false;
            }
            Object mte = GtReflect.GET_META_TILE_ENTITY.invoke(te);
            if (mte == null) {
                return false;
            }
            Object idObj = GtReflect.GET_META_TILE_ID.invoke(te);
            if (!(idObj instanceof Integer)) {
                return false;
            }
            final int metaTileId = ((Integer) idObj).intValue();
            if (ProcessingMachineIds.isPipeOrFrameId(metaTileId)) {
                return false;
            }
            return SupervisorMachineIds.contains(metaTileId);
        } catch (Throwable t) {
            return false;
        }
    }

    public static List<int[]> findSupervisorMachines(World world, int cx, int cy, int cz, int radius) {
        if (world == null || radius <= 0) {
            return new ArrayList<int[]>();
        }
        return getCachedOrScan(world, cx, cy, cz, radius, true);
    }

    private static List<int[]> scanSupervisorMachines(World world, int cx, int cy, int cz, int radius) {
        List<int[]> found = new ArrayList<int[]>();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - 2; y <= cy + 4; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if (!world.blockExists(x, y, z)) {
                        continue;
                    }
                    if (!isSupervisorMachine(world, x, y, z)) {
                        continue;
                    }
                    found.add(new int[] { x, y, z });
                }
            }
        }
        return found;
    }

    public static int[] findRandomSupervisorMachine(World world, int cx, int cy, int cz, int radius, int[] excludeXyz,
        Random rand) {
        List<int[]> all = findSupervisorMachines(world, cx, cy, cz, radius);
        if (all.isEmpty()) {
            return null;
        }
        if (excludeXyz != null && excludeXyz.length >= 3) {
            List<int[]> others = new ArrayList<int[]>(all.size());
            for (int i = 0; i < all.size(); i++) {
                int[] m = all.get(i);
                if (m[0] != excludeXyz[0] || m[1] != excludeXyz[1] || m[2] != excludeXyz[2]) {
                    others.add(m);
                }
            }
            if (!others.isEmpty()) {
                all = others;
            }
        }
        if (rand == null) {
            return all.get(0);
        }
        return all.get(rand.nextInt(all.size()));
    }

}
