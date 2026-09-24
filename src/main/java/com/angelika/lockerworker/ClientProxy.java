package com.angelika.lockerworker;

import com.angelika.lockerworker.client.render.RenderLockerWorker;
import com.angelika.lockerworker.client.render.RenderShiftSupervisor;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.entity.EntityShiftSupervisor;
import com.angelika.lockerworker.sound.ClientWorkerSounds;
import com.angelika.lockerworker.sound.SoundAutoRegister;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
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
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // Mods → Config save → persist Gui edits then sync static fields (AI next tick)
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        // Jar-baked .ogg auto-register into SoundRegistry (no manual sounds.json)
        SoundAutoRegister.setup();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // Deferred past preInit / splash: register entity renderer here.
        // RenderLockerWorker constructs ModelVillager lazily on first doRender.
        RenderingRegistry.registerEntityRenderingHandler(EntityLockerWorker.class, new RenderLockerWorker());
        RenderingRegistry.registerEntityRenderingHandler(EntityShiftSupervisor.class, new RenderShiftSupervisor());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void tickWorkerClientSounds(EntityLockerWorker worker) {
        ClientWorkerSounds.tick(worker);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void stopWorkerClientSounds(int entityId) {
        ClientWorkerSounds.stopAndRemove(entityId);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (LockerWorkerMod.MODID.equals(event.modID)) {
            // GuiConfig already wrote Property values into the shared Configuration.
            // Persist to disk FIRST, then sync statics — never new Configuration(file).
            Config.save();
            Config.syncStaticFromConfig();
            LockerWorkerMod.LOG.info(
                "Saved+synced config: machineScanRadius=" + Config.machineScanRadius
                    + ", maxDistanceFromLocker="
                    + Config.maxDistanceFromLocker
                    + ", machineSwitch="
                    + Config.machineSwitchMinTicks
                    + "-"
                    + Config.machineSwitchMaxTicks
                    + ", walkingSpeed="
                    + Config.walkingSpeed
                    + " (path="
                    + Config.getPathSpeed()
                    + "), aggressiveModeAllowed="
                    + Config.aggressiveModeAllowed
                    + ", soundFreeRoaming="
                    + Config.soundFreeRoamingEnabled
                    + ", soundVolume="
                    + Config.soundVolume
                    + ", soundHearDistance="
                    + Config.soundHearDistance
                    + ", supervisorSwitch="
                    + Config.supervisorMachineSwitchInterval
                    + ", reportRadius="
                    + Config.reportPlayerRadius);
        }
    }
}
