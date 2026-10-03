package com.angelika.robolcraft.sound;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISoundEventAccessor;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundEventAccessorComposite;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundPoolEntry;
import net.minecraft.client.audio.SoundRegistry;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.sound.SoundLoadEvent;
import net.minecraftforge.common.MinecraftForge;

import com.angelika.robolcraft.RobolCraftMod;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client auto-registration for jar-baked Vorbis clips.
 *
 * <p>
 * Forge 1.7.10 normally requires {@code sounds.json}. {@link SoundLoadEvent} fires
 * before the sound registry is cleared and rebuilt from resource packs, so we defer
 * registration to the next client tick and inject any missing events discovered by
 * {@link ModSounds} into {@link SoundRegistry}. Ogg bytes stay in the mod jar under
 * {@code assets/robolcraft/sounds/...} — no config drop folders.
 * Categories include all folders discovered by {@link ModSounds#discover()}
 * (v12+ locker_sound; v13 bed/afterwork; v20 waiting_for_bed).
 */
@SideOnly(Side.CLIENT)
public final class SoundAutoRegister {

    public static final SoundAutoRegister INSTANCE = new SoundAutoRegister();

    private boolean pendingRegister;
    private boolean busesRegistered;

    private SoundAutoRegister() {}

    /** Call once from client preInit after {@link ModSounds#discover()}. */
    public static void setup() {
        INSTANCE.ensureBuses();
        INSTANCE.pendingRegister = true;
        RobolCraftMod.LOG.info("SoundAutoRegister armed (jar-baked oggs → SoundRegistry)");
    }

    private void ensureBuses() {
        if (busesRegistered) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        busesRegistered = true;
    }

    @SubscribeEvent
    public void onSoundLoad(SoundLoadEvent event) {
        // Registry is cleared AFTER this event returns; re-discover + register next tick.
        ModSounds.discover();
        pendingRegister = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!pendingRegister || event.phase != TickEvent.Phase.END) {
            return;
        }
        pendingRegister = false;
        registerDiscovered();
    }

    private void registerDiscovered() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getSoundHandler() == null) {
            pendingRegister = true;
            return;
        }
        SoundHandler handler = mc.getSoundHandler();
        SoundRegistry registry = getSoundRegistry(handler);
        if (registry == null) {
            RobolCraftMod.LOG
                .warn("SoundAutoRegister: could not access SoundRegistry; jar sounds may need sounds.json");
            return;
        }

        List<String[]> clips = ModSounds.discoveredClips();
        int added = 0;
        int skipped = 0;
        for (String[] clip : clips) {
            String category = clip[0];
            String basename = clip[1];
            ResourceLocation eventLoc = new ResourceLocation(RobolCraftMod.MODID, category + "." + basename);
            ResourceLocation oggLoc = new ResourceLocation(
                RobolCraftMod.MODID,
                ModSounds.toOggResourcePath(category, basename));
            if (registerOne(handler, registry, eventLoc, oggLoc)) {
                added++;
            } else {
                skipped++;
            }
        }
        List<AddonSounds.Clip> addonClips = AddonSounds.registeredClips();
        for (AddonSounds.Clip clip : addonClips) {
            ResourceLocation eventLoc = new ResourceLocation(clip.playName);
            if (registerOne(handler, registry, eventLoc, clip.ogg)) {
                added++;
            } else {
                skipped++;
            }
        }
        RobolCraftMod.LOG.info(
            "SoundAutoRegister: registered {} new sound(s), {} already present (jar {}, addon {})",
            added,
            skipped,
            clips.size(),
            addonClips.size());
    }

    /** @return {@code true} if a new event was registered */
    private static boolean registerOne(SoundHandler handler, SoundRegistry registry, ResourceLocation eventLoc,
        ResourceLocation oggLoc) {
        if (handler.getSound(eventLoc) != null) {
            return false;
        }
        try {
            SoundEventAccessorComposite composite = new SoundEventAccessorComposite(
                eventLoc,
                1.0D,
                1.0D,
                SoundCategory.ANIMALS);
            composite.addSoundToEventPool(makeFileAccessor(oggLoc));
            registry.registerSound(composite);
            return true;
        } catch (Throwable t) {
            RobolCraftMod.LOG.warn("SoundAutoRegister failed for {}: {}", eventLoc, t.toString());
            return false;
        }
    }

    /** Ask the next client tick to register again, after addon contributions have frozen. */
    public static void armAfterAddons() {
        INSTANCE.ensureBuses();
        INSTANCE.pendingRegister = true;
    }

    /**
     * Prefer constructing package-private {@code SoundEventAccessor}; fall back to a
     * tiny {@link ISoundEventAccessor} that returns a fixed {@link SoundPoolEntry}.
     */
    private static ISoundEventAccessor makeFileAccessor(final ResourceLocation oggLoc) throws Exception {
        try {
            Constructor<?> ctor = Class.forName("net.minecraft.client.audio.SoundEventAccessor")
                .getDeclaredConstructor(SoundPoolEntry.class, int.class);
            ctor.setAccessible(true);
            SoundPoolEntry entry = new SoundPoolEntry(oggLoc, 1.0D, 1.0D, false);
            return (ISoundEventAccessor) ctor.newInstance(entry, Integer.valueOf(1));
        } catch (Throwable ignored) {
            return new ISoundEventAccessor() {

                @Override
                public int func_148721_a() {
                    return 1;
                }

                @Override
                public SoundPoolEntry func_148720_g() {
                    return new SoundPoolEntry(oggLoc, 1.0D, 1.0D, false);
                }
            };
        }
    }

    private static SoundRegistry getSoundRegistry(SoundHandler handler) {
        try {
            Field field = SoundHandler.class.getDeclaredField("sndRegistry");
            field.setAccessible(true);
            return (SoundRegistry) field.get(handler);
        } catch (Throwable t) {
            // Obfuscated field name fallbacks used by some mappings
            String[] alts = { "field_147697_e", "e" };
            for (String name : alts) {
                try {
                    Field field = SoundHandler.class.getDeclaredField(name);
                    field.setAccessible(true);
                    Object val = field.get(handler);
                    if (val instanceof SoundRegistry) {
                        return (SoundRegistry) val;
                    }
                } catch (Throwable ignored) {
                    // try next
                }
            }
            RobolCraftMod.LOG.warn("SoundAutoRegister reflection failed: {}", t.toString());
            return null;
        }
    }
}
