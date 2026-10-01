package com.angelika.lockerworker.sound;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.angelika.lockerworker.LockerWorkerMod;

/**
 * Discovers Vorbis {@code .ogg} files baked into the mod jar under
 * {@code assets/lockerworker/sounds/{category}/}.
 *
 * <p>
 * Categories (v13): free_roaming, working, interaction, breaktime,
 * breaktime_start, breaktime_end, day_start, day_end, smoking, work_exit,
 * locker_sound, changing_clothes, get_into_bed, get_up, afterwork_roaming,
 * waiting_for_bed, fighting, fleeing, healing.
 * Play names are {@code lockerworker:&lt;category&gt;.&lt;basename&gt;}.
 * Empty category → silent (no crash).
 */
public final class ModSounds {

    public static final String CAT_FREE_ROAMING = "free_roaming";
    public static final String CAT_WORKING = "working";
    public static final String CAT_INTERACTION = "interaction";
    public static final String CAT_BREAKTIME = "breaktime";
    public static final String CAT_BREAKTIME_START = "breaktime_start";
    public static final String CAT_BREAKTIME_END = "breaktime_end";
    public static final String CAT_DAY_START = "day_start";
    public static final String CAT_DAY_END = "day_end";
    public static final String CAT_SMOKING = "smoking";
    public static final String CAT_WORK_EXIT = "work_exit";
    public static final String CAT_LOCKER_SOUND = "locker_sound";
    public static final String CAT_CHANGING_CLOTHES = "changing_clothes";
    public static final String CAT_GET_INTO_BED = "get_into_bed";
    public static final String CAT_GET_UP = "get_up";
    public static final String CAT_AFTERWORK_ROAMING = "afterwork_roaming";
    public static final String CAT_WAITING_FOR_BED = "waiting_for_bed";
    public static final String CAT_SUPERVISOR_ASK_REPORT = "supervisor_ask_report";
    public static final String CAT_SUPERVISOR_REPORT = "supervisor_report";
    public static final String CAT_FIGHTING = "fighting";
    public static final String CAT_FLEEING = "fleeing";
    public static final String CAT_HEALING = "healing";

    private static final String[] ALL_CATEGORIES = new String[] { CAT_FREE_ROAMING, CAT_WORKING, CAT_INTERACTION,
        CAT_BREAKTIME, CAT_BREAKTIME_START, CAT_BREAKTIME_END, CAT_DAY_START, CAT_DAY_END, CAT_SMOKING, CAT_WORK_EXIT,
        CAT_LOCKER_SOUND, CAT_CHANGING_CLOTHES, CAT_GET_INTO_BED, CAT_GET_UP, CAT_AFTERWORK_ROAMING,
        CAT_WAITING_FOR_BED, CAT_SUPERVISOR_ASK_REPORT, CAT_SUPERVISOR_REPORT, CAT_FIGHTING, CAT_FLEEING, CAT_HEALING };

    private static final List<String> FREE_ROAMING = new ArrayList<String>();
    private static final List<String> WORKING = new ArrayList<String>();
    private static final List<String> INTERACTION = new ArrayList<String>();
    private static final List<String> BREAKTIME = new ArrayList<String>();
    private static final List<String> BREAKTIME_START = new ArrayList<String>();
    private static final List<String> BREAKTIME_END = new ArrayList<String>();
    private static final List<String> DAY_START = new ArrayList<String>();
    private static final List<String> DAY_END = new ArrayList<String>();
    private static final List<String> SMOKING = new ArrayList<String>();
    private static final List<String> WORK_EXIT = new ArrayList<String>();
    private static final List<String> LOCKER_SOUND = new ArrayList<String>();
    private static final List<String> CHANGING_CLOTHES = new ArrayList<String>();
    private static final List<String> GET_INTO_BED = new ArrayList<String>();
    private static final List<String> GET_UP = new ArrayList<String>();
    private static final List<String> AFTERWORK_ROAMING = new ArrayList<String>();
    private static final List<String> WAITING_FOR_BED = new ArrayList<String>();
    private static final List<String> SUPERVISOR_ASK_REPORT = new ArrayList<String>();
    private static final List<String> SUPERVISOR_REPORT = new ArrayList<String>();
    private static final List<String> FIGHTING = new ArrayList<String>();
    private static final List<String> FLEEING = new ArrayList<String>();
    private static final List<String> HEALING = new ArrayList<String>();

    /** category + basename for each discovered clip (registration helpers). */
    private static final List<String[]> DISCOVERED = new ArrayList<String[]>();

    private ModSounds() {}

