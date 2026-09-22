package com.angelika.lockerworker;

import com.angelika.lockerworker.client.render.RenderLockerWorker;
import com.angelika.lockerworker.entity.EntityLockerWorker;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-only proxy. Renderer registration is deferred to {@link #init} (not preInit)
 * to avoid early ModelVillager / GL work during BLS splash — suspected contributor to
 * Mac aarch64 Metal AGXG13GFamilyCommandBuffer crashes with Angelica.
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    @SideOnly(Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // Deferred past preInit / splash: register entity renderer here.
        // RenderLockerWorker constructs ModelVillager lazily on first doRender.
        RenderingRegistry.registerEntityRenderingHandler(EntityLockerWorker.class, new RenderLockerWorker());
    }
}
