package com.angelika.lockerworker.sound;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.util.ResourceLocation;

import com.angelika.lockerworker.Config;
import com.angelika.lockerworker.entity.EntityLockerWorker;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-only exclusive playback: at most one {@link WorkerMovingSound} per worker
 * entityId. Mode changes and interaction stop the previous clip immediately.
 */
@SideOnly(Side.CLIENT)
public final class ClientWorkerSounds {

    public enum LocalMode {
        NONE,
        FREE_ROAMING,
        WORKING,
        INTERACTION
    }

    private static final Map<Integer, Entry> ENTRIES = new HashMap<Integer, Entry>();

    private ClientWorkerSounds() {}

    public static void tick(EntityLockerWorker worker) {
        if (worker == null || worker.worldObj == null || !worker.worldObj.isRemote) {
            return;
        }
        if (worker.isDead) {
            stopAndRemove(worker.getEntityId());
            return;
        }

        int id = worker.getEntityId();
        Entry e = ENTRIES.get(id);
        if (e == null) {
            e = new Entry();
            ENTRIES.put(id, e);
        }

        byte synced = worker.getSyncedSoundMode();
        int interactSeq = worker.getInteractionSoundSeq();

        // Interaction interrupt — highest priority
        if (interactSeq != e.lastInteractSeq) {
            e.lastInteractSeq = interactSeq;
            if (Config.soundInteractionEnabled) {
                stopCurrent(e);
                startClip(worker, e, LocalMode.INTERACTION, ModSounds.interaction(), 1.0F, pitch(worker, 0.95F, 0.1F));
                return;
            }
        }

        LocalMode want = modeFromSynced(synced);

        // Mode change while not mid-interaction: stop previous, switch immediately
        if (e.localMode != LocalMode.INTERACTION && want != e.desiredMode) {
            e.desiredMode = want;
            stopCurrent(e);
            e.silenceLeft = 0;
            e.workingIndex = 0;
            if (want == LocalMode.NONE) {
                e.localMode = LocalMode.NONE;
                return;
            }
            beginModeClip(worker, e, want);
            return;
        }

        e.desiredMode = want;

        if (e.active != null) {
            if (clipFinished(e)) {
                stopCurrent(e);
                onClipEnded(worker, e);
            }
            return;
        }

        if (e.silenceLeft > 0) {
            e.silenceLeft--;
            return;
        }

        if (e.localMode == LocalMode.INTERACTION) {
            resumeAfterInteraction(worker, e);
            return;
        }

        if (e.localMode == LocalMode.NONE) {
            return;
        }

        beginModeClip(worker, e, e.localMode);
    }

    public static void stopAndRemove(int entityId) {
        Entry e = ENTRIES.remove(Integer.valueOf(entityId));
        if (e != null) {
            stopCurrent(e);
        }
    }

    private static void onClipEnded(EntityLockerWorker worker, Entry e) {
        if (e.localMode == LocalMode.INTERACTION) {
            resumeAfterInteraction(worker, e);
            return;
        }
        if (e.localMode == LocalMode.WORKING) {
            e.silenceLeft = Math.max(0, Config.workingSilenceTicks);
            return;
        }
        if (e.localMode == LocalMode.FREE_ROAMING) {
            e.silenceLeft = nextFreeRoamingGap(worker.getRNG());
            return;
        }
        e.localMode = LocalMode.NONE;
    }

    private static void resumeAfterInteraction(EntityLockerWorker worker, Entry e) {
        LocalMode want = e.desiredMode;
        e.silenceLeft = 0;
        if (want == LocalMode.NONE) {
            e.localMode = LocalMode.NONE;
            return;
        }
        beginModeClip(worker, e, want);
    }

    private static void beginModeClip(EntityLockerWorker worker, Entry e, LocalMode mode) {
        if (mode == LocalMode.WORKING) {
            if (!Config.soundWorkingEnabled) {
                e.localMode = LocalMode.WORKING;
                e.silenceLeft = 10;
                return;
            }
            startClip(worker, e, LocalMode.WORKING, ModSounds.working(), 1.0F, 1.0F);
        } else if (mode == LocalMode.FREE_ROAMING) {
            if (!Config.soundFreeRoamingEnabled) {
                e.localMode = LocalMode.FREE_ROAMING;
                e.silenceLeft = nextFreeRoamingGap(worker.getRNG());
                return;
            }
            startClip(worker, e, LocalMode.FREE_ROAMING, ModSounds.freeRoaming(), 0.8F, pitch(worker, 0.9F, 0.2F));
        } else {
            e.localMode = LocalMode.NONE;
        }
    }

    private static void startClip(EntityLockerWorker worker, Entry e, LocalMode mode, List<String> list, float volume,
        float pitch) {
        e.localMode = mode;
        if (list == null || list.isEmpty()) {
            e.silenceLeft = mode == LocalMode.WORKING ? Math.max(2, Config.workingSilenceTicks)
                : nextFreeRoamingGap(worker.getRNG());
            if (mode == LocalMode.INTERACTION) {
                resumeAfterInteraction(worker, e);
            }
            return;
        }

        String name;
        if (mode == LocalMode.WORKING) {
            if (e.workingIndex >= list.size()) {
                e.workingIndex = 0;
            }
            name = list.get(e.workingIndex++);
        } else {
            name = list.get(
                worker.getRNG()
                    .nextInt(list.size()));
        }

        ResourceLocation loc = new ResourceLocation(name);
        WorkerMovingSound sound = new WorkerMovingSound(worker, loc, volume, pitch);
        e.active = sound;
        e.ticksPlaying = 0;
        e.maxTicks = Math.max(1, Config.defaultClipLengthTicks);
        Minecraft.getMinecraft()
            .getSoundHandler()
            .playSound(sound);
    }

    private static boolean clipFinished(Entry e) {
        if (e.active == null) {
            return true;
        }
        e.ticksPlaying++;
        if (e.active.wasForceStopped() || e.active.isDonePlaying()) {
            return true;
        }
        if (e.ticksPlaying >= e.maxTicks) {
            return true;
        }
        SoundHandler handler = Minecraft.getMinecraft()
            .getSoundHandler();
        if (e.ticksPlaying > 10 && !handler.isSoundPlaying(e.active)) {
            return true;
        }
        return false;
    }

    private static void stopCurrent(Entry e) {
        if (e.active != null) {
            e.active.forceStop();
            try {
                Minecraft.getMinecraft()
                    .getSoundHandler()
                    .stopSound(e.active);
            } catch (Throwable ignored) {
                // Sound system may be unloaded
            }
            e.active = null;
        }
        e.ticksPlaying = 0;
    }

    private static LocalMode modeFromSynced(byte synced) {
        if (synced == EntityLockerWorker.SOUND_MODE_FREE_ROAMING) {
            return LocalMode.FREE_ROAMING;
        }
        if (synced == EntityLockerWorker.SOUND_MODE_WORKING) {
            return LocalMode.WORKING;
        }
        return LocalMode.NONE;
    }

    private static float pitch(EntityLockerWorker worker, float base, float span) {
        return base + worker.getRNG()
            .nextFloat() * span;
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

    private static final class Entry {

        WorkerMovingSound active;
        LocalMode localMode = LocalMode.NONE;
        LocalMode desiredMode = LocalMode.NONE;
        int lastInteractSeq;
        int silenceLeft;
        int workingIndex;
        int ticksPlaying;
        int maxTicks;
    }
}
