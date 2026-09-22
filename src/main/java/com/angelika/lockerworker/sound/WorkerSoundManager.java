package com.angelika.lockerworker.sound;

import java.util.List;
import java.util.Random;

import net.minecraft.entity.Entity;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityLockerWorker;
import com.angelika.lockerworker.entity.ai.EntityAIWanderNearMachines;

/**
 * Server-side ambient / interaction sound scheduler for {@link EntityLockerWorker}.
 * Empty sound lists → silent (no crash). Plays via {@code world.playSoundAtEntity}.
 */
public class WorkerSoundManager {

    private final EntityLockerWorker worker;
    private int freeRoamingSilenceLeft;
    private int workingSilenceLeft;
    private int interactionCooldownLeft;
    private int workingIndex;

    public WorkerSoundManager(EntityLockerWorker worker) {
        this.worker = worker;
        Random r = worker.getRNG();
        freeRoamingSilenceLeft = nextFreeRoamingGap(r);
        workingSilenceLeft = 0;
    }

    public void onUpdate() {
        if (worker.worldObj == null || worker.worldObj.isRemote) {
            return;
        }
        if (interactionCooldownLeft > 0) {
            interactionCooldownLeft--;
        }

        EntityAIWanderNearMachines.SoundPhase phase = worker.getDaySoundPhase();

        if (phase == EntityAIWanderNearMachines.SoundPhase.WORKING) {
            tickWorking();
            return;
        }

        // Reset working cycle when leaving inspect/approach
        workingSilenceLeft = 0;

        if (phase == EntityAIWanderNearMachines.SoundPhase.FREE_ROAMING) {
            tickFreeRoaming();
        }
    }

    /** Normal right-click interaction sound (not shift-stay). */
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
        String name = list.get(
            worker.getRNG()
                .nextInt(list.size()));
        play(
            name,
            1.0F,
            0.95F + worker.getRNG()
                .nextFloat() * 0.1F);
        interactionCooldownLeft = Config.interactionSoundCooldownTicks;
    }

    private void tickFreeRoaming() {
        if (!Config.soundFreeRoamingEnabled) {
            return;
        }
        List<String> list = ModSounds.freeRoaming();
        if (list.isEmpty()) {
            return;
        }
        if (freeRoamingSilenceLeft > 0) {
            freeRoamingSilenceLeft--;
            return;
        }
        String name = list.get(
            worker.getRNG()
                .nextInt(list.size()));
        play(
            name,
            0.8F,
            0.9F + worker.getRNG()
                .nextFloat() * 0.2F);
        freeRoamingSilenceLeft = nextFreeRoamingGap(worker.getRNG());
    }

    private void tickWorking() {
        if (!Config.soundWorkingEnabled) {
            return;
        }
        List<String> list = ModSounds.working();
        if (list.isEmpty()) {
            return;
        }
        if (workingSilenceLeft > 0) {
            workingSilenceLeft--;
            return;
        }
        if (workingIndex >= list.size()) {
            workingIndex = 0;
        }
        String name = list.get(workingIndex++);
        play(name, 1.0F, 1.0F);
        workingSilenceLeft = Math.max(0, Config.workingSilenceTicks);
    }

    private void play(String soundName, float volume, float pitch) {
        Entity e = worker;
        worker.worldObj.playSoundAtEntity(e, soundName, volume, pitch);
    }

    private static int nextFreeRoamingGap(Random r) {
        int min = Config.freeRoamingMinSilenceTicks;
        int max = Config.freeRoamingMaxSilenceTicks;
        if (max < min) {
            max = min;
        }
        if (max == min) {
            return min;
        }
        return min + r.nextInt(max - min + 1);
    }
}