    /**
     * Rescan jar classpath assets and rebuild play lists. Safe to call on every
     * sound reload; empty folders stay empty.
     */
    public static void discover() {
        FREE_ROAMING.clear();
        WORKING.clear();
        INTERACTION.clear();
        BREAKTIME.clear();
        BREAKTIME_START.clear();
        BREAKTIME_END.clear();
        DAY_START.clear();
        DAY_END.clear();
        SMOKING.clear();
        WORK_EXIT.clear();
        LOCKER_SOUND.clear();
        CHANGING_CLOTHES.clear();
        GET_INTO_BED.clear();
        GET_UP.clear();
        AFTERWORK_ROAMING.clear();
        WAITING_FOR_BED.clear();
        SUPERVISOR_ASK_REPORT.clear();
        SUPERVISOR_REPORT.clear();
        FIGHTING.clear();
        FLEEING.clear();
        HEALING.clear();
        DISCOVERED.clear();
        scanCategory(CAT_FREE_ROAMING, FREE_ROAMING);
        scanCategory(CAT_WORKING, WORKING);
        scanCategory(CAT_INTERACTION, INTERACTION);
        scanCategory(CAT_BREAKTIME, BREAKTIME);
        scanCategory(CAT_BREAKTIME_START, BREAKTIME_START);
        scanCategory(CAT_BREAKTIME_END, BREAKTIME_END);
        scanCategory(CAT_DAY_START, DAY_START);
        scanCategory(CAT_DAY_END, DAY_END);
        scanCategory(CAT_SMOKING, SMOKING);
        scanCategory(CAT_WORK_EXIT, WORK_EXIT);
        scanCategory(CAT_LOCKER_SOUND, LOCKER_SOUND);
        scanCategory(CAT_CHANGING_CLOTHES, CHANGING_CLOTHES);
        scanCategory(CAT_GET_INTO_BED, GET_INTO_BED);
        scanCategory(CAT_GET_UP, GET_UP);
        scanCategory(CAT_AFTERWORK_ROAMING, AFTERWORK_ROAMING);
        scanCategory(CAT_WAITING_FOR_BED, WAITING_FOR_BED);
        scanCategory(CAT_SUPERVISOR_ASK_REPORT, SUPERVISOR_ASK_REPORT);
        scanCategory(CAT_SUPERVISOR_REPORT, SUPERVISOR_REPORT);
        scanCategory(CAT_FIGHTING, FIGHTING);
        scanCategory(CAT_FLEEING, FLEEING);
        scanCategory(CAT_HEALING, HEALING);
        LockerWorkerMod.LOG.info(
            "Sounds discovered (jar): free_roaming={}, working={}, interaction={}, "
                + "breaktime={}, breaktime_start={}, breaktime_end={}, day_start={}, day_end={}, smoking={}, "
                + "work_exit={}, locker_sound={}, changing_clothes={}, get_into_bed={}, get_up={}, afterwork_roaming={}, waiting_for_bed={}, supervisor_ask_report={}, supervisor_report={}, fighting={}, fleeing={}, healing={}",
            FREE_ROAMING.size(),
            WORKING.size(),
            INTERACTION.size(),
            BREAKTIME.size(),
            BREAKTIME_START.size(),
            BREAKTIME_END.size(),
            DAY_START.size(),
            DAY_END.size(),
            SMOKING.size(),
            WORK_EXIT.size(),
            LOCKER_SOUND.size(),
            CHANGING_CLOTHES.size(),
            GET_INTO_BED.size(),
            GET_UP.size(),
            AFTERWORK_ROAMING.size(),
            WAITING_FOR_BED.size(),
            SUPERVISOR_ASK_REPORT.size(),
            SUPERVISOR_REPORT.size(),
            FIGHTING.size(),
            FLEEING.size(),
            HEALING.size());
    }

    public static String[] allCategories() {
        return ALL_CATEGORIES.clone();
    }

    public static List<String> freeRoaming() {
        return Collections.unmodifiableList(FREE_ROAMING);
    }

    public static List<String> working() {
        return Collections.unmodifiableList(WORKING);
    }

    public static List<String> interaction() {
        return Collections.unmodifiableList(INTERACTION);
    }

    public static List<String> breaktime() {
        return Collections.unmodifiableList(BREAKTIME);
    }

    public static List<String> breaktimeStart() {
        return Collections.unmodifiableList(BREAKTIME_START);
    }

    public static List<String> breaktimeEnd() {
        return Collections.unmodifiableList(BREAKTIME_END);
    }

    public static List<String> dayStart() {
        return Collections.unmodifiableList(DAY_START);
    }

    public static List<String> dayEnd() {
        return Collections.unmodifiableList(DAY_END);
    }

    public static List<String> smoking() {
        return Collections.unmodifiableList(SMOKING);
    }

    public static List<String> workExit() {
        return Collections.unmodifiableList(WORK_EXIT);
    }

