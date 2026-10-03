package com.angelika.robolcraft.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.world.World;

import com.angelika.robolcraft.RobolCraftMod;
import com.angelika.robolcraft.sound.AddonSounds;
import com.angelika.robolcraft.util.MedkitRegistry;
import com.angelika.robolcraft.util.TrashcanRegistry;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.versioning.ArtifactVersion;

/**
 * Entry point for addon jars. Register from addon {@code preInit}. Sound and block contributions
 * stay open through addon {@code init} and freeze at the start of RobolCraft {@code postInit}.
 */
public final class RobolCraftAPI {

    /**
     * Bump only when a method addons are told to call changes or disappears. Adding a method does
     * not bump this. {@code static final} so addon jars inline the value they compiled against.
     */
    public static final int API_VERSION = 1;

    private static final Map<String, IRobolCraftAddon> ADDONS = new LinkedHashMap<String, IRobolCraftAddon>();
    private static final Map<String, Integer> REJECTED = new LinkedHashMap<String, Integer>();

    private static boolean handshakeClosed;
    private static boolean contributionsFrozen;

    private RobolCraftAPI() {}

    public static RobolCraftSounds sounds() {
        return AddonSounds.INSTANCE;
    }

    /**
     * @return {@code false} if the addon was ignored (duplicate, too late, mod id mismatch, or built
     *         for a newer API)
     */
    public static boolean register(IRobolCraftAddon addon) {
        if (addon == null || addon.getModId() == null
            || addon.getModId()
                .length() == 0) {
            RobolCraftMod.LOG.warn("Ignored RobolCraft addon register: missing mod id");
            return false;
        }
        String id = addon.getModId();
        if (handshakeClosed) {
            RobolCraftMod.LOG
                .warn("Addon {} called RobolCraftAPI.register after RobolCraft init. Register from addon preInit.", id);
            return false;
        }
        ModContainer active = Loader.instance()
            .activeModContainer();
        if (active == null || !id.equals(active.getModId())) {
            RobolCraftMod.LOG.warn(
                "Addon register id '{}' does not match the mod currently loading ({}).",
                id,
                active == null ? "none" : active.getModId());
            return false;
        }
        int reported = addon.getApiVersion();
        if (reported > API_VERSION) {
            REJECTED.put(id, Integer.valueOf(reported));
            RobolCraftMod.LOG.warn(
                "Addon {} ({}) needs RobolCraft API {} but this jar is API {}. It was not loaded.",
                addon.getName(),
                id,
                Integer.valueOf(reported),
                Integer.valueOf(API_VERSION));
            return false;
        }
        if (ADDONS.containsKey(id)) {
            RobolCraftMod.LOG.info("Addon {} already registered; ignoring the second call.", id);
            return false;
        }
        ADDONS.put(id, addon);
        RobolCraftMod.LOG.info(
            "RobolCraft addon registered: {} ({}) api {} (this mod is api {})",
            addon.getName(),
            id,
            Integer.valueOf(reported),
            Integer.valueOf(API_VERSION));
        return true;
    }

    public static boolean isRegistered(String modId) {
        return modId != null && ADDONS.containsKey(modId);
    }

    public static IRobolCraftAddon findAddon(String modId) {
        return ADDONS.get(modId);
    }

    public static boolean registerBehavior(String name, BehaviorFactory factory) {
        return com.angelika.robolcraft.npc.BehaviorSlots.register(name, factory);
    }

    public static void registerNpcRole(String role, String fileKey) {
        com.angelika.robolcraft.npc.NpcWorldFile.registerRole(role, fileKey);
    }

    /** {@code id < 1} or role {@code global} edits the global section. */
    public static boolean setNpcEnabled(net.minecraft.world.World world, String role, int id, String slot,
        boolean enabled) {
        return com.angelika.robolcraft.npc.NpcWorldFile.setSlotEnabled(world, role, id, slot, enabled);
    }

    public static boolean setNpcBehavior(net.minecraft.world.World world, String role, int id, String slot,
        String behavior) {
        return com.angelika.robolcraft.npc.NpcWorldFile.setSlotBehavior(world, role, id, slot, behavior);
    }

    public static boolean setNpcParameter(net.minecraft.world.World world, String role, int id, String slot, String key,
        String value) {
        return com.angelika.robolcraft.npc.NpcWorldFile.setSlotParameter(world, role, id, slot, key, value);
    }

