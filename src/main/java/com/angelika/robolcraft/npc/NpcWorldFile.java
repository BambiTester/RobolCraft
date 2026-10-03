package com.angelika.robolcraft.npc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import com.angelika.robolcraft.RobolCraftMod;
import com.angelika.robolcraft.api.IRobolCraftAddon;
import com.angelika.robolcraft.api.RobolCraftAPI;
import com.angelika.robolcraft.entity.EntityRobolCraft;
import com.angelika.robolcraft.tileentity.TileEntityLocker;
import com.angelika.robolcraft.util.ShortIdRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Per-dimension {@code data/robolcraft.json}. Empty objects mean base defaults. The game is the
 * only writer.
 */
public final class NpcWorldFile {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static final JsonParser PARSER = new JsonParser();
    private static final Map<Integer, NpcWorldFile> OPEN = new HashMap<Integer, NpcWorldFile>();
    private static final Map<String, String> ROLE_KEYS = new HashMap<String, String>();
    private static final Set<String> LOGGED_KEYS = new HashSet<String>();
    private static boolean eventsRegistered;

    static {
        ROLE_KEYS.put("worker", "workers");
        ROLE_KEYS.put("supervisor", "supervisors");
    }

    private final World world;
    private final File file;
    private JsonObject root;
    private boolean dirty;

    private NpcWorldFile(World world, File file, JsonObject root) {
        this.world = world;
        this.file = file;
        this.root = root;
    }

    public static void registerRole(String role, String fileKey) {
        if (role == null || fileKey == null || role.length() == 0 || fileKey.length() == 0) {
            return;
        }
        if ("global".equals(role)) {
            return;
        }
        ROLE_KEYS.put(role, fileKey);
    }

    public static boolean knownRole(String role) {
        return role != null && ROLE_KEYS.containsKey(role);
    }

    public static void registerEvents() {
        if (eventsRegistered) {
            return;
        }
        MinecraftForge.EVENT_BUS.register(new WorldHooks());
        eventsRegistered = true;
    }

    public static NpcWorldFile get(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        int dim = world.provider.dimensionId;
        NpcWorldFile open = OPEN.get(Integer.valueOf(dim));
        if (open != null) {
            return open;
        }
        File file = fileFor(world);
        if (file == null) {
            return null;
        }
        JsonObject root = read(file);
        boolean migrated = migrateAll(root);
        open = new NpcWorldFile(world, file, root);
        OPEN.put(Integer.valueOf(dim), open);
        if (migrated || !file.isFile()) {
            open.dirty = true;
            open.save();
        }
        return open;
    }

    public static void ensureId(World world, String role, int id) {
        NpcWorldFile file = get(world);
        if (file == null || id < 1) {
            return;
        }
        JsonObject section = file.section(role, true);
        if (section == null) {
            return;
        }
        String key = Integer.toString(id);
        if (!section.has(key)) {
            section.add(key, new JsonObject());
            file.dirty = true;
            file.save();
        }
    }

    public static void removeId(World world, String role, int id) {
        NpcWorldFile file = get(world);
        if (file == null || id < 1) {
            return;
        }
        JsonObject section = file.section(role, false);
        if (section == null) {
            return;
        }
        String key = Integer.toString(id);
        if (section.has(key)) {
            section.remove(key);
            file.dirty = true;
            file.save();
        }
    }

    /** {@code id < 1} edits {@code global}. Unknown ids are rejected. */
    public static boolean setSlotEnabled(World world, String role, int id, String slot, boolean enabled) {
        JsonObject slotObj = editSlot(world, role, id, slot);
        if (slotObj == null) {
            return false;
        }
        slotObj.addProperty("enabled", Boolean.valueOf(enabled));
        stampIfAddon(slotObj);
        finishEdit(world, role, id);
        return true;
    }

