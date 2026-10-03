package com.angelika.robolcraft.sound;

import java.util.List;

import com.angelika.robolcraft.Config;
import com.angelika.robolcraft.entity.EntityRobolCraft;
import com.angelika.robolcraft.entity.ai.EntityAIWanderNearMachines;

/**
 * Server-side sound mode synchronizer for {@link EntityRobolCraft}.
 *
 * <p>
 * Does not call {@code playSoundAtEntity} (that stacked overlapping clips). Writes
 * ambient mode into the entity datawatcher and bumps an interaction sequence. The
 * client ({@link ClientWorkerSounds}) owns exclusive {@link WorkerMovingSound}
 * playback — at most one clip per worker. One-shot day/break/smoking events are
 * fired from the entity and also play exclusively on the client.
 */
public class WorkerSoundManager {

    private final EntityRobolCraft worker;
    private int interactionCooldownLeft;

    public WorkerSoundManager(EntityRobolCraft worker) {
        this.worker = worker;
    }

    public void onUpdate() {
        if (worker.worldObj == null || worker.worldObj.isRemote) {
            return;
        }
        if (interactionCooldownLeft > 0) {
            interactionCooldownLeft--;
        }

        byte mode = EntityRobolCraft.SOUND_MODE_NONE;
        if (worker.isLyingInBed()) {
            mode = EntityRobolCraft.SOUND_MODE_NONE;
        } else if (worker.isChangingClothes()) {
            // v25: exclusive ambient from changing_clothes/ during 60-tick hold
            mode = EntityRobolCraft.SOUND_MODE_CHANGING_CLOTHES;
        } else if (worker.isPanicking()) {
            // 1.0.0 fleeing/ — panic overrides other ambient (not crowd-capped)
            mode = EntityRobolCraft.SOUND_MODE_FLEEING;
        } else if (worker.isHealing()) {
            // 1.0.8 healing/ — regenerating at medkit (working silence cadence)
            mode = EntityRobolCraft.SOUND_MODE_HEALING;
        } else if (worker.isCombatAttackActive()) {
            // 1.0.0 fighting/ — attack AI active (100% start on client)
            mode = EntityRobolCraft.SOUND_MODE_FIGHTING;
        } else if (worker.getNightAI() != null && worker.getNightAI()
            .isWaitingForBed()) {
                mode = EntityRobolCraft.SOUND_MODE_WAITING_FOR_BED;
            } else if (worker.getNightAI() != null && worker.getNightAI()
                .isAfterworkRoaming()) {
                    mode = EntityRobolCraft.SOUND_MODE_AFTERWORK_ROAMING;
                } else if (worker.isBreakPhaseActive()) {
                    mode = EntityRobolCraft.SOUND_MODE_BREAKTIME;
                } else {
                    EntityAIWanderNearMachines.SoundPhase phase = worker.getDaySoundPhase();
                    if (phase == EntityAIWanderNearMachines.SoundPhase.WORKING) {
                        mode = EntityRobolCraft.SOUND_MODE_WORKING;
                    } else if (phase == EntityAIWanderNearMachines.SoundPhase.FREE_ROAMING) {
                        mode = EntityRobolCraft.SOUND_MODE_FREE_ROAMING;
                    }
                }
        worker.setSyncedSoundMode(mode);
    }

    /** Normal right-click interaction. Interrupts client ambient. */
    public void playInteraction() {
        if (worker.worldObj == null || worker.worldObj.isRemote) {
            return;
        }
        if (!Config.soundInteractionEnabled) {
            return;
        }
        if (interactionCooldownLeft > 0) {
            return;
        }
        List<String> list = ModSounds.clipsFor(worker, ModSounds.CAT_INTERACTION);
        if (list.isEmpty()) {
            return;
        }
        worker.triggerInteractionSound();
        interactionCooldownLeft = Config.interactionSoundCooldownTicks;
    }
}
