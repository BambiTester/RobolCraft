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
 * entityId.
 *
 * <p>
 * 1.0.4: free-roam / working / breaktime / afterwork ambient clips finish before
 * switching among that family (oneshots queue). Fighting, fleeing, clothes, and
 * interaction still interrupt immediately. Soft {@code isSoundPlaying} check for
 * protected ambient avoids false early-ends when OpenAL drops a channel briefly.
 */
@SideOnly(Side.CLIENT)
public final class ClientWorkerSounds {

    public enum LocalMode {
        NONE,
        FREE_ROAMING,
        WORKING,
        BREAKTIME,
        AFTERWORK_ROAMING,
        WAITING_FOR_BED,
        CHANGING_CLOTHES,
        FIGHTING,
        FLEEING,
        HEALING,
        INTERACTION,
        ONESHOT
    }

    private static final Map<Integer, Entry> ENTRIES = new HashMap<Integer, Entry>();
    private static int sweepTicker;

    /** Soft-cap radius (blocks) for concurrent breaktime ambient near a speaker. */
    private static final double BREAKTIME_CROWD_RADIUS = 14.0D;
    private static final double BREAKTIME_CROWD_RADIUS_SQ = BREAKTIME_CROWD_RADIUS * BREAKTIME_CROWD_RADIUS;
    /** Max simultaneous breaktime ambient MovingSounds within crowd radius (peers with active clip). */
    private static final int BREAKTIME_CROWD_MAX = 3;
    /** Short silence when soft-cap skips a breaktime start (ticks). */
    private static final int BREAKTIME_CROWD_RETRY_MIN = 40;
    private static final int BREAKTIME_CROWD_RETRY_SPAN = 60;

    /** Fighting / fleeing ambient: 100% start, gap 20–100 ticks (1–5s). Not crowd-capped. */
    private static final int COMBAT_SILENCE_MIN = 20;
    private static final int COMBAT_SILENCE_MAX = 100;

    /** Long safety-only fallback if SoundHandler never reports done (~5 min). Clips play fully. */
    private static final int CLIP_SAFETY_MAX_TICKS = 6000;

    /** Consecutive not-playing ticks before treating protected ambient as finished (~2s). */
    private static final int PROTECTED_NOT_PLAYING_STREAK = 40;

    private ClientWorkerSounds() {}

    /** Free-roam / working / break / afterwork — finish clip before mode switch. */
    private static boolean isProtectedAmbient(LocalMode mode) {
        return mode == LocalMode.FREE_ROAMING || mode == LocalMode.WORKING
            || mode == LocalMode.BREAKTIME
            || mode == LocalMode.AFTERWORK_ROAMING
            || mode == LocalMode.HEALING;
    }

    /** Modes that cut protected ambient immediately. */
    private static boolean isHardInterrupt(LocalMode mode) {
        return mode == LocalMode.FIGHTING || mode == LocalMode.FLEEING || mode == LocalMode.CHANGING_CLOTHES;
    }

