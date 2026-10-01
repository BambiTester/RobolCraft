package com.angelika.robolcraft.client.render;

import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.angelika.robolcraft.RobolCraftMod;
import com.angelika.robolcraft.client.model.ModelRobolCraft;
import com.angelika.robolcraft.entity.EntityRobolCraft;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Shift Supervisor renderer with shared ModelRobolCraft (headwear UV 32,0).
 * Work skin = supervisor.png (white hard hat). Afterwork / pijama reuse worker textures.
 */
@SideOnly(Side.CLIENT)
public class RenderShiftSupervisor extends RenderLiving {

    private static final ResourceLocation TEX_WORK = new ResourceLocation(
        RobolCraftMod.MODID,
        "textures/entity/supervisor.png");
    private static final ResourceLocation TEX_AFTERWORK = new ResourceLocation(
        RobolCraftMod.MODID,
        "textures/entity/worker_afterwork.png");
    private static final ResourceLocation TEX_PIJAMA = new ResourceLocation(
        RobolCraftMod.MODID,
        "textures/entity/worker_pijama.png");

    public RenderShiftSupervisor() {
        super(null, 0.5F);
    }

    private void ensureModel() {
        if (this.mainModel == null) {
            this.mainModel = new ModelRobolCraft(0.0F);
        }
    }

    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partialTicks) {
        ensureModel();
        super.doRender(entity, x, y, z, yaw, partialTicks);
    }

    @Override
    protected void rotateCorpse(EntityLivingBase entity, float p1, float p2, float partialTicks) {
        if (entity instanceof EntityRobolCraft && ((EntityRobolCraft) entity).isLyingInBed()) {
            // Vanilla-player-style bed orientation so head lies on the pillow
            float bedYaw = EntityRobolCraft.bedOrientationDegrees(((EntityRobolCraft) entity).getSleepBedDir());
            GL11.glRotatef(bedYaw, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(90.0F, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(270.0F, 0.0F, 1.0F, 0.0F);
        } else {
            super.rotateCorpse(entity, p1, p2, partialTicks);
        }
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        if (entity instanceof EntityRobolCraft) {
            byte outfit = ((EntityRobolCraft) entity).getOutfit();
            if (outfit == EntityRobolCraft.OUTFIT_PIJAMA) {
                return TEX_PIJAMA;
            }
            if (outfit == EntityRobolCraft.OUTFIT_AFTERWORK) {
                return TEX_AFTERWORK;
            }
        }
        return TEX_WORK;
    }
}
