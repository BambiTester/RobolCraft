# Locker Worker sounds (v5 — jar-only)

Sounds are **baked into the mod jar**. There is no config drop folder and **no
manual `sounds.json` editing** for players or pack authors who rebuild the mod.

## Where clips live (in the jar / sources)

| Folder under `assets/lockerworker/sounds/` | When it plays |
|--------------------------------------------|---------------|
| `free_roaming/` | Daytime ORBIT / IDLE_WANDER (random gaps) |
| `working/` | APPROACH_CLOSE / INSPECT_PAUSE (sequential cycle) |
| `interaction/` | Normal right-click on worker |

Drop **Vorbis `.ogg` only** (never mp3) into those source folders, then
**rebuild the mod** and **restart the game**.

Empty category → silent (no crash).

Shipped defaults: `roam1`/`roam2`, `work1`/`work2`, `hey1`.

## Auto-registration (v5)

On client start / sound reload:

1. `ModSounds.discover()` scans
   `assets/lockerworker/sounds/{free_roaming,working,interaction}/` on the
   classpath (mod jar).
2. `SoundAutoRegister` registers every discovered `.ogg` into the Forge 1.7.10
   `SoundRegistry` (deferred to the tick after `SoundLoadEvent`, because that
   event fires before the registry rebuild).
3. Play lists used by `ClientWorkerSounds` are rebuilt from the same scan.

You do **not** need to edit `assets/lockerworker/sounds.json` for new jar
clips — auto-register creates `lockerworker:<category>.<basename>` events so
`WorkerMovingSound` ResourceLocations resolve. A checked-in `sounds.json` may
still list the defaults as a fallback; missing entries are filled at runtime.

**Live reload:** not supported for newly added jar assets (the jar does not
change mid-session). Prefer **restart** after rebuilding with new `.ogg` files.
F3+T re-runs discovery/registration for clips already inside the loaded jar.

## Exclusivity (kept from v4 — no overlap)

Each worker plays **at most one** clip at a time.

- **Server** (`WorkerSoundManager`): syncs ambient mode via entity datawatcher
  (`NONE` / `FREE_ROAMING` / `WORKING`) and bumps an interaction sequence on
  right-click. Never calls `playSoundAtEntity`.
- **Client** (`ClientWorkerSounds`): map `entityId → active WorkerMovingSound`.
  On mode change or interaction, immediately `SoundHandler.stopSound` the previous
  clip, then start the new one.
- **Working** cycle: next `working/*.ogg` starts only after the previous finished
  (prefer `SoundHandler.isSoundPlaying` end; else `defaultClipLengthTicks`).
- **Interaction** interrupts whatever is playing, then ambient resumes from the
  synced mode.

## Config keys (`sounds` category)

- `soundFreeRoamingEnabled`
- `freeRoamingMinSilenceTicks` / `freeRoamingMaxSilenceTicks`
- `soundWorkingEnabled` / `workingSilenceTicks`
- `soundInteractionEnabled` / `interactionSoundCooldownTicks`
- `defaultClipLengthTicks` — fallback max length per clip (default 40)

Shift+right-click worker = stay toggle only (no interaction sound).
