# Day / Night / Break / Bed AI — Explicit Overworld Tick Window (v13)

Updated: 2026-09-24 (Europe/Warsaw, UTC+2)

**v13 NIGHT/BED redesign:** worker **stays in the world** overnight. The old
`workerStored` despawn/appear flow is **removed** (no more vanish into locker,
no redstone output 15 while stored).

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

**No custom day/night duration config** — do not add one.

## Behavior

| Phase | Gate | AI |
|-------|------|----|
| WORK | `WorkerSchedule.isWork` | `EntityAIWanderNearMachines` — SEEK → ORBIT → LOOK / APPROACH → INSPECT |
| BREAK | `WorkerSchedule.isBreak` | `EntityAIBreakTime` — trashcan hangout; **machine AI does not run** |
| LOCKER | `WorkerSchedule.isLocker` | `EntityAINightRoutine` — home → afterwork → bed (or stand) |

### Evening (LOCKER, t ≥ 12000)

1. Path home with unlimited `PathToward`.
2. At locker: work outfit → **afterwork** (`worker_afterwork.png`). Play
   `changing_clothes` 100%, then `locker_sound` 100% + `work_exit` 75%.
3. If linked worker bed exists: walk to bed with `afterwork_roaming` ambient.
   At bed: afterwork → **pijama** (`worker_pijama.png`), `changing_clothes` 100%
   + `get_into_bed` 25%, then **lie in bed** until tick 0.
4. No bed: stay at locker in afterwork.

### Morning (leave LOCKER → WORK, t → 0)

5. Wake, `get_up` 25%. Pajamas **only while in bed** — immediately afterwork for
   the walk. Walk to locker with `afterwork_roaming`.
6. At locker: afterwork → work outfit, `changing_clothes` 100%, then
   `locker_sound` 100% + `day_start` 25%, resume day schedule — **unless forced stay**.

### Forced stay

Triggers: existing **shift-right-click** on worker **OR** redstone **power into**
locker **TOP or BOTTOM**.

Locker **never emits** redstone anymore (legacy `workerStored=15` output removed).

While forced: still do night/bed loop; after morning return to locker stay in
**afterwork** at locker and do **not** start work until force cleared (no redstone
+ player toggles stay off), then change to work and resume.

### Locker ID + bed

- On place: unique durable UUID; give placing player one linked worker-bed item
  (full inv → drop at locker; creative same).
- Bed: vanilla 2-block bed model/behavior, Martyna textures (`bed_*` faces).
  Linked by same UUID; worker paths with `PathToward` (no distance limit;
  chunk-unload limits apply).
- Unplaced bed **item** destroyed → replacement dropped at locker.
- Destroy locker → remove matching bed in world + remove worker.
- Player may share bed: if occupied, the other waiter waits until free.

### Combat

- Afterwork: keep configured aggressive/passive.
- In bed / pajamas: forced peaceful.
- Die while afterwork/pijama: respawn at locker, wait until tick 0, then morning routine.

### Existing worlds

- Old locker no ID: assign ID + one-shot bed-owed → drop linked bed at locker.
- Old `workerStored` overnight: release into afterwork stand/bed flow (no despawn).

### `/time set` expectations

- `/time set 20000` → LOCKER → path home → afterwork → bed/stand
- `/time set 7000` → BREAK
- `/time set 0` or `1000` → WORK (morning clothes at locker if coming from night)
- Cycle forever with the same windows every 24000 ticks

Mutual exclusivity: night routine priority 3, return (forced day) 4, break 5,
wander 6; shared mutex bit 1. Aggressive AI yields during LOCKER / bed.
