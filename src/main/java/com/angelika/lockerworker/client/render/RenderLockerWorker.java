package com.angelika.lockerworker.client.render;

import net.minecraft.client.model.ModelVillager;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;

import com.angelika.lockerworker.LockerWorkerMod;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Villager-model renderer so the 64×64 locker_worker.png UV maps correctly
 * (1.7.10 ModelVillager layout per UV_NOTES — not player/biped skin).
 *
 * <p>
 * ModelVillager is created lazily on first {@link #doRender} — not in the ctor —
 * to avoid early GL/model work during FML init / BLS splash (Mac Metal AGX).
 */
@SideOnly(Side.CLIENT)
public class RenderLockerWorker extends RenderLiving {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        LockerWorkerMod.MODID,
        "textures/entity/locker_worker.png");

    public RenderLockerWorker() {
        // Pass null model; assign mainModel on first render.
        super(null, 0.5F);
    }

    private void ensureModel() {
        if (this.mainModel == null) {
            this.mainModel = new ModelVillager(0.0F);
        }
    }

    @Override
    public void doRender(EntityLiving entity, double x, double y, double z, float yaw, float partialTicks) {
        ensureModel();
        super.doRender(entity, x, y, z, yaw, partialTicks);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return TEXTURE;
    }
}
