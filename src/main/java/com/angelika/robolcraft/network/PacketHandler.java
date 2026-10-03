package com.angelika.robolcraft.network;

import com.angelika.robolcraft.RobolCraftMod;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * RobolCraft network channel. Work-range needs a real int (enchant-button is one byte).
 */
public final class PacketHandler {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(RobolCraftMod.MODID);

    private PacketHandler() {}

    public static void init() {
        INSTANCE.registerMessage(PacketSetWorkDistance.Handler.class, PacketSetWorkDistance.class, 0, Side.SERVER);
        INSTANCE.registerMessage(PacketLookAtBlock.Handler.class, PacketLookAtBlock.class, 1, Side.CLIENT);
        INSTANCE
            .registerMessage(PacketJourneyMapWaypoint.Handler.class, PacketJourneyMapWaypoint.class, 2, Side.CLIENT);
        INSTANCE.registerMessage(PacketPlayWorkerClip.Handler.class, PacketPlayWorkerClip.class, 3, Side.CLIENT);
    }
}
