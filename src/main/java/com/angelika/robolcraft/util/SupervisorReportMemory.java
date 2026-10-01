package com.angelika.robolcraft.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.event.ClickEvent;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

/**
 * Shift Supervisor report memory: up to 3 machine reports (FIFO) + optional 4th combat slot.
 * Successfully auto-reported entries stay for right-click recall.
 */
public class SupervisorReportMemory {

    public static final int MAX_MACHINE_REPORTS = 3;

    public static final class MachineReport {

        public final String machineName;
        public final int x, y, z;
        public final String phrases;
        public final long worldDayIndex;
        public boolean delivered;

        public MachineReport(String machineName, int x, int y, int z, String phrases, long worldDayIndex) {
            this.machineName = machineName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.phrases = phrases;
            this.worldDayIndex = worldDayIndex;
            this.delivered = false;
        }

        public String ageWording(World world) {
            return SupervisorReportMemory.ageWording(world, worldDayIndex);
        }

        public String toChatLine(int index, World world) {
            return index + ". [" + machineName + "] had a problem: " + phrases + ", " + ageWording(world);
        }

        /**
         * Machine report as clickable chat: problem text + [point me to it] + [show me on the map].
         * No raw coordinates in the visible line.
         */
        public IChatComponent toChatComponent(int index, World world, EnumChatFormatting baseColor) {
            ChatComponentText root = new ChatComponentText("");
            ChatComponentText prefix = new ChatComponentText(toChatLine(index, world) + " ");
            if (baseColor != null) {
                prefix.getChatStyle()
                    .setColor(baseColor);
            }
            root.appendSibling(prefix);

            int dim = world != null && world.provider != null ? world.provider.dimensionId : 0;
            String wpName = encodeWaypointName(machineName + " — " + phrases);

            root.appendSibling(clickableLink("[point me to it]", "/robolcraft lookat " + x + " " + y + " " + z));
            root.appendSibling(new ChatComponentText(" "));
            root.appendSibling(
                clickableLink(
                    "[show me on the map]",
                    "/robolcraft jmwp " + x + " " + y + " " + z + " " + dim + " " + wpName));
            return root;
        }
    }

    private static IChatComponent clickableLink(String label, String command) {
        ChatComponentText link = new ChatComponentText(label);
        ChatStyle style = new ChatStyle();
        style.setColor(EnumChatFormatting.AQUA);
        style.setUnderlined(Boolean.TRUE);
        style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        link.setChatStyle(style);
        return link;
    }

    /** Base64 URL-safe (no padding) for spaces in waypoint names; cap ~80 chars decoded. */
    public static String encodeWaypointName(String raw) {
        if (raw == null) {
            raw = "";
        }
        if (raw.length() > 80) {
            raw = raw.substring(0, 80);
        }
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeWaypointName(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return "RobolCraft";
        }
        try {
            byte[] bytes = Base64.getUrlDecoder()
                .decode(encoded);
            String name = new String(bytes, StandardCharsets.UTF_8);
            if (name.length() > 80) {
                name = name.substring(0, 80);
            }
            return name.isEmpty() ? "RobolCraft" : name;
        } catch (Exception e) {
            return "RobolCraft";
        }
    }

    public enum CombatOutcome {
        DEFEATED,
        DIED_FIGHTING,
        GOT_AWAY,
        DIED_OTHER
    }

    public static final class CombatReport {

        public final String mobName;
        public final CombatOutcome outcome;
        public final long worldDayIndex;
        public boolean delivered;

        public CombatReport(String mobName, CombatOutcome outcome, long worldDayIndex) {
            this.mobName = mobName;
            this.outcome = outcome;
            this.worldDayIndex = worldDayIndex;
            this.delivered = false;
        }

        public String toChatLine(World world) {
            String age = SupervisorReportMemory.ageWording(world, worldDayIndex);
            switch (outcome) {
                case DEFEATED:
                    return "I defeated a " + mobName + " " + age.replace("it happened ", "");
                case DIED_FIGHTING:
                    return "I died fighting a " + mobName + " " + age.replace("it happened ", "");
                case GOT_AWAY:
                    return "A " + mobName + " got away " + age.replace("it happened ", "");
                case DIED_OTHER:
                    return "I died while chasing a " + mobName + " " + age.replace("it happened ", "");
                default:
                    return "I fought a " + mobName + " " + age.replace("it happened ", "");
            }
        }
    }

    private final LinkedList<MachineReport> machines = new LinkedList<MachineReport>();
    private CombatReport combat;

    public static long worldDayIndex(World world) {
        if (world == null) {
            return 0L;
        }
        return world.getWorldTime() / 24000L;
    }

    public static String ageWording(World world, long dayIndex) {
        long now = worldDayIndex(world);
        double daysAgo = (double) (now - dayIndex);
        if (daysAgo < 0.05D) {
            return "it happened today";
        }
        // one decimal from world day index
        double approx = Math.max(0.1D, daysAgo);
        // If same calendar day fraction via worldTime
        if (world != null) {
            long tickDiff = world.getWorldTime() - (dayIndex * 24000L);
            if (tickDiff < 24000L && tickDiff >= 0L) {
                return "it happened today";
            }
            approx = tickDiff / 24000.0D;
            if (approx < 0.1D) {
                return "it happened today";
            }
        }
        return String.format("it happened %.1f days ago", approx);
    }

