package com.angelika.lockerworker.client.model;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelVillager;
import net.minecraft.entity.Entity;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * ModelVillager body/arms/legs/nose plus a player-style headwear overlay.
 * Headwear samples skin UV (32, 0) — 64x64 overlay half; box matches villager
 * head 8x10x8 with +0.5F inset like bipedHeadwear. Rendered in a translucent
 * pass so helmet stays solid and glass lenses (~30% alpha) see through.
 */
@SideOnly(Side.CLIENT)
public class ModelLockerWorker extends ModelVillager {

    /** Overlay half of the 64x64 skin; textureOffset (32, 0). */
    public ModelRenderer villagerHeadwear;

    public ModelLockerWorker(float scale) {
        super(scale);
        // Same rotation point as villagerHead; not a child so we can blend separately.
        this.villagerHeadwear = (new ModelRenderer(this)).setTextureSize(64, 64);
        this.villagerHeadwear.setRotationPoint(0.0F, 0.0F, 0.0F);
        // EXACT offset wired for Martyna: (32, 0) — front overlay eyes at x=40–47.
        this.villagerHeadwear.setTextureOffset(32, 0)
            .addBox(-4.0F, -10.0F, -4.0F, 8, 10, 8, scale + 0.5F);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch, float scale) {
        this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);

        // Opaque / cutout base (head includes nose child).
        this.villagerHead.render(scale);
        this.villagerBody.render(scale);
        this.rightVillagerLeg.render(scale);
        this.leftVillagerLeg.render(scale);
        this.villagerArms.render(scale);

        renderHeadwearTranslucent(scale);
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch, float scale, Entity entity) {
        super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
        // Keep overlay locked to head (ModelBiped bipedHeadwear pattern).
        this.villagerHeadwear.rotateAngleY = this.villagerHead.rotateAngleY;
        this.villagerHeadwear.rotateAngleX = this.villagerHead.rotateAngleX;
        this.villagerHeadwear.rotationPointX = this.villagerHead.rotationPointX;
        this.villagerHeadwear.rotationPointY = this.villagerHead.rotationPointY;
        this.villagerHeadwear.rotationPointZ = this.villagerHead.rotationPointZ;
    }

    /**
     * Translucent overlay pass for helmet + glasses. Restores GL via PushAttrib.
     * Alpha test lowered so ~30% lens pixels draw; a=0 still discarded.
     */
    private void renderHeadwearTranslucent(float scale) {
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_ENABLE_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            // Default entity alpha test is GREATER 0.1F — would kill soft glass (~0.3).
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.001F);
            this.villagerHeadwear.render(scale);
        } finally {
            GL11.glPopAttrib();
        }
    }
}
