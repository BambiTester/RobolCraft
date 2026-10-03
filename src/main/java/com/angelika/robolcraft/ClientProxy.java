package com.angelika.robolcraft;

import net.minecraftforge.common.MinecraftForge;

import com.angelika.robolcraft.client.BlockHighlightClient;
import com.angelika.robolcraft.client.ClientConfig;
import com.angelika.robolcraft.client.ClientGuiHandler;
import com.angelika.robolcraft.client.JourneyMapCompat;
import com.angelika.robolcraft.client.render.RenderRobolCraft;
import com.angelika.robolcraft.client.render.RenderShiftSupervisor;
import com.angelika.robolcraft.entity.EntityRobolCraft;
import com.angelika.robolcraft.entity.EntityShiftSupervisor;
import com.angelika.robolcraft.inventory.GuiHandler;
import com.angelika.robolcraft.sound.ClientWorkerSounds;
import com.angelika.robolcraft.sound.SoundAutoRegister;

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
        // Client-local audio (volume / hear distance) — never required on dedicated server
        ClientConfig.loadOrMigrate(event.getModConfigurationDirectory());
        ClientConfig.migrateFromServerConfigIfNeeded(Config.getConfiguration());
        // Mods → Config save → persist Gui edits then sync static fields (AI next tick)
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        // Jar-baked .ogg auto-register into SoundRegistry (no manual sounds.json)
        SoundAutoRegister.setup();
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected GuiHandler createGuiHandler() {
        return new ClientGuiHandler();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // Deferred past preInit / splash: register entity renderer here.
        // RenderRobolCraft constructs ModelRobolCraft lazily on first doRender.
        RenderingRegistry.registerEntityRenderingHandler(EntityRobolCraft.class, new RenderRobolCraft());
        RenderingRegistry.registerEntityRenderingHandler(EntityShiftSupervisor.class, new RenderShiftSupervisor());
        MinecraftForge.EVENT_BUS.register(BlockHighlightClient.INSTANCE);
        JourneyMapCompat.probe();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void tickWorkerClientSounds(EntityRobolCraft worker) {
        ClientWorkerSounds.tick(worker);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void stopWorkerClientSounds(int entityId) {
        ClientWorkerSounds.stopAndRemove(entityId);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void armAddonSounds() {
        SoundAutoRegister.armAfterAddons();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void queueWorkerClip(int entityId, String playName, float volume, float pitch) {
        ClientWorkerSounds.queueNamedClip(entityId, playName, volume, pitch);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (RobolCraftMod.MODID.equals(event.modID)) {
            // GuiConfig already wrote Property values into the shared Configuration(s).
            // Persist to disk FIRST, then sync statics — never new Configuration(file).
            Config.save();
            Config.syncStaticFromConfig();
            Config.applyToLivingEntities();
            ClientConfig.save();
            ClientConfig.syncStaticFromConfig();
            RobolCraftMod.LOG.info(
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
                    + ", freeRoamSilence="
                    + Config.freeRoamingMinSilenceTicks
                    + "-"
                    + Config.freeRoamingMaxSilenceTicks
                    + ", breaktimeSilence="
                    + Config.breaktimeMinSilenceTicks
                    + "-"
                    + Config.breaktimeMaxSilenceTicks
                    + ", workingSilence="
                    + Config.workingMinSilenceTicks
                    + "-"
                    + Config.workingMaxSilenceTicks
                    + ", clientSoundVolume="
                    + ClientConfig.soundVolume
                    + ", clientHearDistance="
                    + ClientConfig.soundHearDistance
                    + ", supervisorSwitch="
                    + Config.supervisorMachineSwitchInterval
                    + ", reportRadius="
                    + Config.reportPlayerRadius);
        }
    }
}
