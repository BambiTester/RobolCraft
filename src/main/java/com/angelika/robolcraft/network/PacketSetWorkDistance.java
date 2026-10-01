package com.angelika.robolcraft.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import com.angelika.robolcraft.inventory.ContainerLocker;
import com.angelika.robolcraft.tileentity.TileEntityLocker;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Client → server: set locker work-range distance (int). Replaces broken enchant-packet
 * encoding ({@code 10000 + dist} truncated to one byte).
 */
public class PacketSetWorkDistance implements IMessage {

    private int windowId;
    private int distance;

    public PacketSetWorkDistance() {}

    public PacketSetWorkDistance(int windowId, int distance) {
        this.windowId = windowId;
        this.distance = distance;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        windowId = buf.readInt();
        distance = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(windowId);
        buf.writeInt(distance);
    }

    public static class Handler implements IMessageHandler<PacketSetWorkDistance, IMessage> {

        @Override
        public IMessage onMessage(PacketSetWorkDistance message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return null;
            }
            Container open = player.openContainer;
            if (open == null || open.windowId != message.windowId) {
                return null;
            }
            if (!(open instanceof ContainerLocker)) {
                return null;
            }
            ContainerLocker lockerContainer = (ContainerLocker) open;
            TileEntityLocker locker = lockerContainer.locker;
            if (locker == null || !locker.isUseableByPlayer(player)) {
                return null;
            }
            locker.setMaxWorkDistance(message.distance);
            return null;
        }
    }
}
