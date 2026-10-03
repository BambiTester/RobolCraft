package com.angelika.robolcraft.api;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/**
 * Shared worker sound pools. Clip files stay in whichever jar owns them. Call these methods
 * from addon {@code preInit} or {@code init}. RobolCraft {@code postInit} freezes the pools.
 */
public interface RobolCraftSounds {

    /**
     * Names of the worker categories shipped by the base mod ({@code free_roaming}, {@code working},
     * and the rest). A copy; safe to keep.
     */
    List<String> baseCategories();

    /**
     * Append one ogg to a base category for every worker, including the base worker and the
     * supervisor. Unknown category names are rejected.
     *
     * @return {@code false} if the category is unknown, the call is too late, or this mod has not
     *         checked in
     */
    boolean extendCategory(String category, ResourceLocation ogg);

    /**
     * Create a category the base AI does not play. The addon plays it with {@link #play}. A base
     * category name is rejected; use {@link #extendCategory} for those.
     */
    boolean registerCategory(String category, ResourceLocation ogg);

    /**
     * Register an ogg without putting it in a shared pool. Return the play name from
     * {@link com.angelika.robolcraft.entity.EntityRobolCraft#additionalClips(String)} so only that
     * character uses it. {@code null} if the call is rejected.
     */
    String registerPrivateClip(String category, ResourceLocation ogg);

    /** Base clips plus every addon's {@link #extendCategory} contributions. Empty if unknown. */
    List<String> clips(String category);

    /**
     * Play one clip from {@link #clips}. On a worker this uses the exclusive client channel so it
     * does not stack on the ambient line. On any other entity this uses {@code playSoundAtEntity}.
     */
    void play(Entity entity, String category, float volume, float pitch);
}
