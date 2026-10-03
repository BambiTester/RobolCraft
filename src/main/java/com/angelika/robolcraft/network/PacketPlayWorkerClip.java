package com.angelika.robolcraft.network;

import com.angelika.robolcraft.sound.ClientWorkerSounds;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → client: play one already-chosen worker clip on the exclusive channel.
 */
public class PacketPlayWorkerClip implements IMessage {

    private int entityId;
    private String playName;
    private float volume;
    private float pitch;

    public PacketPlayWorkerClip() {}

    public PacketPlayWorkerClip(int entityId, String playName, float volume, float pitch) {
        this.entityId = entityId;
        this.playName = playName;
        this.volume = volume;
        this.pitch = pitch;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        volume = buf.readFloat();
        pitch = buf.readFloat();
        int len = buf.readUnsignedShort();
        byte[] raw = new byte[len];
        buf.readBytes(raw);
        playName = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeFloat(volume);
        buf.writeFloat(pitch);
        byte[] raw = playName == null ? new byte[0] : playName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(raw.length);
        buf.writeBytes(raw);
    }

    public static class Handler implements IMessageHandler<PacketPlayWorkerClip, IMessage> {

        @Override
        public IMessage onMessage(PacketPlayWorkerClip message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT || message.playName == null || message.playName.length() == 0) {
                return null;
            }
            apply(message.entityId, message.playName, message.volume, message.pitch);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void apply(int entityId, String playName, float volume, float pitch) {
            ClientWorkerSounds.queueNamedClip(entityId, playName, volume, pitch);
        }
    }
}
