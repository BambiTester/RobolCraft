# Day / Night / Break AI — Explicit Overworld Tick Window (v7)

Updated: 2026-09-23 (Europe/Warsaw, UTC+2)

## Chosen check

**Explicit tick window** on `world.getWorldTime() % 24000`, wrapped by
`com.angelika.lockerworker.util.WorkerSchedule` (legacy helpers remain in
`VanillaDayNight`).

**Do NOT use `World.isDaytime()` / skylight for worker AI.**

| Phase | Condition (`t = world.getWorldTime() % 24000`) | Examples |
|-------|------------------------------------------------|----------|
| **LOCKER** | `t in [12000, 23999]` inclusive | 12000, 18000, **20000**, 23000–23999 |
| **WORK** | `t in [0, 5999] OR [8001, 11999]` | morning + afternoon |
| **BREAK** | `t in [6000, 8000]` inclusive | noon break |

Helper: `WorkerSchedule.phase(world)` → `LOCKER | WORK | BREAK`.

Constants: `LOCKER_START = 12000`, `BREAK_START = 6000`, `BREAK_END = 8000`.

**v7 change vs v6:** sunrise band `23000–23999` is now **LOCKER** time (was day).

**No custom day/night duration config** — do not add one.

## Behavior

| Phase | Gate | AI |
|-------|------|----|
| WORK | `WorkerSchedule.isWork` | `EntityAIWanderNearMachines` — SEEK → ORBIT → LOOK / APPROACH → INSPECT |
| BREAK | `WorkerSchedule.isBreak` | `EntityAIBreakTime` — trashcan hangout; **machine AI does not run** |
| LOCKER | `WorkerSchedule.isLocker` | Path home; within **1 block** of locker → **enter/despawn into locker** (not stand outside all night) |

### Overnight enter / day release

1. During LOCKER, when the worker reaches ≤1 block of the home locker:
   - Entity despawns (“goes into the locker”) via `TileEntityLocker.storeWorkerOvernight`
   - **75%** `work_exit/` sound at the **locker block** position
   - Redstone waiting = **15** while `workerStored` (same signal as prior waiting-at-locker)
2. When schedule leaves LOCKER into WORK (or BREAK via `/time set`):
   - Worker respawns at locker stand position
   - Into **WORK**: **25%** `day_start/` on the new worker (appear edge)
3. Forced stay / shift-stay: during LOCKER phase, night enter-locker despawn still applies
   (default = vanish overnight). Outside LOCKER, forced stay still stands at the locker.

### Edge cases

- Mid-night `/time set` into LOCKER → path home then despawn + work_exit roll
- `/time set` to day while stored → respawn + day_start roll (if WORK)
- Death mid-night → death-respawn delayed; if still LOCKER when cooldown ends, treat as stored until day

`forcedStayAtLocker` still forces return AI outside LOCKER. Aggressive combat still
applies when relevant. Creepers are never targeted; creepers flee workers (~7 blocks).

Mutual exclusivity: opposite phase gates + shared mutex bit 1; return priority 3,
break priority 4, wander priority 5.

### `/time set` expectations

- `/time set 20000` → LOCKER immediately → path home → enter locker
- `/time set 7000` → BREAK
- `/time set 0` or `1000` → WORK (release if stored)
- Cycle forever with the same windows every 24000 ticks
