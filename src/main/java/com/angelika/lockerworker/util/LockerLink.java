package com.angelika.lockerworker.util;

import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Shared NBT keys linking a locker TE ↔ worker-bed item/block.
 * Durable UUID remains the link key; short type+number is the player-facing ID.
 */
public final class LockerLink {

    public static final String NBT_LOCKER_ID_MOST = "LockerIdMost";
    public static final String NBT_LOCKER_ID_LEAST = "LockerIdLeast";
    public static final String NBT_HINT_X = "LockerHintX";
    public static final String NBT_HINT_Y = "LockerHintY";
    public static final String NBT_HINT_Z = "LockerHintZ";
    public static final String NBT_HINT_DIM = "LockerHintDim";

    /** 0 = worker, 1 = supervisor */
    public static final String NBT_SHORT_KIND = "LockerShortKind";
    public static final String NBT_SHORT_NUM = "LockerShortNum";

    public static final int KIND_WORKER = 0;
    public static final int KIND_SUPERVISOR = 1;

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

    public static void writeShortId(NBTTagCompound tag, int kind, int num) {
        if (tag == null || num < 1) {
            return;
        }
        tag.setInteger(NBT_SHORT_KIND, kind);
        tag.setInteger(NBT_SHORT_NUM, num);
    }

    public static int readShortKind(NBTTagCompound tag) {
        return tag != null && tag.hasKey(NBT_SHORT_KIND) ? tag.getInteger(NBT_SHORT_KIND) : -1;
    }

    public static int readShortNum(NBTTagCompound tag) {
        return tag != null && tag.hasKey(NBT_SHORT_NUM) ? tag.getInteger(NBT_SHORT_NUM) : -1;
    }

    public static boolean hasShortId(NBTTagCompound tag) {
        return tag != null && tag.hasKey(NBT_SHORT_NUM) && tag.getInteger(NBT_SHORT_NUM) >= 1;
    }

    /** Player-facing short label, e.g. "Worker 3" / "Shift Supervisor 1". */
    public static String formatShortLabel(int kind, int num) {
        String role = kind == KIND_SUPERVISOR ? "Shift Supervisor" : "Worker";
        if (num < 1) {
            return role + " ?";
        }
        return role + " " + num;
    }

    /**
     * Bed item display name. Linked with short ID: "Worker Bed ID 3" /
     * "Shift Supervisor Bed ID 1". Unlinked (or no short num): "Worker Bed".
     */
    public static String formatBedItemDisplayName(int kind, int num) {
        if (num < 1) {
            return "Worker Bed";
        }
        if (kind == KIND_SUPERVISOR) {
            return "Shift Supervisor Bed ID " + num;
        }
        return "Worker Bed ID " + num;
    }

    /**
     * Player-facing locker ID line. Prefers short label from NBT when present;
     * UUID-only legacy falls back to UUID string until TE migrates.
     */
    public static String formatChatId(UUID id) {
        if (id == null) {
            return "Locker ID: (none)";
        }
        return "Locker ID: " + id.toString();
    }

    public static String formatChatId(int kind, int num) {
        if (num < 1) {
            return "Locker ID: (none)";
        }
        return "Locker ID: " + formatShortLabel(kind, num);
    }

    public static String formatChatId(NBTTagCompound tag) {
        if (tag != null && hasShortId(tag)) {
            return formatChatId(readShortKind(tag), readShortNum(tag));
        }
        return formatChatId(readId(tag));
    }
}
