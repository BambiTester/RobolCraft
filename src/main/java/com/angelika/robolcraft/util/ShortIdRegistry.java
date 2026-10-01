package com.angelika.robolcraft.util;

import java.util.BitSet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

/**
 * Per-world sequential short IDs for lockers. Separate pools for workers and supervisors.
 * Lowest free int is reused after destroy (Forge {@link WorldSavedData}).
 */
public class ShortIdRegistry extends WorldSavedData {

    public static final String DATA_NAME = "robolcraft_short_ids";

    private final BitSet usedWorkers = new BitSet();
    private final BitSet usedSupervisors = new BitSet();

    public ShortIdRegistry(String name) {
        super(name);
    }

    public ShortIdRegistry() {
        super(DATA_NAME);
    }

    public static ShortIdRegistry get(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        MapStorage storage = world.perWorldStorage;
        ShortIdRegistry data = (ShortIdRegistry) storage.loadData(ShortIdRegistry.class, DATA_NAME);
        if (data == null) {
            data = new ShortIdRegistry(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    /** Allocate lowest free positive int (>=1) for a worker locker. */
    public int allocateWorker() {
        int id = usedWorkers.nextClearBit(1);
        usedWorkers.set(id);
        markDirty();
        return id;
    }

    /** Allocate lowest free positive int (>=1) for a supervisor locker. */
    public int allocateSupervisor() {
        int id = usedSupervisors.nextClearBit(1);
        usedSupervisors.set(id);
        markDirty();
        return id;
    }

    public void freeWorker(int id) {
        if (id >= 1) {
            usedWorkers.clear(id);
            markDirty();
        }
    }

    public void freeSupervisor(int id) {
        if (id >= 1) {
            usedSupervisors.clear(id);
            markDirty();
        }
    }

    /** Mark an ID used without allocating (migration of already-assigned numbers). */
    public void markWorkerUsed(int id) {
        if (id >= 1) {
            usedWorkers.set(id);
            markDirty();
        }
    }

    public void markSupervisorUsed(int id) {
        if (id >= 1) {
            usedSupervisors.set(id);
            markDirty();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        usedWorkers.clear();
        usedSupervisors.clear();
        int[] w = tag.getIntArray("UsedWorkers");
        for (int id : w) {
            if (id >= 1) {
                usedWorkers.set(id);
            }
        }
        int[] s = tag.getIntArray("UsedSupervisors");
        for (int id : s) {
            if (id >= 1) {
                usedSupervisors.set(id);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setIntArray("UsedWorkers", bitsToArray(usedWorkers));
        tag.setIntArray("UsedSupervisors", bitsToArray(usedSupervisors));
    }

    private static int[] bitsToArray(BitSet bits) {
        int n = bits.cardinality();
        int[] out = new int[n];
        int i = 0;
        for (int id = bits.nextSetBit(1); id >= 0; id = bits.nextSetBit(id + 1)) {
            out[i++] = id;
        }
        return out;
    }
}