    public static boolean setNpcSound(net.minecraft.world.World world, String role, int id, String category,
        String value) {
        return com.angelika.robolcraft.npc.NpcWorldFile.setSound(world, role, id, category, value);
    }

    public static boolean setNpcSchedule(net.minecraft.world.World world, String role, int id, String phase, int start,
        int end) {
        return com.angelika.robolcraft.npc.NpcWorldFile.setScheduleWindow(world, role, id, phase, start, end);
    }

    public static boolean setNpcScheduleSkip(net.minecraft.world.World world, String role, int id, String phase,
        boolean skip) {
        return com.angelika.robolcraft.npc.NpcWorldFile.setScheduleSkip(world, role, id, phase, skip);
    }

    /** True after RobolCraft {@code postInit} starts. Further sound and block contributions are dropped. */
    public static boolean contributionsFrozen() {
        return contributionsFrozen;
    }

    /** Called from RobolCraft {@code init}, after every addon {@code preInit}. */
    public static void closeHandshake() {
        if (handshakeClosed) {
            return;
        }
        handshakeClosed = true;
        for (ModContainer mod : Loader.instance()
            .getActiveModList()) {
            if (mod == null || RobolCraftMod.MODID.equals(mod.getModId())) {
                continue;
            }
            if (!requiresThisMod(mod)) {
                continue;
            }
            if (ADDONS.containsKey(mod.getModId()) || REJECTED.containsKey(mod.getModId())) {
                continue;
            }
            RobolCraftMod.LOG.warn(
                "Mod {} depends on robolcraft but never called RobolCraftAPI.register. Addon hooks will ignore it.",
                mod.getModId());
        }
    }

    /** Called at the start of RobolCraft {@code postInit}. */
    public static void freezeContributions() {
        contributionsFrozen = true;
        RobolCraftMod.LOG.info(
            "RobolCraft addon contributions frozen. {} addon(s), {} rejected.",
            Integer.valueOf(ADDONS.size()),
            Integer.valueOf(REJECTED.size()));
    }

    public static Set<String> registeredModIds() {
        return Collections.unmodifiableSet(ADDONS.keySet());
    }

    /**
     * Mark a block as a medkit the worker heal AI can find. Also call {@link #addMedkit} and
     * {@link #removeMedkit} from the block's place and break. Extending {@code BlockMedkit} does
     * not by itself make a new block visible to the chunk scan.
     */
    public static boolean registerMedkit(Block block) {
        return MedkitRegistry.registerAcceptedBlock(block);
    }

    /** Mark a block as a trashcan the break-time AI can find. Pair with {@link #addTrashcan}. */
    public static boolean registerTrashcan(Block block) {
        return TrashcanRegistry.registerAcceptedBlock(block);
    }

    public static boolean isMedkit(Block block) {
        return MedkitRegistry.accepts(block);
    }

    public static boolean isTrashcan(Block block) {
        return TrashcanRegistry.accepts(block);
    }

    public static void addMedkit(World world, int x, int y, int z) {
        MedkitRegistry reg = MedkitRegistry.get(world);
        if (reg != null) {
            reg.add(x, y, z);
        }
    }

    public static void removeMedkit(World world, int x, int y, int z) {
        MedkitRegistry reg = MedkitRegistry.get(world);
        if (reg != null) {
            reg.remove(x, y, z);
        }
    }

    public static void addTrashcan(World world, int x, int y, int z) {
        TrashcanRegistry reg = TrashcanRegistry.get(world);
        if (reg != null) {
            reg.add(x, y, z);
        }
    }

    public static void removeTrashcan(World world, int x, int y, int z) {
        TrashcanRegistry reg = TrashcanRegistry.get(world);
        if (reg != null) {
            reg.remove(x, y, z);
        }
    }

    private static boolean requiresThisMod(ModContainer mod) {
        try {
            if (labelsContain(mod.getRequirements())) {
                return true;
            }
        } catch (Throwable ignored) {
            // Older containers
        }
        try {
            if (labelsContain(mod.getDependencies())) {
                return true;
            }
        } catch (Throwable ignored) {
            // ignore
        }
        return false;
    }

    private static boolean labelsContain(Iterable<ArtifactVersion> versions) {
        if (versions == null) {
            return false;
        }
        for (ArtifactVersion version : versions) {
            if (version != null && RobolCraftMod.MODID.equals(version.getLabel())) {
                return true;
            }
        }
        return false;
    }
}
