# Config

Two files. The server file is shared by the world. The client file is each player's own machine and is never synced.

| File | Role |
|------|------|
| `config/robolcraft.cfg` | Gameplay, combat, supervisor timing, which sounds are allowed, silence gaps |
| `config/robolcraft-client.cfg` | Volume and hear distance |

In-game **Mods → Config** edits the same files. On a dedicated server, change `walkingSpeed` and restart if workers are already loaded in other dimensions.

20 ticks is one second. Keys named `migrated*` are internal. Leave them alone.

## general

| Key | Default | Range | Meaning |
|-----|---------|-------|---------|
| `machineScanRadius` | 100 | 4–256 | How far a worker looks for GregTech machines |
| `machineScanIntervalTicks` | 40 | 10–200 | Ticks between those scans |
| `maxDistanceFromLocker` | 150 | 0–256 | Work leash applied to a newly placed locker. Each locker stores its own value after that. `0` is unlimited. Does not limit night, break, or going home |
| `trashcanSearchRadius` | 1000 | 0–9999 | How far to search for a trashcan on break. `0` is any trashcan in the dimension |
| `machineSwitchMinTicks` | 400 | 40–12000 | Shortest wait before a worker picks another machine |
| `machineSwitchMaxTicks` | 1800 | 40–24000 | Longest wait. Raised to the minimum if it is smaller |
| `walkingSpeed` | 0.32 | 0.05–1.0 | Movement attribute. Restart recommended after a change |
| `medkitSearchRadius` | 200 | 8–512 | How far an injured worker looks for a medkit |
| `medkitHealRange` | 2 | 0.5–8 | How close they must stand to regenerate |

## sounds_server

These gates and gaps do not apply to one-shots (day start, day end, break cues, smoking, clothes).

| Key | Default | Meaning |
|-----|---------|---------|
| `soundFreeRoamingEnabled` | true | Free-roam clips while orbiting or wandering in the day |
| `freeRoamingMinSilenceTicks` | 300 | Shortest gap for free-roam, after-work walking, and waiting for a bed |
| `freeRoamingMaxSilenceTicks` | 1200 | Longest gap for those |
| `breaktimeMinSilenceTicks` | 100 | Shortest gap for break ambience |
| `breaktimeMaxSilenceTicks` | 300 | Longest gap for break ambience |
| `soundWorkingEnabled` | true | Working clips while standing at a machine, not while walking to it |
| `workingMinSilenceTicks` | 40 | Shortest gap between working clips |
| `workingMaxSilenceTicks` | 80 | Longest gap between working clips |
| `soundInteractionEnabled` | true | Right-click interaction clip on a worker |
| `interactionSoundCooldownTicks` | 20 | Cooldown for that clip. Range 0–200 |

## combat

| Key | Default | Range | Meaning |
|-----|---------|-------|---------|
| `aggressiveModeAllowed` | true | | If false, lockers cannot be set to aggressive |
| `aggressiveAttackDamage` | 3.0 | 0–40 | Damage to hostile mobs |
| `hostileDetectRadius` | 10 | 4–48 | How far an aggressive worker looks for a target |
| `attackPlayers` | false | | If true, aggressive workers may target players |
| `packAggroRadius` | 10 | 0–64 | Nearby aggressive workers adopt the same target. `0` uses `hostileDetectRadius` |

## supervisor

| Key | Default | Range | Meaning |
|-----|---------|-------|---------|
| `supervisorMachineSwitchInterval` | 200 | 40–12000 | Ticks between supervisor machine switches |
| `reportPlayerRadius` | 100 | 8–256 | How far a supervisor walks to deliver a report |
| `reportFollowTicks` | 60 | 10–600 | How long it follows after the report |

## client_audio

In `robolcraft-client.cfg` only.

| Key | Default | Range | Meaning |
|-----|---------|-------|---------|
| `soundVolume` | 1.25 | 0.0–2.0 | Volume for worker and supervisor sounds on this client |
| `soundHearDistance` | 16 | 4–64 | How far those sounds carry, in blocks |
