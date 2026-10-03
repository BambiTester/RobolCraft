package com.angelika.robolcraft.api;

/**
 * Implemented by a Forge mod that cooperates with RobolCraft.
 *
 * <p>
 * Call {@link RobolCraftAPI#register(IRobolCraftAddon)} from the addon {@code preInit}.
 * {@link #getApiVersion()} must return {@link RobolCraftAPI#API_VERSION}. That field is a
 * compile-time constant, so the addon jar keeps the version it was built against. An older
 * addon still loads on a newer RobolCraft. An addon built for a newer API is refused.
 */
public interface IRobolCraftAddon {

    String getModId();

    String getName();

    /**
     * API version this class was compiled against. Default is 1 for addons built before this
     * method existed. New addons should {@code return RobolCraftAPI.API_VERSION}.
     */
    default int getApiVersion() {
        return 1;
    }

    /**
     * Called when this addon's version no longer matches {@code v} on a setting it wrote.
     * Return the replacement object, the same object to keep it, or null to delete the override.
     */
    default com.google.gson.JsonObject migrate(String oldVersion, com.google.gson.JsonObject entry) {
        return entry;
    }
}