    public static List<String> lockerSound() {
        return Collections.unmodifiableList(LOCKER_SOUND);
    }

    public static List<String> changingClothes() {
        return Collections.unmodifiableList(CHANGING_CLOTHES);
    }

    public static List<String> getIntoBed() {
        return Collections.unmodifiableList(GET_INTO_BED);
    }

    public static List<String> getUp() {
        return Collections.unmodifiableList(GET_UP);
    }

    public static List<String> afterworkRoaming() {
        return Collections.unmodifiableList(AFTERWORK_ROAMING);
    }

    public static List<String> waitingForBed() {
        return Collections.unmodifiableList(WAITING_FOR_BED);
    }

    public static List<String> supervisorAskReport() {
        return Collections.unmodifiableList(SUPERVISOR_ASK_REPORT);
    }

    public static List<String> supervisorReport() {
        return Collections.unmodifiableList(SUPERVISOR_REPORT);
    }

    public static List<String> fighting() {
        return Collections.unmodifiableList(FIGHTING);
    }

    public static List<String> fleeing() {
        return Collections.unmodifiableList(FLEEING);
    }

    public static List<String> healing() {
        return Collections.unmodifiableList(HEALING);
    }

    /**
     * All discovered clips as {@code [category, basename]} pairs (for auto-register).
     */
    public static List<String[]> discoveredClips() {
        return Collections.unmodifiableList(DISCOVERED);
    }

    /** Forge play name: {@code lockerworker:category.basename} (no .ogg). */
    public static String toPlayName(String category, String basename) {
        return LockerWorkerMod.MODID + ":" + category + "." + basename;
    }

    /** Asset path relative to domain: {@code sounds/category/basename.ogg}. */
    public static String toOggResourcePath(String category, String basename) {
        return "sounds/" + category + "/" + basename + ".ogg";
    }

    private static void scanCategory(String category, List<String> out) {
        String path = "assets/" + LockerWorkerMod.MODID + "/sounds/" + category + "/";
        try {
            ClassLoader cl = ModSounds.class.getClassLoader();
            Enumeration<URL> roots = cl.getResources(path);
            boolean any = false;
            while (roots.hasMoreElements()) {
                any = true;
                collectFromUrl(roots.nextElement(), path, category, out);
            }
            if (!any) {
                URL self = ModSounds.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
                if (self != null) {
                    collectFromUrl(self, path, category, out);
                }
            }
        } catch (IOException e) {
            LockerWorkerMod.LOG.warn("Sound scan failed for {}: {}", category, e.toString());
        }
        Collections.sort(out);
    }

    private static void collectFromUrl(URL url, String pathInJar, String category, List<String> out)
        throws IOException {
        if (url == null) {
            return;
        }
        String protocol = url.getProtocol();
        if ("file".equals(protocol)) {
            try {
                File root = new File(url.toURI());
                if (root.isFile() && root.getName()
                    .endsWith(".jar")) {
                    scanJar(root, pathInJar, category, out);
                    return;
                }
                File dir = new File(root, pathInJar);
                if (!dir.isDirectory()) {
                    dir = root;
                }
                if (dir.isDirectory()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            addIfOgg(f.getName(), category, out);
                        }
                    }
                }
            } catch (URISyntaxException e) {
                LockerWorkerMod.LOG.warn("Bad sound URL {}: {}", url, e.toString());
            }
        } else if ("jar".equals(protocol)) {
            String full = url.getPath();
            int bang = full.indexOf('!');
            String jarPath = full.substring(0, bang);
            if (jarPath.startsWith("file:")) {
                jarPath = jarPath.substring(5);
            }
            jarPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8.name());
            scanJar(new File(jarPath), pathInJar, category, out);
        }
    }

    private static void scanJar(File jarFile, String pathInJar, String category, List<String> out) throws IOException {
        if (!jarFile.isFile()) {
            return;
        }
        JarFile jar = new JarFile(jarFile);
        try {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String name = e.getName();
                if (!name.startsWith(pathInJar) || e.isDirectory()) {
                    continue;
                }
                String fileName = name.substring(pathInJar.length());
                if (fileName.contains("/")) {
                    continue;
                }
                addIfOgg(fileName, category, out);
            }
        } finally {
            jar.close();
        }
    }

    private static void addIfOgg(String fileName, String category, List<String> out) {
        if (fileName == null || !fileName.toLowerCase()
            .endsWith(".ogg")) {
            return;
        }
        String base = fileName.substring(0, fileName.length() - 4);
        if (base.isEmpty()) {
            return;
        }
        String play = toPlayName(category, base);
        if (!out.contains(play)) {
            out.add(play);
            DISCOVERED.add(new String[] { category, base });
        }
    }
}
