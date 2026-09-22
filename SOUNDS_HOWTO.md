# Locker Worker sounds (v3)

Drop **`.ogg` only** (never mp3) into these folders under the jar / resources:

| Folder | When it plays |
|--------|----------------|
| `assets/lockerworker/sounds/free_roaming/` | Daytime ORBIT / IDLE_WANDER (random gaps) |
| `assets/lockerworker/sounds/working/` | APPROACH_CLOSE / INSPECT_PAUSE (near-continuous cycle) |
| `assets/lockerworker/sounds/interaction/` | Normal right-click on worker |

Empty folder → silent (no crash).

## Forge 1.7.10 `sounds.json`

Minecraft 1.7.10 resolves play names through `assets/lockerworker/sounds.json`.
Classpath discovery finds `.ogg` files and builds play names
`lockerworker:<category>.<basename>` (e.g. file `free_roaming/hum.ogg` →
`lockerworker:free_roaming.hum`).

**You must list each file in `sounds.json`** for clients to hear them:

```json
{
  "free_roaming.hum": {
    "category": "neutral",
    "sounds": ["free_roaming/hum"]
  },
  "working.clank": {
    "category": "neutral",
    "sounds": ["working/clank"]
  },
  "interaction.hey": {
    "category": "neutral",
    "sounds": ["interaction/hey"]
  }
}
```

Paths inside `"sounds"` are relative to `assets/lockerworker/sounds/` and omit `.ogg`.

After editing, rebuild the jar (or put an override resource pack with the same paths).

## Config keys (`sounds` category)

- `soundFreeRoamingEnabled`
- `freeRoamingMinSilenceTicks` / `freeRoamingMaxSilenceTicks`
- `soundWorkingEnabled` / `workingSilenceTicks`
- `soundInteractionEnabled` / `interactionSoundCooldownTicks`

Shift+right-click worker = stay toggle only (no interaction sound required).