    public static void tick(EntityLockerWorker worker) {
        if (worker == null || worker.worldObj == null || !worker.worldObj.isRemote) {
            return;
        }
        // 1.0.6: rare sweep for entries whose entity unloaded without setDead
        if (++sweepTicker >= 100) {
            sweepTicker = 0;
            sweepStaleEntries();
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
        int oneshotSeq = worker.getOneshotSoundSeq();
        byte oneshotKind = worker.getOneshotSoundKind();

        // One-shot events — queue if protected ambient is mid-clip; otherwise interrupt
        if (oneshotSeq != e.lastOneshotSeq) {
            e.lastOneshotSeq = oneshotSeq;
            handleOneshot(worker, e, oneshotKind);
            return;
        }

        // Interaction interrupt — still cuts ambient (clear queued oneshot)
        if (interactSeq != e.lastInteractSeq) {
            e.lastInteractSeq = interactSeq;
            if (Config.soundInteractionEnabled) {
                e.pendingOneshotKind = -1;
                stopCurrent(e);
                startClip(worker, e, LocalMode.INTERACTION, ModSounds.interaction(), 1.0F, pitch(worker, 0.95F, 0.1F));
                return;
            }
        }

        LocalMode want = modeFromSynced(synced);

        // Mode change while not mid-interaction/oneshot
        if (e.localMode != LocalMode.INTERACTION && e.localMode != LocalMode.ONESHOT && want != e.desiredMode) {
            e.desiredMode = want;
            boolean playingProtected = e.active != null && isProtectedAmbient(e.localMode);
            if (playingProtected && isProtectedAmbient(want)) {
                // Finish current line; switch after clip ends
                return;
            }
            if (playingProtected && !isHardInterrupt(want) && want != LocalMode.INTERACTION) {
                // Soft leave (NONE / waiting_for_bed / etc.): finish line first
                return;
            }
            // Hard interrupt (fight / flee / clothes) or unprotected modes
            e.pendingOneshotKind = -1;
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
                if (playPendingOneshotIfAny(worker, e)) {
                    return;
                }
                onClipEnded(worker, e);
            }
            return;
        }

        if (e.silenceLeft > 0) {
            e.silenceLeft--;
            return;
        }

        if (playPendingOneshotIfAny(worker, e)) {
            return;
        }

        if (e.localMode == LocalMode.INTERACTION || e.localMode == LocalMode.ONESHOT) {
            resumeAfterInterrupt(worker, e);
            return;
        }

        if (e.desiredMode == LocalMode.NONE && e.localMode == LocalMode.NONE) {
            return;
        }

        // Prefer latest desired ambient mode (finish-before-switch handoff)
        LocalMode next = e.desiredMode != LocalMode.NONE ? e.desiredMode : e.localMode;
        if (next == LocalMode.NONE) {
            e.localMode = LocalMode.NONE;
            return;
        }
        beginModeClip(worker, e, next);
    }

    public static void stopAndRemove(int entityId) {
        Entry e = ENTRIES.remove(Integer.valueOf(entityId));
        if (e != null) {
            e.pendingOneshotKind = -1;
            stopCurrent(e);
        }
    }

