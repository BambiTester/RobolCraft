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
 * Discovers {@code .ogg} files under
 * {@code assets/lockerworker/sounds/{free_roaming,working,interaction}/}.
 *
 * <p>
 * Forge 1.7.10 normally needs static {@code sounds.json} entries. We also scan the
 * classpath/jar at startup so empty folders stay silent (no crash) and dropped
 * {@code .ogg} files become playable event names of the form
 * {@code lockerworker:<category>.<basename>} once listed in {@code sounds.json}
 * (see {@code SOUNDS_HOWTO.md}). Playback uses those event names via
 * {@code world.playSoundAtEntity}.
 */
public final class ModSounds {

    public static final String CAT_FREE_ROAMING = "free_roaming";
    public static final String CAT_WORKING = "working";
    public static final String CAT_INTERACTION = "interaction";

    private static final List<String> FREE_ROAMING = new ArrayList<String>();
    private static final List<String> WORKING = new ArrayList<String>();
    private static final List<String> INTERACTION = new ArrayList<String>();

    private ModSounds() {}

    public static void discover() {
        FREE_ROAMING.clear();
        WORKING.clear();
        INTERACTION.clear();
        scanCategory(CAT_FREE_ROAMING, FREE_ROAMING);
        scanCategory(CAT_WORKING, WORKING);
        scanCategory(CAT_INTERACTION, INTERACTION);
        LockerWorkerMod.LOG.info(
            "Sounds discovered: free_roaming={}, working={}, interaction={}",
            FREE_ROAMING.size(),
            WORKING.size(),
            INTERACTION.size());
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

    /** Forge play name: {@code lockerworker:category.basename} (no .ogg). */
    public static String toPlayName(String category, String basename) {
        return LockerWorkerMod.MODID + ":" + category + "." + basename;
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
                // Fallback: try file under src/resources during dev
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
                File dir;
                if (root.isFile() && root.getName()
                    .endsWith(".jar")) {
                    scanJar(root, pathInJar, category, out);
                    return;
                }
                // classes/ or resources root
                dir = new File(root, pathInJar);
                if (!dir.isDirectory()) {
                    // URL may already point at the sounds category folder
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
            // jar:file:/path/to.jar!/assets/...
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
                    continue; // only flat category folder
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
        }
    }
}
