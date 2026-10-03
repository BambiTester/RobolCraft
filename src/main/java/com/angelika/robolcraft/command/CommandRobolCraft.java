package com.angelika.robolcraft.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;

import com.angelika.robolcraft.api.RobolCraftAPI;
import com.angelika.robolcraft.network.PacketHandler;
import com.angelika.robolcraft.network.PacketJourneyMapWaypoint;
import com.angelika.robolcraft.network.PacketLookAtBlock;
import com.angelika.robolcraft.util.SupervisorReportMemory;

/**
 * Chat click-event bridge: {@code /robolcraft lookat|jmwp ...} → client packets.
 */
public class CommandRobolCraft extends CommandBase {

    @Override
    public String getCommandName() {
        return "robolcraft";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/robolcraft <lookat|jmwp|slot> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        // Click events run as the player; allow all (0)
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender instanceof EntityPlayerMP;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        if (args.length < 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        String sub = args[0];
        if ("lookat".equalsIgnoreCase(sub)) {
            if (args.length < 4) {
                throw new WrongUsageException("/robolcraft lookat <x> <y> <z>");
            }
            int x = parseInt(sender, args[1]);
            int y = parseInt(sender, args[2]);
            int z = parseInt(sender, args[3]);
            PacketHandler.INSTANCE.sendTo(new PacketLookAtBlock(x, y, z), player);
            return;
        }
        if ("jmwp".equalsIgnoreCase(sub)) {
            if (args.length < 6) {
                throw new WrongUsageException("/robolcraft jmwp <x> <y> <z> <dim> <nameB64>");
            }
            int x = parseInt(sender, args[1]);
            int y = parseInt(sender, args[2]);
            int z = parseInt(sender, args[3]);
            int dim = parseInt(sender, args[4]);
            String name = SupervisorReportMemory.decodeWaypointName(args[5]);
            PacketHandler.INSTANCE.sendTo(new PacketJourneyMapWaypoint(x, y, z, dim, name), player);
            return;
        }
        if ("slot".equalsIgnoreCase(sub)) {
            if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
                throw new CommandException("commands.generic.permission");
            }
            applySlot(sender, player, args);
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    /**
     * {@code /robolcraft slot global <slot> off|on} or
     * {@code /robolcraft slot worker <id> <slot> off|on|behavior <name>|param <key> <value>} and
     * {@code schedule <phase> <start> <end>} / {@code sounds <category> off|on|<pool>}.
     */
    private void applySlot(ICommandSender sender, EntityPlayerMP player, String[] args) {
        if (args.length < 4) {
            throw new WrongUsageException(
                "/robolcraft slot <global|worker|supervisor> [id] <slot|sounds|schedule> ...");
        }
        String role = args[1].toLowerCase();
        int index = 2;
        int id = 0;
        if (!"global".equals(role)) {
            if (args.length < 5) {
                throw new WrongUsageException("/robolcraft slot <worker|supervisor> <id> <slot> ...");
            }
            id = parseInt(sender, args[2]);
            index = 3;
        }
        String kind = args[index].toLowerCase();
        if ("schedule".equals(kind)) {
            if (args.length < index + 4) {
                throw new WrongUsageException("/robolcraft slot ... schedule <break|work|locker> <start> <end>");
            }
            String phase = args[index + 1].toLowerCase();
            int start = parseInt(sender, args[index + 2]);
            int end = parseInt(sender, args[index + 3]);
            if (!RobolCraftAPI.setNpcSchedule(player.worldObj, role, id, phase, start, end)) {
                throw new CommandException("Could not set that schedule.");
            }
            return;
        }
        if ("sounds".equals(kind)) {
            if (args.length < index + 3) {
                throw new WrongUsageException("/robolcraft slot ... sounds <category> <off|on|pool>");
            }
            if (!RobolCraftAPI.setNpcSound(player.worldObj, role, id, args[index + 1], args[index + 2])) {
                throw new CommandException("Could not set that sound.");
            }
            return;
        }
        if (args.length < index + 2) {
            throw new WrongUsageException("/robolcraft slot ... <slot> <off|on|behavior|param> ...");
        }
        String action = args[index + 1].toLowerCase();
        boolean ok;
        if ("off".equals(action)) {
            ok = RobolCraftAPI.setNpcEnabled(player.worldObj, role, id, kind, false);
        } else if ("on".equals(action)) {
            ok = RobolCraftAPI.setNpcEnabled(player.worldObj, role, id, kind, true);
        } else if ("behavior".equals(action)) {
            if (args.length < index + 3) {
                throw new WrongUsageException("/robolcraft slot ... <slot> behavior <name>");
            }
            ok = RobolCraftAPI.setNpcBehavior(player.worldObj, role, id, kind, args[index + 2]);
        } else if ("param".equals(action)) {
            if (args.length < index + 4) {
                throw new WrongUsageException("/robolcraft slot ... <slot> param <key> <value>");
            }
            ok = RobolCraftAPI.setNpcParameter(player.worldObj, role, id, kind, args[index + 2], args[index + 3]);
        } else {
            throw new WrongUsageException("/robolcraft slot ... <slot> <off|on|behavior|param>");
        }
        if (!ok) {
            throw new CommandException("Could not change that slot.");
        }
    }
}
