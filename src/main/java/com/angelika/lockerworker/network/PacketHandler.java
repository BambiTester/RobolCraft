package com.angelika.lockerworker.network;

import com.angelika.lockerworker.LockerWorkerMod;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * RobolCraft network channel. Work-range needs a real int (enchant-button is one byte).
 */
public final class PacketHandler {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE
        .newSimpleChannel(LockerWorkerMod.MODID);

    private PacketHandler() {}

    public static void init() {
        INSTANCE.registerMessage(PacketSetWorkDistance.Handler.class, PacketSetWorkDistance.class, 0, Side.SERVER);
        INSTANCE.registerMessage(PacketLookAtBlock.Handler.class, PacketLookAtBlock.class, 1, Side.CLIENT);
        INSTANCE
            .registerMessage(PacketJourneyMapWaypoint.Handler.class, PacketJourneyMapWaypoint.class, 2, Side.CLIENT);
    }
}
