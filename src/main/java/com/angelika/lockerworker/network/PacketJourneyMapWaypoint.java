package com.angelika.lockerworker.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.angelika.lockerworker.client.JourneyMapCompat;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → client: add a red JourneyMap waypoint when JM 5.2.x is present.
 */
public class PacketJourneyMapWaypoint implements IMessage {

    private int x;
    private int y;
    private int z;
    private int dim;
    private String name;

    public PacketJourneyMapWaypoint() {}

    public PacketJourneyMapWaypoint(int x, int y, int z, int dim, String name) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.name = name != null ? name : "RobolCraft";
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        dim = buf.readInt();
        name = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(dim);
        ByteBufUtils.writeUTF8String(buf, name != null ? name : "RobolCraft");
    }

    public static class Handler implements IMessageHandler<PacketJourneyMapWaypoint, IMessage> {

        @Override
        public IMessage onMessage(PacketJourneyMapWaypoint message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            applyClient(message);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void applyClient(PacketJourneyMapWaypoint message) {
            EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return;
            }
            if (!JourneyMapCompat.isAvailable()) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.GRAY + "JourneyMap not installed."));
                return;
            }
            boolean ok = JourneyMapCompat.addRedWaypoint(message.name, message.x, message.y, message.z, message.dim);
            if (ok) {
                player.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "Waypoint added."));
            } else {
                player.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.GRAY + "Could not add JourneyMap waypoint."));
            }
        }
    }
}
