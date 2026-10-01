package com.angelika.lockerworker.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Compact locker GUI button. Vanilla {@link GuiButton} uses
 * {@code drawTexturedModalRect} where the draw size == UV size, so a height of
 * 14 only samples the top 14px of the 20px {@code widgets.png} bevel and looks
 * cut off on the bottom/right. This class maps the full 200×20 button strip
 * (left + right halves) onto the actual W×H rect so the gray face fills the
 * hitbox while keeping the v21 compact layout.
 */
@SideOnly(Side.CLIENT)
public class GuiLockerButton extends GuiButton {

    /** Vanilla widgets.png button face height. */
    private static final int TEX_H = 20;
    /** Half of the full-width (200) template used by vanilla left/right split. */
    private static final int TEX_FULL_W = 200;
    public static final float FONT_SCALE = 0.85F;

    public GuiLockerButton(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        FontRenderer fontrenderer = mc.fontRenderer;
        mc.getTextureManager()
            .bindTexture(buttonTextures);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.field_146123_n = mouseX >= this.xPosition && mouseY >= this.yPosition
            && mouseX < this.xPosition + this.width
            && mouseY < this.yPosition + this.height;
        int state = this.getHoverState(this.field_146123_n);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int v = 46 + state * TEX_H;
        int halfW = this.width / 2;
        int remW = this.width - halfW; // absorb odd pixel so right edge meets left
        // Left half: UV u=0..halfW (same as vanilla), full 20px V → stretched to height
        drawStretchedModalRect(this.xPosition, this.yPosition, 0, v, halfW, TEX_H, halfW, this.height);
        // Right half: UV from (200 - remW) so the right bevel edge is included
        drawStretchedModalRect(
            this.xPosition + halfW,
            this.yPosition,
            TEX_FULL_W - remW,
            v,
            remW,
            TEX_H,
            remW,
            this.height);

        this.mouseDragged(mc, mouseX, mouseY);

        int color = 14737632;
        if (packedFGColour != 0) {
            color = packedFGColour;
        } else if (!this.enabled) {
            color = 10526880;
        } else if (this.field_146123_n) {
            color = 16777120;
        }

        GL11.glPushMatrix();
        float cx = this.xPosition + this.width / 2.0F;
        float cy = this.yPosition + this.height / 2.0F;
        GL11.glTranslatef(cx, cy, 0.0F);
        GL11.glScalef(FONT_SCALE, FONT_SCALE, 1.0F);
        this.drawCenteredString(fontrenderer, this.displayString, 0, -4, color);
        GL11.glPopMatrix();
    }

    /**
     * Like {@code drawTexturedModalRect}, but screen size (drawW×drawH) may differ
     * from the UV source size (texW×texH). widgets.png is 256×256.
     */
    private void drawStretchedModalRect(int x, int y, int u, int v, int texW, int texH, int drawW, int drawH) {
        float f = 0.00390625F; // 1/256
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + drawH, this.zLevel, u * f, (v + texH) * f);
        tess.addVertexWithUV(x + drawW, y + drawH, this.zLevel, (u + texW) * f, (v + texH) * f);
        tess.addVertexWithUV(x + drawW, y, this.zLevel, (u + texW) * f, v * f);
        tess.addVertexWithUV(x, y, this.zLevel, u * f, v * f);
        tess.draw();
    }
}
