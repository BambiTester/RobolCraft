package com.angelika.lockerworker.client;

import java.awt.Color;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import com.angelika.lockerworker.LockerWorkerMod;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Soft JourneyMap 5.2.10 fairplay compat (GTNH). Reflection only — no hard dependency.
 * Targets: {@code journeymap.client.model.Waypoint} + {@code WaypointStore.save}.
 */
@SideOnly(Side.CLIENT)
public final class JourneyMapCompat {

    private static boolean probed;
    private static boolean available;
    private static Constructor<?> waypointCtor;
    private static Object typeNormal;
    private static Method storeInstance;
    private static Method storeSave;

    private JourneyMapCompat() {}

    /** One-shot probe during client init. Safe to call repeatedly. */
    public static void probe() {
        if (probed) {
            return;
        }
        probed = true;
        available = false;
        if (!Loader.isModLoaded("journeymap")) {
            LockerWorkerMod.LOG.info("JourneyMap not loaded — map waypoints disabled");
            return;
        }
        try {
            Class<?> wpClass = Class.forName("journeymap.client.model.Waypoint");
            Class<?> typeClass = Class.forName("journeymap.client.model.Waypoint$Type");
            Class<?> storeClass = Class.forName("journeymap.client.waypoint.WaypointStore");
            @SuppressWarnings({ "unchecked", "rawtypes" })
            Object normal = Enum.valueOf((Class) typeClass, "Normal");
            typeNormal = normal;
            waypointCtor = wpClass
                .getConstructor(String.class, int.class, int.class, int.class, Color.class, typeClass, Integer.class);
            storeInstance = storeClass.getMethod("instance");
            storeSave = storeClass.getMethod("save", wpClass);
            available = true;
            LockerWorkerMod.LOG.info("JourneyMap waypoint reflection ready (5.2.x fairplay)");
        } catch (Throwable t) {
            available = false;
            LockerWorkerMod.LOG.info("JourneyMap present but waypoint API unavailable: {}", t.toString());
        }
    }

    public static boolean isAvailable() {
        if (!probed) {
            probe();
        }
        return available;
    }

    /**
     * Add/persist a red Normal waypoint. Returns false if JM missing or reflection fails.
     */
    public static boolean addRedWaypoint(String name, int x, int y, int z, int dimension) {
        if (!isAvailable()) {
            return false;
        }
        try {
            Object wp = waypointCtor.newInstance(
                name != null ? name : "RobolCraft",
                x,
                y,
                z,
                Color.RED,
                typeNormal,
                Integer.valueOf(dimension));
            Object store = storeInstance.invoke(null);
            storeSave.invoke(store, wp);
            return true;
        } catch (Throwable t) {
            LockerWorkerMod.LOG.warn("Failed to add JourneyMap waypoint: {}", t.toString());
            return false;
        }
    }
}
