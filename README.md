# RobolCraft

Factory workers and shift supervisors for GregTech New Horizons. A locker binds one worker to your machines. They keep a day schedule, take a break, sleep in a linked bed, and can fight hostiles. A supervisor locker binds a shift supervisor who inspects machines and reports faults in chat.

Made by BambiHero and Grubiorz14.

- **Minecraft:** 1.7.10
- **Forge:** 10.13.4.1614
- **Requires:** GregTech (GTNH)
- **Mod id:** `lockerworker` (kept so existing worlds still load)
- **Version:** 1.0.0

## Install

1. Use a GTNH 1.7.10 instance.
2. Copy [`release/robolcraft-1.0.0.jar`](release/robolcraft-1.0.0.jar) into the instance `mods/` folder.
3. Start the pack. Config appears at `config/robolcraft.cfg` and, on the client, `config/robolcraft-client.cfg`.

An older `lockerworker.cfg` is copied forward once on first load.

## What you can build

| Block | Recipe | What it does |
|-------|--------|----------------|
| Worker Locker | Iron bars + rotten flesh / iron bars + dirt (2×2) | Spawns a worker. Placing it also gives you one linked Worker Bed. |
| Shift Supervisor Locker | Same shape, gold ingot instead of rotten flesh | Spawns a shift supervisor. |
| Trashcan | Five iron bars in a U | Break hangout. Workers walk to the nearest one. |
| Medkit | Eight iron bars around white wool | Wall station. Injured workers walk here and regenerate. |
| Worker Bed | Not crafted. Given when you place a locker. | Night sleep for the worker linked to that locker. |

Right-click a locker (top or bottom) to open its GUI.

- **Stay** keeps the worker at the locker after morning instead of starting the shift. Redstone power into the top or bottom half does the same. The locker never emits redstone.
- **Aggressive / Passive** toggles combat. Aggressive workers attack hostiles they can see. They do not attack players unless `attackPlayers` is turned on.
- **Work distance** is that locker's daytime leash in blocks. `0` means no leash. New lockers start from `maxDistanceFromLocker` (default 150).
- **Report** (supervisor locker only) prints the stored machine and combat reports in chat.

Right-click the worker or supervisor to see which locker they belong to. Right-click a supervisor to hear the current report. Shift-right-click a linked bed to see its locker id.

## Day

The schedule uses the overworld clock (`world time % 24000`), not skylight.

| Time | Phase |
|------|--------|
| 0–5999 and 8001–11999 | Work, near whitelisted GregTech machines |
| 6000–8000 | Break, at a trashcan |
| 12000–23999 | Night: change clothes, walk to the linked bed, sleep |

Details, outfits, combat, medkits, and doors are in [docs/GAMEPLAY.md](docs/GAMEPLAY.md). Supervisors are in [docs/SUPERVISOR.md](docs/SUPERVISOR.md).

## Reports

When a supervisor finds a fault, chat can include two links:

- **[point me to it]** turns your camera toward the machine and draws a red outline for 10 seconds.
- **[show me on the map]** adds a red JourneyMap waypoint when JourneyMap is installed.

Those links run `/robolcraft lookat` and `/robolcraft jmwp` for you. You do not need to type them. They only work as the player who clicked.

## Docs

| Doc | Topic |
|-----|--------|
| [docs/GAMEPLAY.md](docs/GAMEPLAY.md) | Schedule, lockers, beds, break, medkit, combat, doors |
| [docs/SUPERVISOR.md](docs/SUPERVISOR.md) | Reports, memory, fault phrases |
| [docs/SOUNDS.md](docs/SOUNDS.md) | Sound folders and how clips are picked |
| [docs/CONFIG.md](docs/CONFIG.md) | `robolcraft.cfg` and `robolcraft-client.cfg` |
| [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) | GregTech, MalisisDoors, JourneyMap |
| [docs/MACHINES.md](docs/MACHINES.md) | GregTech machine ids workers will visit |
| [docs/BUILDING.md](docs/BUILDING.md) | Build the jar from source |
| [docs/ART.md](docs/ART.md) | Texture layout |
| [CHANGELOG.md](CHANGELOG.md) | Release history |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to change the mod |

## Build

Java 25, then from this folder:

```bash
./gradlew syncReleaseJar -x test
```

That writes `build/libs/robolcraft-<version>.jar` and replaces the single file in `release/`. See [docs/BUILDING.md](docs/BUILDING.md).
