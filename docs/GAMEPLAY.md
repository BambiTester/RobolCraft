# Gameplay

Workers and supervisors share one clock. It is the overworld day tick, `world.getWorldTime() % 24000`, wrapped by `WorkerSchedule`. Skylight and `World.isDaytime()` are not used. There is no config for day length.

| Phase | Ticks | What they do |
|-------|-------|----------------|
| Work | 0–5999 and 8001–11999 | Path to whitelisted GregTech machines and stand beside them |
| Break | 6000–8000 | Leave the machines and hang out at a trashcan |
| Night | 12000–23999 | Go home, change clothes, sleep if a bed is linked |

`/time set 1000` is work, `/time set 7000` is break, `/time set 20000` is night.

## Locker

A worker locker is two blocks tall. Both halves open the same GUI. Breaking either half in survival drops the locker. It faces the player when placed.

Placing a locker:

- Assigns a durable id and a short label (`Worker N` / `Supervisor N`)
- Spawns the bound entity
- Gives the placer one Worker Bed item linked to that locker (dropped at the locker if the inventory is full)

The GUI sets Stay, Aggressive or Passive, and the daytime work distance for this locker. Stay can also be forced by redstone power into the top or bottom half. The locker does not emit redstone.

Destroying the locker removes its worker and its linked bed in the world.

## Work

During work the worker scans for single-block GregTech processing machines on the whitelist in [MACHINES.md](MACHINES.md). Pipes, cables, hatches, hulls, and multiblock controllers are ignored. The worker will not stand on GregTech pipes or cables.

It switches machines on a random interval (`machineSwitchMinTicks`–`machineSwitchMaxTicks`). The locker's work distance limits how far it roams during the day. Night, break, and a forced return home are not limited by that leash.

## Break

From tick 6000 to 8000 the machine AI stops. The worker walks to the nearest trashcan within `trashcanSearchRadius` (default 1000; `0` means any trashcan in the dimension). While waiting it can smoke. At the end of break it goes back to work, or home if the clock has reached night.

## Night and bed

At tick 12000 the worker paths home with no distance cap.

1. At the locker it changes from the work outfit into after-work clothes, then stands for 3 seconds.
2. If a linked bed exists, it walks there, changes into pajamas, and lies down until morning. The head rests on the pillow half.
3. If no bed is linked, it waits at the locker in after-work clothes. Placing the linked bed later the same night sends it to bed.
4. If the bed is already occupied, it waits until the bed is free.

At morning (tick 0) it gets up in after-work clothes, walks back to the locker, and changes into the work outfit.

If Stay is on, or the locker is powered, it still does the night routine, then remains at the locker in after-work clothes and does not start work until Stay is off and the redstone is gone.

Pajamas are only worn in bed. Dying in after-work clothes or pajamas respawns the worker at the locker; it waits until morning to dress for work. Death uses the vanilla villager death sound.

## Medkit

A medkit is a wall block. A worker or supervisor under half health paths to the nearest medkit within `medkitSearchRadius` (default 200). Within `medkitHealRange` (default 2 blocks) it regenerates 1 health (half a heart) every 2.5 seconds until full.

## Combat

Aggressive mode is per locker, from the GUI, and only if `aggressiveModeAllowed` is true.

- Targets are hostile mobs the worker can see. Detection radius defaults to 10 blocks.
- Players are not targets unless `attackPlayers` is true.
- When one aggressive worker picks a target, other aggressive workers inside `packAggroRadius` adopt it.
- After-work clothes keep the aggressive or passive setting. In bed the worker is peaceful.
- Creepers move away from workers and supervisors, and an ignited creeper does not explode while one is next to it.

## Doors

Workers open vanilla wooden trapdoors that block the path. If MalisisDoors is loaded they also open its doors, trapdoors, fence gates, and garage doors. Digicode doors and redstone-only doors are left alone.
