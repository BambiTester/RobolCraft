# Locker Worker sounds (v7 — jar-only)

Sounds are **baked into the mod jar**. There is no config drop folder and **no
manual `sounds.json` editing** for players or pack authors who rebuild the mod.

## Where clips live (in the jar / sources)

| Folder under `assets/lockerworker/sounds/` | When it plays |
|--------------------------------------------|---------------|
| `free_roaming/` | WORK ORBIT / IDLE_WANDER (random gaps) |
| `working/` | APPROACH_CLOSE / INSPECT_PAUSE (sequential cycle) |
| `interaction/` | Normal right-click on worker |
| `breaktime/` | BREAK ambient (random gaps) |
| `breaktime_start/` | 25% on enter BREAK (edge, once per transition) |
| `breaktime_end/` | 25% on leave BREAK → WORK/LOCKER |
| `day_start/` | 25% when leaving LOCKER into morning WORK (t→0) |
| `day_end/` | 25% when entering LOCKER (t→12000) |
| `smoking/` | Each smoking exhale during BREAK (+ soft smoke particles) |
| `work_exit/` | 75% at **locker block** when worker enters/despawns overnight |

Drop **Vorbis `.ogg` only** (never mp3) into those source folders, then
**rebuild the mod** and **restart the game**.

Empty category → silent (no crash).

Shipped defaults: `roam1`/`roam2`, `work1`/`work2`, `hey1`. New v7 folders ship
empty until clips are added.

## Auto-registration (v5+, extended in v7)

On client start / sound reload:

1. `ModSounds.discover()` scans all category folders above on the classpath
   (mod jar).
2. `SoundAutoRegister` registers every discovered `.ogg` into the Forge 1.7.10
   `SoundRegistry` (deferred to the tick after `SoundLoadEvent`).
3. Play lists used by `ClientWorkerSounds` are rebuilt from the same scan.

You do **not** need to edit `assets/lockerworker/sounds.json` for new jar
clips — auto-register creates `lockerworker:<category>.<basename>` events.

**Live reload:** not supported for newly added jar assets. Prefer **restart**
after rebuilding. F3+T re-runs discovery/registration for clips already inside
the loaded jar.

## Exclusivity (kept — no overlap)

Each worker plays **at most one** clip at a time.

- **Server** (`WorkerSoundManager` + entity schedule edges): syncs ambient mode
  via datawatcher (`NONE` / `FREE_ROAMING` / `WORKING` / `BREAKTIME`) and bumps
  interaction / one-shot sequences. Never calls `playSoundAtEntity`.
- **Client** (`ClientWorkerSounds`): map `entityId → active WorkerMovingSound`.
  On mode change, interaction, or one-shot, immediately `stopSound` the previous
  clip, then start the new one.
- **Working** cycle: next `working/*.ogg` starts only after the previous finished.
- **Volume / hear distance:** `Config.soundVolume` and `Config.soundHearDistance`
  apply to entity moving sounds (breaktime, day_start/end, smoking, etc.) via
  `WorkerMovingSound`. `work_exit` plays at the locker via `playSoundEffect`
  using `soundVolume` (vanilla attenuation from the block).

## Config keys (`sounds` category)

- `soundFreeRoamingEnabled`
- `freeRoamingMinSilenceTicks` / `freeRoamingMaxSilenceTicks`
- `soundWorkingEnabled` / `workingSilenceTicks`
- `soundInteractionEnabled` / `interactionSoundCooldownTicks`
- `defaultClipLengthTicks` — fallback max length per clip (default 40)
- `soundVolume` — master multiplier (0–2, default 1.0)
- `soundHearDistance` — max hear blocks (4–64, default 16)

Shift+right-click worker = stay toggle only (no interaction sound).