    /**
     * Record a machine fault. Unique machine per calendar day; FIFO if 4th unique.
     * 
     * @return true if stored (new or refreshed undelivered)
     */
    public boolean recordMachineFault(String machineName, int x, int y, int z, String phrases, World world) {
        long day = worldDayIndex(world);
        for (MachineReport r : machines) {
            if (r.x == x && r.y == y && r.z == z && r.worldDayIndex == day) {
                // already have this machine today — keep first phrases; do not duplicate
                return false;
            }
        }
        machines.addLast(new MachineReport(machineName, x, y, z, phrases, day));
        while (machines.size() > MAX_MACHINE_REPORTS) {
            machines.removeFirst();
        }
        return true;
    }

    public void recordCombat(String mobName, CombatOutcome outcome, World world) {
        combat = new CombatReport(mobName, outcome, worldDayIndex(world));
    }

    public List<MachineReport> getMachineReports() {
        return new ArrayList<MachineReport>(machines);
    }

    public CombatReport getCombatReport() {
        return combat;
    }

    public boolean hasUndelivered() {
        for (MachineReport r : machines) {
            if (!r.delivered) {
                return true;
            }
        }
        return combat != null && !combat.delivered;
    }

    public List<String> formatAllChatLines(World world) {
        List<String> lines = new ArrayList<String>();
        int i = 1;
        for (MachineReport r : machines) {
            lines.add(r.toChatLine(i++, world));
        }
        if (combat != null) {
            lines.add(combat.toChatLine(world));
        }
        return lines;
    }

    public List<String> formatUndeliveredChatLines(World world) {
        List<String> lines = new ArrayList<String>();
        int i = 1;
        for (MachineReport r : machines) {
            if (!r.delivered) {
                lines.add(r.toChatLine(i, world));
            }
            i++;
        }
        if (combat != null && !combat.delivered) {
            lines.add(combat.toChatLine(world));
        }
        return lines;
    }

    public List<IChatComponent> formatAllChatComponents(World world, EnumChatFormatting baseColor) {
        List<IChatComponent> lines = new ArrayList<IChatComponent>();
        int i = 1;
        for (MachineReport r : machines) {
            lines.add(r.toChatComponent(i++, world, baseColor));
        }
        if (combat != null) {
            ChatComponentText combatLine = new ChatComponentText(combat.toChatLine(world));
            if (baseColor != null) {
                combatLine.getChatStyle()
                    .setColor(baseColor);
            }
            lines.add(combatLine);
        }
        return lines;
    }

    public List<IChatComponent> formatUndeliveredChatComponents(World world, EnumChatFormatting baseColor) {
        List<IChatComponent> lines = new ArrayList<IChatComponent>();
        int i = 1;
        for (MachineReport r : machines) {
            if (!r.delivered) {
                lines.add(r.toChatComponent(i, world, baseColor));
            }
            i++;
        }
        if (combat != null && !combat.delivered) {
            ChatComponentText combatLine = new ChatComponentText(combat.toChatLine(world));
            if (baseColor != null) {
                combatLine.getChatStyle()
                    .setColor(baseColor);
            }
            lines.add(combatLine);
        }
        return lines;
    }

    public void markAllDelivered() {
        for (MachineReport r : machines) {
            r.delivered = true;
        }
        if (combat != null) {
            combat.delivered = true;
        }
    }

    /** End of shift: stop chasing today's undelivered set (leave in memory). */
    public void onShiftEnded() {
        // Intentionally keep undelivered flags; AI stops chasing via schedule.
    }

    /** Wipe all reports (locker destroyed). */
    public void clear() {
        // re-read empty
        readFromNBT(new NBTTagCompound());
    }

    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (MachineReport r : machines) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("Name", r.machineName);
            c.setInteger("X", r.x);
            c.setInteger("Y", r.y);
            c.setInteger("Z", r.z);
            c.setString("Phrases", r.phrases);
            c.setLong("Day", r.worldDayIndex);
            c.setBoolean("Delivered", r.delivered);
            list.appendTag(c);
        }
        tag.setTag("SupReports", list);
        if (combat != null) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("Mob", combat.mobName);
            c.setString("Outcome", combat.outcome.name());
            c.setLong("Day", combat.worldDayIndex);
            c.setBoolean("Delivered", combat.delivered);
            tag.setTag("SupCombat", c);
        }
    }

    public void readFromNBT(NBTTagCompound tag) {
        machines.clear();
        combat = null;
        if (tag.hasKey("SupReports")) {
            NBTTagList list = tag.getTagList("SupReports", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound c = list.getCompoundTagAt(i);
                MachineReport r = new MachineReport(
                    c.getString("Name"),
                    c.getInteger("X"),
                    c.getInteger("Y"),
                    c.getInteger("Z"),
                    c.getString("Phrases"),
                    c.getLong("Day"));
                r.delivered = c.getBoolean("Delivered");
                machines.add(r);
            }
        }
        if (tag.hasKey("SupCombat")) {
            NBTTagCompound c = tag.getCompoundTag("SupCombat");
            CombatOutcome out;
            try {
                out = CombatOutcome.valueOf(c.getString("Outcome"));
            } catch (Exception e) {
                out = CombatOutcome.GOT_AWAY;
            }
            combat = new CombatReport(c.getString("Mob"), out, c.getLong("Day"));
            combat.delivered = c.getBoolean("Delivered");
        }
    }
}
