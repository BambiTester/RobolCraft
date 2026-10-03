package com.angelika.robolcraft.api;

import net.minecraft.entity.ai.EntityAIBase;

import com.angelika.robolcraft.entity.EntityRobolCraft;
import com.google.gson.JsonObject;

/**
 * Builds one AI goal for a named behavior. {@code parameters} is the merged slot object from the
 * world JSON, or empty when the NPC is on defaults.
 */
public interface BehaviorFactory {

    EntityAIBase create(EntityRobolCraft entity, JsonObject parameters);
}
