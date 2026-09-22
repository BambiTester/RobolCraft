# Locker Worker sounds (v4)

Drop **`.ogg` only** (never mp3) into these folders under the jar / resources:

| Folder | When it plays |
|--------|----------------|
| `assets/lockerworker/sounds/free_roaming/` | Daytime ORBIT / IDLE_WANDER (random gaps) |
| `assets/lockerworker/sounds/working/` | APPROACH_CLOSE / INSPECT_PAUSE (sequential cycle) |
| `assets/lockerworker/sounds/interaction/` | Normal right-click on worker |

Empty folder → silent (no crash).

## Exclusivity (v4 — no overlap)

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

## Forge 1.7.10 `sounds.json`

Classpath discovery builds play names `lockerworker:<category>.<basename>`.
**List each file in `sounds.json`** for clients to hear them (see examples below).

```json
{
  "free_roaming.hum": { "category": "neutral", "sounds": ["free_roaming/hum"] },
  "working.clank": { "category": "neutral", "sounds": ["working/clank"] },
  "interaction.hey": { "category": "neutral", "sounds": ["interaction/hey"] }
}
```

Paths inside `"sounds"` are relative to `assets/lockerworker/sounds/` and omit `.ogg`.

## Config keys (`sounds` category)

- `soundFreeRoamingEnabled`
- `freeRoamingMinSilenceTicks` / `freeRoamingMaxSilenceTicks`
- `soundWorkingEnabled` / `workingSilenceTicks`
- `soundInteractionEnabled` / `interactionSoundCooldownTicks`
- `defaultClipLengthTicks` — fallback max length per clip (default 40)

Shift+right-click worker = stay toggle only (no interaction sound).
