package com.angelika.robolcraft.sound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import com.angelika.robolcraft.RobolCraftMod;
import com.angelika.robolcraft.api.RobolCraftAPI;
import com.angelika.robolcraft.api.RobolCraftSounds;
import com.angelika.robolcraft.entity.EntityRobolCraft;
import com.angelika.robolcraft.network.PacketHandler;
import com.angelika.robolcraft.network.PacketPlayWorkerClip;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.network.NetworkRegistry;

/**
 * Addon clip store. Survives {@link ModSounds#discover()} clearing the live pools.
 * Global extensions are copied back at the end of every discover.
 */
public final class AddonSounds implements RobolCraftSounds {

    public static final AddonSounds INSTANCE = new AddonSounds();

    private static final int SCOPE_GLOBAL = 0;
    private static final int SCOPE_CUSTOM = 1;
    private static final int SCOPE_PRIVATE = 2;

    private final List<Clip> clips = new ArrayList<Clip>();
    private final List<String> customNames = new ArrayList<String>();

    private AddonSounds() {}

    @Override
    public List<String> baseCategories() {
        String[] names = ModSounds.allCategories();
        List<String> copy = new ArrayList<String>(names.length);
        Collections.addAll(copy, names);
        return copy;
    }

    @Override
    public boolean extendCategory(String category, ResourceLocation ogg) {
        if (!ModSounds.isBaseCategory(category)) {
            RobolCraftMod.LOG
                .warn("extendCategory rejected unknown category '{}'. Use registerCategory for new names.", category);
            return false;
        }
        return add(SCOPE_GLOBAL, category, ogg) != null;
    }

    @Override
    public boolean registerCategory(String category, ResourceLocation ogg) {
        if (category == null || category.length() == 0) {
            return false;
        }
        if (ModSounds.isBaseCategory(category)) {
            RobolCraftMod.LOG.warn(
                "registerCategory rejected '{}': that name belongs to the base worker. Use extendCategory.",
                category);
            return false;
        }
        String safe = sanitize(category);
        if (!safe.equals(category) || ModSounds.isBaseCategory(safe)) {
            RobolCraftMod.LOG
                .warn("registerCategory rejected '{}'. Use lowercase letters, digits, and underscores.", category);
            return false;
        }
        if (add(SCOPE_CUSTOM, safe, ogg) == null) {
            return false;
        }
        if (!customNames.contains(safe)) {
            customNames.add(safe);
        }
        return true;
    }

    @Override
    public String registerPrivateClip(String category, ResourceLocation ogg) {
        if (category == null) {
            return null;
        }
        String safe = ModSounds.isBaseCategory(category) ? category : sanitize(category);
        if (!ModSounds.isBaseCategory(category) && !safe.equals(category)) {
            RobolCraftMod.LOG.warn("registerPrivateClip rejected category '{}'.", category);
            return null;
        }
        Clip clip = add(SCOPE_PRIVATE, safe, ogg);
        return clip == null ? null : clip.playName;
    }

    @Override
    public List<String> clips(String category) {
        List<String> base = ModSounds.basePool(category);
        if (base != null) {
            return Collections.unmodifiableList(base);
        }
        List<String> custom = namesFor(SCOPE_CUSTOM, category);
        if (!custom.isEmpty() || customNames.contains(category)) {
            return Collections.unmodifiableList(custom);
        }
        return Collections.emptyList();
    }

    @Override
    public void play(Entity entity, String category, float volume, float pitch) {
        if (entity == null || entity.worldObj == null || volume <= 0.0F) {
            return;
        }
        List<String> list;
        if (entity instanceof EntityRobolCraft) {
            list = ModSounds.clipsFor((EntityRobolCraft) entity, category);
        } else {
            list = clips(category);
        }
        if (list == null || list.isEmpty()) {
            return;
        }
        String name = list.get(entity.worldObj.rand.nextInt(list.size()));
        if (entity instanceof EntityRobolCraft) {
            if (entity.worldObj.isRemote) {
                RobolCraftMod.proxy.queueWorkerClip(entity.getEntityId(), name, volume, pitch);
                return;
            }
            PacketHandler.INSTANCE.sendToAllAround(
                new PacketPlayWorkerClip(entity.getEntityId(), name, volume, pitch),
                new NetworkRegistry.TargetPoint(
                    entity.worldObj.provider.dimensionId,
                    entity.posX,
                    entity.posY,
                    entity.posZ,
                    64.0D));
            return;
        }
        entity.worldObj.playSoundAtEntity(entity, name, volume, pitch);
    }

    /** Copy stored global extensions into the live base pools. Called at the end of discover. */
    public static void reapply() {
        for (Clip clip : INSTANCE.clips) {
            if (clip.scope == SCOPE_GLOBAL) {
                ModSounds.appendPlayName(clip.category, clip.playName);
            }
        }
    }

    /** Every addon ogg the client sound registry must know about. */
    public static List<Clip> registeredClips() {
        return Collections.unmodifiableList(INSTANCE.clips);
    }

    private Clip add(int scope, String category, ResourceLocation ogg) {
        if (RobolCraftAPI.contributionsFrozen()) {
            RobolCraftMod.LOG
                .warn("Sound contribution for '{}' dropped: RobolCraft postInit already froze addons.", category);
            return null;
        }
        String addonId = loadingAddonId();
        if (addonId == null) {
            return null;
        }
        if (ogg == null || ogg.getResourceDomain() == null || ogg.getResourcePath() == null) {
            RobolCraftMod.LOG.warn("Addon {} sent a null sound location.", addonId);
            return null;
        }
        String base = basename(ogg.getResourcePath());
        String playName = RobolCraftMod.MODID + ":" + category + "." + addonId + "_" + base;
        for (Clip existing : clips) {
            if (playName.equals(existing.playName)) {
                return existing;
            }
        }
        Clip clip = new Clip(scope, addonId, category, playName, ogg);
        clips.add(clip);
        if (scope == SCOPE_GLOBAL) {
            ModSounds.appendPlayName(category, playName);
        }
        RobolCraftMod.LOG.info("Addon {} sound {} -> {}", addonId, playName, ogg);
        return clip;
    }

    private List<String> namesFor(int scope, String category) {
        List<String> out = new ArrayList<String>();
        for (Clip clip : clips) {
            if (clip.scope == scope && category.equals(clip.category)) {
                out.add(clip.playName);
            }
        }
        return out;
    }

    private static String loadingAddonId() {
        ModContainer active = Loader.instance()
            .activeModContainer();
        if (active == null) {
            RobolCraftMod.LOG.warn("Sound contribution ignored: no mod is loading.");
            return null;
        }
        String id = active.getModId();
        if (!RobolCraftAPI.isRegistered(id)) {
            RobolCraftMod.LOG.warn("Mod {} tried to add sounds before RobolCraftAPI.register, or it was refused.", id);
            return null;
        }
        return sanitize(id);
    }

    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toLowerCase(raw.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private static String basename(String path) {
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        if (file.toLowerCase()
            .endsWith(".ogg")) {
            file = file.substring(0, file.length() - 4);
        }
        String safe = sanitize(file);
        return safe.length() == 0 ? "clip" : safe;
    }

    /** One contributed clip. {@code playName} is {@code robolcraft:<category>.<addon>_<file>}. */
    public static final class Clip {

        public final int scope;
        public final String addonId;
        public final String category;
        public final String playName;
        public final ResourceLocation ogg;

        Clip(int scope, String addonId, String category, String playName, ResourceLocation ogg) {
            this.scope = scope;
            this.addonId = addonId;
            this.category = category;
            this.playName = playName;
            this.ogg = ogg;
        }
    }
}
