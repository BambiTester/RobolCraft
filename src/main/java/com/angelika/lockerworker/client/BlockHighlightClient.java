package com.angelika.lockerworker.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-only red wireframe block outline (NEI-style Tessellator lines), timed.
 */
@SideOnly(Side.CLIENT)
public final class BlockHighlightClient {

    public static final BlockHighlightClient INSTANCE = new BlockHighlightClient();

    private static int hlX;
    private static int hlY;
    private static int hlZ;
    private static long expireAtMs;
    private static boolean active;

    private BlockHighlightClient() {}

    /**
     * Highlight block {@code (x,y,z)} for {@code durationTicks} (20 ticks ≈ 1s).
     */
    public static void highlight(int x, int y, int z, int durationTicks) {
        hlX = x;
        hlY = y;
        hlZ = z;
        if (durationTicks < 1) {
            durationTicks = 1;
        }
        expireAtMs = System.currentTimeMillis() + durationTicks * 50L;
        active = true;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!active) {
            return;
        }
        if (System.currentTimeMillis() >= expireAtMs) {
            active = false;
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        Entity view = mc.renderViewEntity != null ? mc.renderViewEntity : mc.thePlayer;
        float pt = event.partialTicks;
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;

        // Slight expand to avoid z-fighting with the block face
        double eps = 0.002D;
        double x0 = hlX - eps - vx;
        double y0 = hlY - eps - vy;
        double z0 = hlZ - eps - vz;
        double x1 = hlX + 1.0D + eps - vx;
        double y1 = hlY + 1.0D + eps - vy;
        double z1 = hlZ + 1.0D + eps - vz;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2.5F);
        GL11.glDepthMask(false);
        // Draw through terrain slightly so the contour stays visible
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINES);
        t.setColorRGBA(255, 40, 40, 220);

        // Bottom face
        line(t, x0, y0, z0, x1, y0, z0);
        line(t, x1, y0, z0, x1, y0, z1);
        line(t, x1, y0, z1, x0, y0, z1);
        line(t, x0, y0, z1, x0, y0, z0);
        // Top face
        line(t, x0, y1, z0, x1, y1, z0);
        line(t, x1, y1, z0, x1, y1, z1);
        line(t, x1, y1, z1, x0, y1, z1);
        line(t, x0, y1, z1, x0, y1, z0);
        // Verticals
        line(t, x0, y0, z0, x0, y1, z0);
        line(t, x1, y0, z0, x1, y1, z0);
        line(t, x1, y0, z1, x1, y1, z1);
        line(t, x0, y0, z1, x0, y1, z1);

        t.draw();

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glPopAttrib();
    }

    private static void line(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1) {
        t.addVertex(x0, y0, z0);
        t.addVertex(x1, y1, z1);
    }
}
