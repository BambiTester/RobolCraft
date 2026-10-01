package com.angelika.lockerworker.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.util.MathHelper;

import com.angelika.lockerworker.client.BlockHighlightClient;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * Server → client: snap camera toward the reported machine (aim raised +1 Y) and
 * show a red wireframe outline for 10 seconds.
 */
public class PacketLookAtBlock implements IMessage {

    private int x;
    private int y;
    private int z;

    public PacketLookAtBlock() {}

    public PacketLookAtBlock(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
    }

    public static class Handler implements IMessageHandler<PacketLookAtBlock, IMessage> {

        @Override
        public IMessage onMessage(PacketLookAtBlock message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            applyClient(message.x, message.y, message.z);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void applyClient(int bx, int by, int bz) {
            EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            if (player == null) {
                return;
            }
            // Vanilla camera eye height
            double eyeX = player.posX;
            double eyeY = player.posY + player.getEyeHeight();
            double eyeZ = player.posZ;
            // Aim at reported machine block center
            double tx = bx + 0.5D;
            double ty = by + 0.5D;
            double tz = bz + 0.5D;
            double dx = tx - eyeX;
            double dy = ty - eyeY;
            double dz = tz - eyeZ;
            double horiz = MathHelper.sqrt_double(dx * dx + dz * dz);
            float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            float pitch = (float) (-(Math.atan2(dy, horiz) * 180.0D / Math.PI));
            player.rotationYaw = yaw;
            player.prevRotationYaw = yaw;
            player.rotationYawHead = yaw;
            player.prevRotationYawHead = yaw;
            player.rotationPitch = pitch;
            player.prevRotationPitch = pitch;

            // Outline the reported machine block for 10s
            BlockHighlightClient.highlight(bx, by, bz, 200);
        }
    }
}
