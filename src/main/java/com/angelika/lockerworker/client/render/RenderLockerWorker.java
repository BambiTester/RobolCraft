package com.angelika.lockerworker.client.render;

import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.angelika.lockerworker.LockerWorkerMod;
import com.angelika.lockerworker.client.model.ModelLockerWorker;
import com.angelika.lockerworker.entity.EntityLockerWorker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Villager-model renderer with translucent headwear overlay (UV 32,0).
 * Outfit textures: work / afterwork / pijama. Lying-in-bed rotates like a sleeping player.
 */
@SideOnly(Side.CLIENT)
public class RenderLockerWorker extends RenderLiving {

    private static final ResourceLocation TEX_WORK = new ResourceLocation(
        LockerWorkerMod.MODID,
        "textures/entity/locker_worker.png");
    private static final ResourceLocation TEX_AFTERWORK = new ResourceLocation(
        LockerWorkerMod.MODID,
        "textures/entity/worker_afterwork.png");
    private static final ResourceLocation TEX_PIJAMA = new ResourceLocation(
        LockerWorkerMod.MODID,
        "textures/entity/worker_pijama.png");

    public RenderLockerWorker() {
        super(null, 0.5F);
    }

    private void ensureModel() {
        if (this.mainModel == null) {
            this.mainModel = new ModelLockerWorker(0.0F);
        }
    }

    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partialTicks) {
        ensureModel();
        super.doRender(entity, x, y, z, yaw, partialTicks);
    }

    @Override
    protected void rotateCorpse(EntityLivingBase entity, float p1, float p2, float partialTicks) {
        if (entity instanceof EntityLockerWorker && ((EntityLockerWorker) entity).isLyingInBed()) {
            // Vanilla-player-style bed orientation so head lies on the pillow
            float bedYaw = EntityLockerWorker.bedOrientationDegrees(((EntityLockerWorker) entity).getSleepBedDir());
            GL11.glRotatef(bedYaw, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(90.0F, 0.0F, 0.0F, 1.0F);
            GL11.glRotatef(270.0F, 0.0F, 1.0F, 0.0F);
        } else {
            super.rotateCorpse(entity, p1, p2, partialTicks);
        }
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        if (entity instanceof EntityLockerWorker) {
            byte outfit = ((EntityLockerWorker) entity).getOutfit();
            if (outfit == EntityLockerWorker.OUTFIT_PIJAMA) {
                return TEX_PIJAMA;
            }
            if (outfit == EntityLockerWorker.OUTFIT_AFTERWORK) {
                return TEX_AFTERWORK;
            }
        }
        return TEX_WORK;
    }
}