    /** Drop map entries whose worker is gone / unloaded (entityId reuse safety). */
    private static void sweepStaleEntries() {
        if (ENTRIES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) {
            return;
        }
        java.util.Iterator<Map.Entry<Integer, Entry>> it = ENTRIES.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Entry> en = it.next();
            net.minecraft.entity.Entity ent = mc.theWorld.getEntityByID(en.getKey());
            if (ent == null || ent.isDead || !(ent instanceof EntityLockerWorker)) {
                Entry e = en.getValue();
                if (e != null) {
                    e.pendingOneshotKind = -1;
                    stopCurrent(e);
                }
                it.remove();
            }
        }
    }

    private static void handleOneshot(EntityLockerWorker worker, Entry e, byte kind) {
        List<String> list = listForOneshot(kind);
        if (kind == EntityLockerWorker.ONESHOT_SMOKING) {
            spawnSmokeParticles(worker);
        }
        if (list == null || list.isEmpty()) {
            // Empty folder = silent OK; still resume ambient after
            e.desiredMode = modeFromSynced(worker.getSyncedSoundMode());
            return;
        }
        // Protected ambient mid-clip: queue non-schedule oneshots until the line finishes.
        // Schedule cues (break_start/end, day_end) interrupt so they are actually heard.
        if (e.active != null && isProtectedAmbient(e.localMode) && !isScheduleCueOneshot(kind)) {
            e.pendingOneshotKind = kind;
            return;
        }
        e.pendingOneshotKind = -1;
        stopCurrent(e);
        startClip(worker, e, LocalMode.ONESHOT, list, 1.0F, pitch(worker, 0.95F, 0.1F));
    }

    /** @return true if a queued oneshot was started */
    private static boolean playPendingOneshotIfAny(EntityLockerWorker worker, Entry e) {
        if (e.pendingOneshotKind < 0) {
            return false;
        }
        byte kind = (byte) e.pendingOneshotKind;
        e.pendingOneshotKind = -1;
        List<String> list = listForOneshot(kind);
        if (list == null || list.isEmpty()) {
            return false;
        }
        e.silenceLeft = 0;
        startClip(worker, e, LocalMode.ONESHOT, list, 1.0F, pitch(worker, 0.95F, 0.1F));
        return true;
    }

    private static List<String> listForOneshot(byte kind) {
        if (kind == EntityLockerWorker.ONESHOT_DAY_START) {
            return ModSounds.dayStart();
        }
        if (kind == EntityLockerWorker.ONESHOT_DAY_END) {
            return ModSounds.dayEnd();
        }
        if (kind == EntityLockerWorker.ONESHOT_BREAK_START) {
            return ModSounds.breaktimeStart();
        }
        if (kind == EntityLockerWorker.ONESHOT_BREAK_END) {
            return ModSounds.breaktimeEnd();
        }
        if (kind == EntityLockerWorker.ONESHOT_SMOKING) {
            return ModSounds.smoking();
        }
        if (kind == EntityLockerWorker.ONESHOT_CHANGING_CLOTHES) {
            return ModSounds.changingClothes();
        }
        if (kind == EntityLockerWorker.ONESHOT_GET_INTO_BED) {
            return ModSounds.getIntoBed();
        }
        if (kind == EntityLockerWorker.ONESHOT_GET_UP) {
            return ModSounds.getUp();
        }
        return null;
    }

    /** Schedule edge cues — interrupt protected ambient instead of queuing behind it. */
    private static boolean isScheduleCueOneshot(byte kind) {
        return kind == EntityLockerWorker.ONESHOT_BREAK_END || kind == EntityLockerWorker.ONESHOT_BREAK_START
            || kind == EntityLockerWorker.ONESHOT_DAY_END;
    }

    /** Soft white smoke from head/mouth area — upward drift, not explosions. */
    private static void spawnSmokeParticles(EntityLockerWorker worker) {
        if (worker.worldObj == null) {
            return;
        }
        double x = worker.posX;
        double y = worker.posY + worker.getEyeHeight() * 0.85D;
        double z = worker.posZ;
        // Bias slightly forward of facing
        float yaw = worker.rotationYawHead;
        double rad = Math.toRadians(yaw);
        x -= Math.sin(rad) * 0.25D;
        z += Math.cos(rad) * 0.25D;
        Random r = worker.getRNG();
        for (int i = 0; i < 3; i++) {
            double ox = (r.nextDouble() - 0.5D) * 0.08D;
            double oy = r.nextDouble() * 0.05D;
            double oz = (r.nextDouble() - 0.5D) * 0.08D;
            // "smoke" = soft grey; vanilla also has "cloud" (whiter)
            worker.worldObj.spawnParticle("smoke", x + ox, y + oy, z + oz, 0.0D, 0.02D + r.nextDouble() * 0.02D, 0.0D);
            if (r.nextBoolean()) {
                worker.worldObj
                    .spawnParticle("cloud", x + ox, y + oy, z + oz, 0.0D, 0.015D + r.nextDouble() * 0.015D, 0.0D);
            }
        }
    }

    private static void onClipEnded(EntityLockerWorker worker, Entry e) {
        if (e.localMode == LocalMode.INTERACTION || e.localMode == LocalMode.ONESHOT) {
            resumeAfterInterrupt(worker, e);
            return;
        }
        if (e.localMode == LocalMode.WORKING || e.localMode == LocalMode.HEALING) {
            e.silenceLeft = nextWorkingGap(worker.getRNG());
            return;
        }
        if (e.localMode == LocalMode.FIGHTING || e.localMode == LocalMode.FLEEING) {
            e.silenceLeft = nextCombatGap(worker.getRNG());
            return;
        }
        if (e.localMode == LocalMode.FREE_ROAMING) {
            e.silenceLeft = nextAmbientGap(worker.getRNG());
            return;
        }
        if (e.localMode == LocalMode.BREAKTIME) {
            e.silenceLeft = nextBreaktimeGap(worker.getRNG());
            return;
        }
        if (e.localMode == LocalMode.AFTERWORK_ROAMING) {
            e.silenceLeft = nextAmbientGap(worker.getRNG());
            return;
        }
        if (e.localMode == LocalMode.WAITING_FOR_BED) {
            e.silenceLeft = nextAmbientGap(worker.getRNG());
            return;
        }
        if (e.localMode == LocalMode.CHANGING_CLOTHES) {
            // Short gap; hold is only 60 ticks — keep clips available if folder filled
            e.silenceLeft = 2;
            return;
        }
        e.localMode = LocalMode.NONE;
    }

    private static void resumeAfterInterrupt(EntityLockerWorker worker, Entry e) {
        LocalMode want = e.desiredMode;
        if (want == LocalMode.NONE) {
            want = modeFromSynced(worker.getSyncedSoundMode());
            e.desiredMode = want;
        }
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
        } else if (mode == LocalMode.HEALING) {
            // Same silence cadence as working; empty folder → silent gaps
            startClip(worker, e, LocalMode.HEALING, ModSounds.healing(), 1.0F, 1.0F);
        } else if (mode == LocalMode.FIGHTING) {
            // 100% start when mode active; empty folder → short retry (not crowd-capped)
            startClip(worker, e, LocalMode.FIGHTING, ModSounds.fighting(), 1.0F, pitch(worker, 0.95F, 0.1F));
        } else if (mode == LocalMode.FLEEING) {
            startClip(worker, e, LocalMode.FLEEING, ModSounds.fleeing(), 1.0F, pitch(worker, 0.95F, 0.1F));
        } else if (mode == LocalMode.FREE_ROAMING) {
            if (!Config.soundFreeRoamingEnabled) {
                e.localMode = LocalMode.FREE_ROAMING;
                e.silenceLeft = nextAmbientGap(worker.getRNG());
                return;
            }
            startClip(worker, e, LocalMode.FREE_ROAMING, ModSounds.freeRoaming(), 1.0F, pitch(worker, 0.9F, 0.2F));
        } else if (mode == LocalMode.BREAKTIME) {
            e.localMode = LocalMode.BREAKTIME;
            if (countNearbyBreaktimeAmbient(worker) >= BREAKTIME_CROWD_MAX) {
                // Soft-cap at trashcan: skip start, roll short silence (one-shots ungated)
                e.silenceLeft = BREAKTIME_CROWD_RETRY_MIN + worker.getRNG()
                    .nextInt(BREAKTIME_CROWD_RETRY_SPAN + 1);
                return;
            }
            startClip(worker, e, LocalMode.BREAKTIME, ModSounds.breaktime(), 1.05F, pitch(worker, 0.9F, 0.2F));
        } else if (mode == LocalMode.AFTERWORK_ROAMING) {
            startClip(
                worker,
                e,
                LocalMode.AFTERWORK_ROAMING,
                ModSounds.afterworkRoaming(),
                1.0F,
                pitch(worker, 0.9F, 0.2F));
        } else if (mode == LocalMode.WAITING_FOR_BED) {
            startClip(worker, e, LocalMode.WAITING_FOR_BED, ModSounds.waitingForBed(), 1.0F, pitch(worker, 0.9F, 0.2F));
        } else if (mode == LocalMode.CHANGING_CLOTHES) {
            startClip(
                worker,
                e,
                LocalMode.CHANGING_CLOTHES,
                ModSounds.changingClothes(),
                1.0F,
                pitch(worker, 0.95F, 0.1F));
        } else {
            e.localMode = LocalMode.NONE;
        }
    }

    private static void startClip(EntityLockerWorker worker, Entry e, LocalMode mode, List<String> list, float volume,
        float pitch) {
        e.localMode = mode;
        if (list == null || list.isEmpty()) {
            if (mode == LocalMode.WORKING || mode == LocalMode.HEALING) {
                e.silenceLeft = Math.max(2, nextWorkingGap(worker.getRNG()));
            } else if (mode == LocalMode.FIGHTING || mode == LocalMode.FLEEING) {
                e.silenceLeft = nextCombatGap(worker.getRNG());
            } else if (mode == LocalMode.INTERACTION || mode == LocalMode.ONESHOT) {
                resumeAfterInterrupt(worker, e);
            } else if (mode == LocalMode.BREAKTIME) {
                e.silenceLeft = nextBreaktimeGap(worker.getRNG());
            } else {
                e.silenceLeft = nextAmbientGap(worker.getRNG());
            }
            return;
        }

        String name;
        if (mode == LocalMode.WORKING || mode == LocalMode.HEALING) {
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
        // Clips play fully — long safety fallback only (~5 min)
        e.maxTicks = CLIP_SAFETY_MAX_TICKS;
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
            e.notPlayingStreak = 0;
            return true;
        }
        SoundHandler handler = Minecraft.getMinecraft()
            .getSoundHandler();
        boolean playing = handler.isSoundPlaying(e.active);
        if (isProtectedAmbient(e.localMode)) {
            // Soft check: OpenAL may briefly report not-playing under channel pressure
            if (e.ticksPlaying > 10 && !playing) {
                e.notPlayingStreak++;
                if (e.notPlayingStreak >= PROTECTED_NOT_PLAYING_STREAK) {
                    return true;
                }
            } else {
                e.notPlayingStreak = 0;
            }
        } else if (e.ticksPlaying > 10 && !playing) {
            return true;
        }
        // Long safety fallback only — never a short hard stop mid-clip
        if (e.ticksPlaying >= e.maxTicks) {
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
        e.notPlayingStreak = 0;
    }

    private static LocalMode modeFromSynced(byte synced) {
        if (synced == EntityLockerWorker.SOUND_MODE_FREE_ROAMING) {
            return LocalMode.FREE_ROAMING;
        }
        if (synced == EntityLockerWorker.SOUND_MODE_WORKING) {
            return LocalMode.WORKING;
        }
        if (synced == EntityLockerWorker.SOUND_MODE_BREAKTIME) {
            return LocalMode.BREAKTIME;
        }
        if (synced == EntityLockerWorker.SOUND_MODE_AFTERWORK_ROAMING) {
            return LocalMode.AFTERWORK_ROAMING;
        }
        if (synced == EntityLockerWorker.SOUND_MODE_WAITING_FOR_BED) {
            return LocalMode.WAITING_FOR_BED;
        }
        if (synced == EntityLockerWorker.SOUND_MODE_CHANGING_CLOTHES) {
            return LocalMode.CHANGING_CLOTHES;
        }
        if (synced == EntityLockerWorker.SOUND_MODE_FIGHTING) {
            return LocalMode.FIGHTING;
        }
        if (synced == EntityLockerWorker.SOUND_MODE_FLEEING) {
            return LocalMode.FLEEING;
        }
        if (synced == EntityLockerWorker.SOUND_MODE_HEALING) {
            return LocalMode.HEALING;
        }
        return LocalMode.NONE;
    }

    private static float pitch(EntityLockerWorker worker, float base, float span) {
        return base + worker.getRNG()
            .nextFloat() * span;
    }

    private static int nextAmbientGap(Random r) {
        return nextGap(r, Config.freeRoamingMinSilenceTicks, Config.freeRoamingMaxSilenceTicks);
    }

    private static int nextBreaktimeGap(Random r) {
        return nextGap(r, Config.breaktimeMinSilenceTicks, Config.breaktimeMaxSilenceTicks);
    }

    private static int nextWorkingGap(Random r) {
        return nextGap(r, Config.workingMinSilenceTicks, Config.workingMaxSilenceTicks);
    }

    private static int nextCombatGap(Random r) {
        return nextGap(r, COMBAT_SILENCE_MIN, COMBAT_SILENCE_MAX);
    }

    private static int nextGap(Random r, int min, int max) {
        if (max < min) {
            max = min;
        }
        if (max == min) {
            return min;
        }
        return min + r.nextInt(max - min + 1);
    }

    /**
     * Count peers already in {@link LocalMode#BREAKTIME} with an active MovingSound
     * within {@link #BREAKTIME_CROWD_RADIUS} of the speaker. Used as a soft anti-chaos
     * cap at crowded trashcans. One-shots are not gated by this.
     */
    private static int countNearbyBreaktimeAmbient(EntityLockerWorker speaker) {
        if (speaker == null) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<Integer, Entry> en : ENTRIES.entrySet()) {
            if (en.getKey()
                .intValue() == speaker.getEntityId()) {
                continue;
            }
            Entry other = en.getValue();
            if (other.localMode != LocalMode.BREAKTIME || other.active == null) {
                continue;
            }
            EntityLockerWorker peer = other.active.getWorker();
            if (peer == null || peer.isDead || peer.worldObj != speaker.worldObj) {
                continue;
            }
            double dx = peer.posX - speaker.posX;
            double dy = peer.posY - speaker.posY;
            double dz = peer.posZ - speaker.posZ;
            if (dx * dx + dy * dy + dz * dz <= BREAKTIME_CROWD_RADIUS_SQ) {
                count++;
            }
        }
        return count;
    }

    private static final class Entry {

        WorkerMovingSound active;
        LocalMode localMode = LocalMode.NONE;
        LocalMode desiredMode = LocalMode.NONE;
        int lastInteractSeq;
        int lastOneshotSeq;
        int silenceLeft;
        int workingIndex;
        int ticksPlaying;
        int maxTicks;
        /** Consecutive ticks SoundHandler reported not playing (protected ambient). */
        int notPlayingStreak;
        /** Queued oneshot kind while protected ambient finishes; -1 = none. */
        int pendingOneshotKind = -1;
    }
}
