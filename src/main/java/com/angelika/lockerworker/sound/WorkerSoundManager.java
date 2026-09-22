package com.angelika.lockerworker.sound;

import java.util.List;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.entity.ai.EntityAIWanderNearMachines;

/**
 * Server-side sound mode synchronizer for {@link EntityLockerWorker}.
 *
 * <p>
 * Does not call {@code playSoundAtEntity} (that stacked overlapping clips). Writes
 * ambient mode into the entity datawatcher and bumps an interaction sequence. The
 * client ({@link ClientWorkerSounds}) owns exclusive {@link WorkerMovingSound}
 * playback — at most one clip per worker. One-shot day/break/smoking events are
 * fired from the entity and also play exclusively on the client.
 */
public class WorkerSoundManager {

    private final EntityLockerWorker worker;
    private int interactionCooldownLeft;

    public WorkerSoundManager(EntityLockerWorker worker) {
        this.worker = worker;
    }

    public void onUpdate() {
        if (worker.worldObj == null || worker.worldObj.isRemote) {
            return;
        }
        if (interactionCooldownLeft > 0) {
            interactionCooldownLeft--;
        }

        byte mode = EntityLockerWorker.SOUND_MODE_NONE;
        if (worker.isBreakPhaseActive()) {
            mode = EntityLockerWorker.SOUND_MODE_BREAKTIME;
        } else {
            EntityAIWanderNearMachines.SoundPhase phase = worker.getDaySoundPhase();
            if (phase == EntityAIWanderNearMachines.SoundPhase.WORKING) {
                mode = EntityLockerWorker.SOUND_MODE_WORKING;
            } else if (phase == EntityAIWanderNearMachines.SoundPhase.FREE_ROAMING) {
                mode = EntityLockerWorker.SOUND_MODE_FREE_ROAMING;
            }
        }
        worker.setSyncedSoundMode(mode);
    }

    /** Normal right-click interaction (not shift-stay). Interrupts client ambient. */
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
        List<String> list = ModSounds.interaction();
        if (list.isEmpty()) {
            return;
        }
        worker.triggerInteractionSound();
        interactionCooldownLeft = Config.interactionSoundCooldownTicks;
    }
}
