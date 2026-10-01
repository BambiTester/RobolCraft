package com.angelika.lockerworker.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;

import com.angelika.lockerworker.network.PacketHandler;
import com.angelika.lockerworker.network.PacketJourneyMapWaypoint;
import com.angelika.lockerworker.network.PacketLookAtBlock;
import com.angelika.lockerworker.util.SupervisorReportMemory;

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
        return "/robolcraft <lookat|jmwp> ...";
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
        throw new WrongUsageException(getCommandUsage(sender));
    }
}