    public static boolean setSlotBehavior(World world, String role, int id, String slot, String behavior) {
        if (behavior == null || !BehaviorSlots.hasBehavior(behavior)) {
            RobolCraftMod.LOG.warn("Unknown behavior '{}'.", behavior);
            return false;
        }
        JsonObject slotObj = editSlot(world, role, id, slot);
        if (slotObj == null) {
            return false;
        }
        slotObj.addProperty("behavior", behavior);
        stampIfAddon(slotObj);
        finishEdit(world, role, id);
        return true;
    }

    public static boolean setSlotParameter(World world, String role, int id, String slot, String key, String value) {
        if (key == null || "enabled".equals(key) || "behavior".equals(key) || "by".equals(key) || "v".equals(key)) {
            return false;
        }
        JsonObject slotObj = editSlot(world, role, id, slot);
        if (slotObj == null) {
            return false;
        }
        slotObj.add(key, parseValue(value));
        stampIfAddon(slotObj);
        finishEdit(world, role, id);
        return true;
    }

    public static boolean setSound(World world, String role, int id, String category, String value) {
        JsonObject sounds = editChild(world, role, id, "sounds");
        if (sounds == null || category == null) {
            return false;
        }
        if (value == null) {
            sounds.remove(category);
        } else if ("off".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            sounds.addProperty(category, Boolean.FALSE);
        } else if ("on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)) {
            sounds.addProperty(category, Boolean.TRUE);
        } else {
            sounds.addProperty(category, value);
        }
        stampIfAddon(sounds);
        finishEdit(world, role, id);
        return true;
    }

    public static boolean setScheduleWindow(World world, String role, int id, String phase, int start, int end) {
        if (!isPhase(phase) || start < 0 || end > 23999 || start > end) {
            return false;
        }
        JsonObject schedule = editChild(world, role, id, "schedule");
        if (schedule == null) {
            return false;
        }
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        arr.add(new com.google.gson.JsonPrimitive(Integer.valueOf(start)));
        arr.add(new com.google.gson.JsonPrimitive(Integer.valueOf(end)));
        schedule.add(phase, arr);
        stampIfAddon(schedule);
        finishEdit(world, role, id);
        return true;
    }

    public static boolean setScheduleSkip(World world, String role, int id, String phase, boolean skip) {
        if (!isPhase(phase)) {
            return false;
        }
        JsonObject schedule = editChild(world, role, id, "schedule");
        if (schedule == null) {
            return false;
        }
        com.google.gson.JsonArray arr = schedule.has("skip") && schedule.get("skip")
            .isJsonArray() ? schedule.getAsJsonArray("skip") : new com.google.gson.JsonArray();
        String name = phase.toLowerCase();
        com.google.gson.JsonArray next = new com.google.gson.JsonArray();
        for (int i = 0; i < arr.size(); i++) {
            if (!name.equals(
                arr.get(i)
                    .getAsString())) {
                next.add(arr.get(i));
            }
        }
        if (skip) {
            next.add(new com.google.gson.JsonPrimitive(name));
        }
        if (next.size() == 0) {
            schedule.remove("skip");
        } else {
            schedule.add("skip", next);
        }
        stampIfAddon(schedule);
        finishEdit(world, role, id);
        return true;
    }

    public static boolean slotEnabled(EntityRobolCraft entity, String slot) {
        JsonObject merged = mergedSlot(entity, slot);
        if (merged != null && merged.has("enabled")
            && merged.get("enabled")
                .isJsonPrimitive()) {
            return merged.get("enabled")
                .getAsBoolean();
        }
        return true;
    }

    public static String slotBehavior(EntityRobolCraft entity, String slot, String fallback) {
        JsonObject merged = mergedSlot(entity, slot);
        if (merged != null && merged.has("behavior")
            && merged.get("behavior")
                .isJsonPrimitive()) {
            return merged.get("behavior")
                .getAsString();
        }
        return fallback;
    }

    public static JsonObject slotParameters(EntityRobolCraft entity, String slot) {
        JsonObject merged = mergedSlot(entity, slot);
        return merged == null ? new JsonObject() : merged;
    }

