package com.angelika.lockerworker.util;

import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Shared NBT keys linking a locker TE ↔ worker-bed item/block by durable UUID.
 */
public final class LockerLink {

    public static final String NBT_LOCKER_ID_MOST = "LockerIdMost";
    public static final String NBT_LOCKER_ID_LEAST = "LockerIdLeast";
    public static final String NBT_HINT_X = "LockerHintX";
    public static final String NBT_HINT_Y = "LockerHintY";
    public static final String NBT_HINT_Z = "LockerHintZ";
    public static final String NBT_HINT_DIM = "LockerHintDim";

    private LockerLink() {}

    public static void writeId(NBTTagCompound tag, UUID id) {
        if (tag == null || id == null) {
            return;
        }
        tag.setLong(NBT_LOCKER_ID_MOST, id.getMostSignificantBits());
        tag.setLong(NBT_LOCKER_ID_LEAST, id.getLeastSignificantBits());
    }

    public static UUID readId(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(NBT_LOCKER_ID_MOST) || !tag.hasKey(NBT_LOCKER_ID_LEAST)) {
            return null;
        }
        return new UUID(tag.getLong(NBT_LOCKER_ID_MOST), tag.getLong(NBT_LOCKER_ID_LEAST));
    }

    public static UUID readIdFromStack(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }
        return readId(stack.getTagCompound());
    }

    public static void writeHint(NBTTagCompound tag, int x, int y, int z, int dim) {
        if (tag == null) {
            return;
        }
        tag.setInteger(NBT_HINT_X, x);
        tag.setInteger(NBT_HINT_Y, y);
        tag.setInteger(NBT_HINT_Z, z);
        tag.setInteger(NBT_HINT_DIM, dim);
    }

    public static boolean hasHint(NBTTagCompound tag) {
        return tag != null && tag.hasKey(NBT_HINT_X) && tag.hasKey(NBT_HINT_Y) && tag.hasKey(NBT_HINT_Z);
    }

    /** Player-facing locker ID line (matches bed-item UUID string). */
    public static String formatChatId(UUID id) {
        if (id == null) {
            return "Locker ID: (none)";
        }
        return "Locker ID: " + id.toString();
    }
}
