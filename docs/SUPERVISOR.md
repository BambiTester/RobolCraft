# Shift supervisor

A shift supervisor locker uses the same two-block shape, bed link, Stay, and redstone rules as a worker locker. The recipe swaps the rotten flesh for a gold ingot. It spawns a shift supervisor.

The supervisor uses the work skin `textures/entity/supervisor.png`. Night outfits reuse the worker after-work and pajama skins.

## Shift

The clock is the same as for workers ([GAMEPLAY.md](GAMEPLAY.md)). Reports are delivered during work and break. Overnight, the supervisor does the bed routine and does not chase players to deliver a report. A player who comes into range mid-shift, including during break, gets every undelivered report.

## Machines

The supervisor visits every machine a worker visits, plus the single-block generators and fuel burners listed in [MACHINES.md](MACHINES.md). It switches machines on `supervisorMachineSwitchInterval` (default 200 ticks, about 10 seconds).

Multiblock controllers are skipped. Solar panels, lightning rods, and magic absorbers are not reported as out of fuel.

## Faults

A report can include one or more of these phrases, joined with `"; "`:

1. `its output is blocked`
2. `it has no power while there is work waiting`
3. `it is missing lubricant`
4. `it has run out of fuel`

Chat looks like:

```
N. [Machine name] had a problem: [phrases], it happened today [point me to it] [show me on the map]
```

**[point me to it]** aims the camera at the machine and draws a red outline for 10 seconds. **[show me on the map]** creates a red JourneyMap waypoint when that mod is installed. Combat lines are plain text, without those links.

Older reports say `it happened today` or `it happened X.Y days ago`.

## Memory

Reports are stored on the supervisor locker, not on the entity. They survive the supervisor dying. They are cleared only when that locker is broken.

- Up to 3 machine reports. One machine counts once per calendar day. Oldest drops off when a fourth distinct machine is stored.
- One extra combat slot that does not use those three: the supervisor defeated something, died fighting, the target got away, or the supervisor died another way.
- Delivered reports stay so a later right-click or the GUI **Report** button can show them again.

## Delivery

The supervisor looks for the nearest player inside `reportPlayerRadius` (default 100 blocks). The vertical window is 20 blocks up and 10 blocks down from the supervisor. It runs over, looks at the player, prints every undelivered report in red, plays the report sound, follows for `reportFollowTicks` (default 60 ticks, about 3 seconds), then goes back to work.

If nobody is in range, it keeps working and holds the report.

Right-click the supervisor, or press **Report** in the locker GUI, to print the stored reports without waiting for the automatic walk-up.