    public static float numberParam(EntityRobolCraft entity, String slot, String key, float fallback) {
        JsonObject merged = mergedSlot(entity, slot);
        if (merged == null || !merged.has(key)
            || !merged.get(key)
                .isJsonPrimitive()) {
            return fallback;
        }
        try {
            return merged.get(key)
                .getAsFloat();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    /**
     * @return {@code null} inherit, {@code false} mute, {@code true} force on, or a pool name
     */
    public static Object soundChoice(EntityRobolCraft entity, String category) {
        Object own = soundOn(entryFor(entity), category);
        if (own != null) {
            return own;
        }
        return soundOn(globalObject(entity == null ? null : entity.worldObj), category);
    }

    public static int[] scheduleWindow(EntityRobolCraft entity, String phase) {
        int[] own = windowOn(entryFor(entity), phase);
        if (own != null) {
            return own;
        }
        return windowOn(globalObject(entity == null ? null : entity.worldObj), phase);
    }

    public static boolean scheduleSkips(EntityRobolCraft entity, String phase) {
        if (skipsOn(entryFor(entity), phase)) {
            return true;
        }
        return skipsOn(globalObject(entity == null ? null : entity.worldObj), phase);
    }

    public static void logUnknown(String slot, String behavior, String key) {
        if ("enabled".equals(key) || "behavior".equals(key) || "by".equals(key) || "v".equals(key)) {
            return;
        }
        String id = slot + "." + key;
        if (!LOGGED_KEYS.add(id)) {
            return;
        }
        RobolCraftMod.LOG.warn("NPC setting {}.{} is not used by behavior {}.", slot, key, behavior);
    }

    private static Object soundOn(JsonObject entry, String category) {
        if (entry == null || !entry.has("sounds")
            || !entry.get("sounds")
                .isJsonObject()) {
            return null;
        }
        JsonObject sounds = entry.getAsJsonObject("sounds");
        if (!applicable(sounds) || !sounds.has(category)) {
            return null;
        }
        JsonElement value = sounds.get(category);
        if (!value.isJsonPrimitive()) {
            return null;
        }
        if (value.getAsJsonPrimitive()
            .isBoolean()) {
            return Boolean.valueOf(value.getAsBoolean());
        }
        return value.getAsString();
    }

    private static int[] windowOn(JsonObject entry, String phase) {
        if (entry == null || !entry.has("schedule")
            || !entry.get("schedule")
                .isJsonObject()) {
            return null;
        }
        JsonObject schedule = entry.getAsJsonObject("schedule");
        if (!applicable(schedule) || !schedule.has(phase)
            || !schedule.get(phase)
                .isJsonArray()) {
            return null;
        }
        com.google.gson.JsonArray arr = schedule.getAsJsonArray(phase);
        if (arr.size() < 2) {
            return null;
        }
        return new int[] { arr.get(0)
            .getAsInt(),
            arr.get(1)
                .getAsInt() };
    }

    private static boolean skipsOn(JsonObject entry, String phase) {
        if (entry == null || !entry.has("schedule")
            || !entry.get("schedule")
                .isJsonObject()) {
            return false;
        }
        JsonObject schedule = entry.getAsJsonObject("schedule");
        if (!applicable(schedule) || !schedule.has("skip")
            || !schedule.get("skip")
                .isJsonArray()) {
            return false;
        }
        com.google.gson.JsonArray arr = schedule.getAsJsonArray("skip");
        for (int i = 0; i < arr.size(); i++) {
            if (phase.equals(
                arr.get(i)
                    .getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject mergedSlot(EntityRobolCraft entity, String slot) {
        JsonObject merged = new JsonObject();
        copyApplicable(globalObject(entity == null ? null : entity.worldObj), slot, merged);
        copyApplicable(entryFor(entity), slot, merged);
        return merged;
    }

    private static void copyApplicable(JsonObject entry, String slot, JsonObject into) {
        if (entry == null || !entry.has(slot)
            || !entry.get(slot)
                .isJsonObject()) {
            return;
        }
        JsonObject slotObj = entry.getAsJsonObject(slot);
        if (!applicable(slotObj)) {
            return;
        }
        for (Map.Entry<String, JsonElement> field : slotObj.entrySet()) {
            into.add(field.getKey(), field.getValue());
        }
    }

    /** An override tagged with a missing addon is kept in the file and not applied. */
    private static boolean applicable(JsonObject obj) {
        if (obj == null || !obj.has("by")) {
            return true;
        }
        String by = obj.get("by")
            .getAsString();
        return by.length() == 0 || Loader.isModLoaded(by);
    }

    private static JsonObject entryFor(EntityRobolCraft entity) {
        if (entity == null || entity.worldObj == null || entity.worldObj.isRemote) {
            return null;
        }
        TileEntityLocker locker = entity.getHomeLockerTE();
        if (locker == null || locker.getShortId() < 1) {
            return null;
        }
        NpcWorldFile file = get(entity.worldObj);
        if (file == null) {
            return null;
        }
        String role = locker.isShortIdSupervisor() ? "supervisor" : "worker";
        if (!role.equals(entity.getNpcRole()) && knownRole(entity.getNpcRole())) {
            role = entity.getNpcRole();
        }
        JsonObject section = file.section(role, false);
        if (section == null) {
            return null;
        }
        String key = Integer.toString(locker.getShortId());
        if (!section.has(key) || !section.get(key)
            .isJsonObject()) {
            return null;
        }
        return section.getAsJsonObject(key);
    }

    private static JsonObject globalObject(World world) {
        NpcWorldFile file = get(world);
        if (file == null) {
            return null;
        }
        if (!file.root.has("global") || !file.root.get("global")
            .isJsonObject()) {
            return null;
        }
        return file.root.getAsJsonObject("global");
    }

    private static JsonObject editSlot(World world, String role, int id, String slot) {
        if (!BehaviorSlots.knownSlot(role, slot)) {
            RobolCraftMod.LOG.warn("Unknown NPC slot '{}' for role '{}'.", slot, role);
            return null;
        }
        JsonObject parent = editParent(world, role, id);
        if (parent == null) {
            return null;
        }
        if (!parent.has(slot) || !parent.get(slot)
            .isJsonObject()) {
            parent.add(slot, new JsonObject());
        }
        return parent.getAsJsonObject(slot);
    }

    private static JsonObject editChild(World world, String role, int id, String child) {
        JsonObject parent = editParent(world, role, id);
        if (parent == null) {
            return null;
        }
        if (!parent.has(child) || !parent.get(child)
            .isJsonObject()) {
            parent.add(child, new JsonObject());
        }
        return parent.getAsJsonObject(child);
    }

    private static JsonObject editParent(World world, String role, int id) {
        NpcWorldFile file = get(world);
        if (file == null) {
            return null;
        }
        if ("global".equals(role) || id < 1) {
            if (!file.root.has("global") || !file.root.get("global")
                .isJsonObject()) {
                file.root.add("global", new JsonObject());
            }
            return file.root.getAsJsonObject("global");
        }
        if (!idExists(world, role, id)) {
            RobolCraftMod.LOG.warn("No {} {} in this dimension.", role, Integer.valueOf(id));
            return null;
        }
        JsonObject section = file.section(role, true);
        if (section == null) {
            return null;
        }
        String key = Integer.toString(id);
        if (!section.has(key) || !section.get(key)
            .isJsonObject()) {
            section.add(key, new JsonObject());
        }
        return section.getAsJsonObject(key);
    }

    private static boolean idExists(World world, String role, int id) {
        ShortIdRegistry reg = ShortIdRegistry.get(world);
        if (reg == null) {
            return false;
        }
        if ("supervisor".equals(role)) {
            return reg.isSupervisorUsed(id);
        }
        if ("worker".equals(role)) {
            return reg.isWorkerUsed(id);
        }
        NpcWorldFile file = get(world);
        if (file == null) {
            return false;
        }
        JsonObject section = file.section(role, false);
        return section != null && section.has(Integer.toString(id));
    }

    private JsonObject section(String role, boolean create) {
        String key = ROLE_KEYS.get(role);
        if (key == null) {
            return null;
        }
        if (!root.has(key) || !root.get(key)
            .isJsonObject()) {
            if (!create) {
                return null;
            }
            root.add(key, new JsonObject());
        }
        return root.getAsJsonObject(key);
    }

    private static void finishEdit(World world, String role, int id) {
        NpcWorldFile file = get(world);
        if (file == null) {
            return;
        }
        file.dirty = true;
        file.save();
        refreshLoaded(world, role, id);
    }

    @SuppressWarnings("unchecked")
    private static void refreshLoaded(World world, String role, int id) {
        if (world == null) {
            return;
        }
        if ("global".equals(role) || id < 1) {
            for (Object obj : world.loadedEntityList) {
                if (obj instanceof EntityRobolCraft) {
                    ((EntityRobolCraft) obj).rebuildBehaviorTasks();
                }
            }
            return;
        }
        for (Object obj : world.loadedTileEntityList) {
            if (!(obj instanceof TileEntityLocker)) {
                continue;
            }
            TileEntityLocker locker = (TileEntityLocker) obj;
            if (locker.getShortId() != id) {
                continue;
            }
            boolean supervisor = locker.isShortIdSupervisor();
            if ("supervisor".equals(role) && !supervisor) {
                continue;
            }
            if ("worker".equals(role) && supervisor) {
                continue;
            }
            EntityRobolCraft worker = locker.findWorkerPublic();
            if (worker != null) {
                worker.rebuildBehaviorTasks();
            }
        }
    }

    private static void stampIfAddon(JsonObject obj) {
        ModContainer active = Loader.instance()
            .activeModContainer();
        if (active == null || RobolCraftMod.MODID.equals(active.getModId())) {
            return;
        }
        if (!RobolCraftAPI.isRegistered(active.getModId())) {
            return;
        }
        obj.addProperty("by", active.getModId());
        obj.addProperty("v", active.getVersion());
    }

    private static JsonElement parseValue(String value) {
        if (value == null) {
            return com.google.gson.JsonNull.INSTANCE;
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return new com.google.gson.JsonPrimitive(Boolean.valueOf(Boolean.parseBoolean(value)));
        }
        try {
            if (value.indexOf('.') >= 0) {
                return new com.google.gson.JsonPrimitive(Double.valueOf(value));
            }
            return new com.google.gson.JsonPrimitive(Integer.valueOf(value));
        } catch (NumberFormatException ignored) {
            return new com.google.gson.JsonPrimitive(value);
        }
    }

    private static boolean isPhase(String phase) {
        return "work".equals(phase) || "break".equals(phase) || "locker".equals(phase);
    }

    private void save() {
        if (!dirty || file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            RobolCraftMod.LOG.warn("Could not create {}", parent);
            return;
        }
        File tmp = new File(parent, file.getName() + ".tmp");
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8);
            GSON.toJson(root, writer);
            writer.close();
            writer = null;
            if (file.exists() && !file.delete()) {
                RobolCraftMod.LOG.warn("Could not replace {}", file);
                return;
            }
            if (!tmp.renameTo(file)) {
                RobolCraftMod.LOG.warn("Could not rename NPC settings into {}", file);
                return;
            }
            dirty = false;
        } catch (Throwable t) {
            RobolCraftMod.LOG.warn("Failed to save {}: {}", file, t.toString());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable ignored) {
                    // already failing
                }
            }
        }
    }

    private static JsonObject read(File file) {
        if (file == null || !file.isFile()) {
            return emptyRoot();
        }
        InputStreamReader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            JsonElement parsed = PARSER.parse(reader);
            if (parsed != null && parsed.isJsonObject()) {
                JsonObject root = parsed.getAsJsonObject();
                if (!root.has("global") || !root.get("global")
                    .isJsonObject()) {
                    root.add("global", new JsonObject());
                }
                if (!root.has("workers") || !root.get("workers")
                    .isJsonObject()) {
                    root.add("workers", new JsonObject());
                }
                if (!root.has("supervisors") || !root.get("supervisors")
                    .isJsonObject()) {
                    root.add("supervisors", new JsonObject());
                }
                return root;
            }
        } catch (Throwable t) {
            RobolCraftMod.LOG.warn("Could not read {}: {}", file, t.toString());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                    // ignore
                }
            }
        }
        return emptyRoot();
    }

    private static JsonObject emptyRoot() {
        JsonObject root = new JsonObject();
        root.add("global", new JsonObject());
        root.add("workers", new JsonObject());
        root.add("supervisors", new JsonObject());
        return root;
    }

    private static boolean migrateAll(JsonObject root) {
        boolean changed = false;
        for (Map.Entry<String, JsonElement> section : new ArrayList<Map.Entry<String, JsonElement>>(root.entrySet())) {
            if (!section.getValue()
                .isJsonObject()) {
                continue;
            }
            if ("global".equals(section.getKey())) {
                changed |= migrateEntry(
                    section.getValue()
                        .getAsJsonObject());
                continue;
            }
            JsonObject ids = section.getValue()
                .getAsJsonObject();
            for (Map.Entry<String, JsonElement> id : new ArrayList<Map.Entry<String, JsonElement>>(ids.entrySet())) {
                if (id.getValue()
                    .isJsonObject()) {
                    changed |= migrateEntry(
                        id.getValue()
                            .getAsJsonObject());
                }
            }
        }
        return changed;
    }

    private static boolean migrateEntry(JsonObject entry) {
        boolean changed = false;
        List<String> keys = new ArrayList<String>();
        for (Map.Entry<String, JsonElement> field : entry.entrySet()) {
            keys.add(field.getKey());
        }
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            JsonElement child = entry.get(key);
            if (child == null || !child.isJsonObject()) {
                continue;
            }
            JsonObject obj = child.getAsJsonObject();
            if (!obj.has("by") || !obj.has("v")) {
                continue;
            }
            String by = obj.get("by")
                .getAsString();
            String oldVersion = obj.get("v")
                .getAsString();
            if (!Loader.isModLoaded(by)) {
                continue;
            }
            String current = modVersion(by);
            if (current.equals(oldVersion)) {
                continue;
            }
            IRobolCraftAddon addon = RobolCraftAPI.findAddon(by);
            JsonObject next = addon == null ? obj : addon.migrate(oldVersion, obj);
            if (next == null) {
                entry.remove(key);
                changed = true;
                continue;
            }
            next.addProperty("by", by);
            next.addProperty("v", current);
            entry.add(key, next);
            changed = true;
        }
        return changed;
    }

    private static String modVersion(String modId) {
        ModContainer container = (ModContainer) Loader.instance()
            .getIndexedModList()
            .get(modId);
        if (container == null || container.getVersion() == null) {
            return "";
        }
        return container.getVersion();
    }

    private static File fileFor(World world) {
        try {
            File root = world.getSaveHandler()
                .getWorldDirectory();
            if (root == null) {
                return null;
            }
            if (world.provider.dimensionId != 0) {
                root = new File(root, "DIM" + world.provider.dimensionId);
            }
            return new File(new File(root, "data"), "robolcraft.json");
        } catch (Throwable t) {
            RobolCraftMod.LOG.warn("No save folder for NPC settings: {}", t.toString());
            return null;
        }
    }

    /** Drop cached files when the world unloads. */
    public static final class WorldHooks {

        @SubscribeEvent
        public void onLoad(WorldEvent.Load event) {
            if (event.world != null && !event.world.isRemote) {
                get(event.world);
            }
        }

        @SubscribeEvent
        public void onUnload(WorldEvent.Unload event) {
            if (event.world == null || event.world.isRemote) {
                return;
            }
            NpcWorldFile open = OPEN.remove(Integer.valueOf(event.world.provider.dimensionId));
            if (open != null) {
                open.save();
            }
        }
    }
}
