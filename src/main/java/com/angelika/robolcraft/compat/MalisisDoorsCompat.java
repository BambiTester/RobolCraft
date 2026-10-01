package com.angelika.robolcraft.compat;

import java.lang.reflect.Method;

import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.angelika.robolcraft.RobolCraftMod;

import cpw.mods.fml.common.Loader;

/**
 * Soft MalisisDoors (GTNH) compat. Reflection only — no hard dependency.
 * Opens doors/trapdoors/fence gates/garage via {@code DoorTileEntity.openOrCloseDoor()}.
 */
public final class MalisisDoorsCompat {

    private static boolean probed;
    private static boolean available;

    private static Method getDoor;
    private static Method isOpened;
    private static Method openOrCloseDoor;
    private static Method getDescriptor;
    private static Method hasCode;
    private static Method requireRedstone;

    private MalisisDoorsCompat() {}

    public static void probe() {
        if (probed) {
            return;
        }
        probed = true;
        available = false;
        if (!Loader.isModLoaded("malisisdoors")) {
            RobolCraftMod.LOG.info("MalisisDoors not loaded — door AI skip");
            return;
        }
        try {
            Class<?> doorClass = Class.forName("net.malisis.doors.door.block.Door");
            Class<?> teClass = Class.forName("net.malisis.doors.door.tileentity.DoorTileEntity");
            Class<?> descClass = Class.forName("net.malisis.doors.door.DoorDescriptor");
            getDoor = doorClass.getMethod("getDoor", IBlockAccess.class, int.class, int.class, int.class);
            isOpened = teClass.getMethod("isOpened");
            openOrCloseDoor = teClass.getMethod("openOrCloseDoor");
            getDescriptor = teClass.getMethod("getDescriptor");
            hasCode = descClass.getMethod("hasCode");
            requireRedstone = descClass.getMethod("requireRedstone");
            available = true;
            RobolCraftMod.LOG.info("MalisisDoors door reflection ready");
        } catch (Throwable t) {
            available = false;
            RobolCraftMod.LOG.info("MalisisDoors present but door API unavailable: {}", t.toString());
        }
    }

    public static boolean isAvailable() {
        if (!probed) {
            probe();
        }
        return available;
    }

    /** True if block class is under {@code net.malisis.doors}. */
    public static boolean isMalisisDoorBlock(Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getClass()
            .getName();
        return name != null && name.startsWith("net.malisis.doors");
    }

    /** True if {@link #getDoor} resolves a TE here (usable by open AI). */
    public static boolean hasDoorTe(World world, int x, int y, int z) {
        return getDoorTe(world, x, y, z) != null;
    }

    /**
     * True if a closed, manually operable Malisis door TE is at this position
     * (no digicode / redstone-only). Does not toggle state.
     */
    public static boolean canOpen(World world, int x, int y, int z) {
        if (!isAvailable() || world == null || world.isRemote) {
            return false;
        }
        Object te = getDoorTe(world, x, y, z);
        if (te == null) {
            return false;
        }
        try {
            if (!isManuallyOperable(te)) {
                return false;
            }
            Boolean opened = (Boolean) isOpened.invoke(te);
            return opened == null || !opened.booleanValue();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * @return true if a Malisis door TE was found and opened (was closed and allowed)
     */
    public static boolean tryOpen(World world, int x, int y, int z) {
        return setOpened(world, x, y, z, true);
    }

    /**
     * @return true if a Malisis door TE was found and closed (was open)
     */
    public static boolean tryClose(World world, int x, int y, int z) {
        return setOpened(world, x, y, z, false);
    }

    private static boolean isManuallyOperable(Object te) throws Exception {
        Object desc = getDescriptor.invoke(te);
        if (desc == null) {
            return true;
        }
        Boolean coded = (Boolean) hasCode.invoke(desc);
        if (coded != null && coded.booleanValue()) {
            return false;
        }
        Boolean redstone = (Boolean) requireRedstone.invoke(desc);
        return redstone == null || !redstone.booleanValue();
    }

    private static boolean setOpened(World world, int x, int y, int z, boolean wantOpen) {
        if (!isAvailable() || world == null || world.isRemote) {
            return false;
        }
        Object te = getDoorTe(world, x, y, z);
        if (te == null) {
            return false;
        }
        try {
            if (!isManuallyOperable(te)) {
                return false;
            }
            Boolean opened = (Boolean) isOpened.invoke(te);
            boolean isOpen = opened != null && opened.booleanValue();
            if (isOpen == wantOpen) {
                return false;
            }
            openOrCloseDoor.invoke(te);
            return true;
        } catch (Throwable t) {
            RobolCraftMod.LOG.warn("MalisisDoors open/close failed at {},{},{}: {}", x, y, z, t.toString());
            return false;
        }
    }

    private static Object getDoorTe(World world, int x, int y, int z) {
        if (!isAvailable() || world == null) {
            return null;
        }
        try {
            return getDoor.invoke(null, world, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));
        } catch (Throwable t) {
            return null;
        }
    }
}
