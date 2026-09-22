# Day / Night AI — Explicit Overworld Tick Window

Updated: 2026-09-22 21:23 CEST (Europe/Warsaw, UTC+2)

## Chosen check

**Explicit tick window** on `world.getWorldTime() % 24000`, wrapped by
`com.angelika.lockerworker.util.VanillaDayNight`.

**Do NOT use `World.isDaytime()` / skylight for worker AI.** Vanilla
`World.isDaytime()` → `provider.isDaytime()` → `skylightSubtracted < 4` can
disagree with `/time set` (e.g. `/time set 20000` still looked like day to
skylight while players expected night). The tick window reacts immediately and
repeats forever every day cycle.

| Predicate | Condition (`t = world.getWorldTime() % 24000`) | Examples |
|-----------|------------------------------------------------|----------|
| **Night** | `t >= 12000 && t < 23000` | 13000, 18000, **20000**, 22000 |
| **Day** | `t < 12000 \|\| t >= 23000` | 0–11999; sunrise band 23000–23999 |

Constants in code: `VanillaDayNight.NIGHT_START = 12000`, `NIGHT_END = 23000`.

**No custom day/night duration config** — `Config.java` has none; do not add one.

## Behavior

| Phase | Gate | AI |
|-------|------|----|
| Day | `VanillaDayNight.isDaytime` | `EntityAIWanderNearMachines` — SEEK → ORBIT → LOOK / APPROACH (~1 block) → INSPECT; switch machines; on night transition `resetTask` clears navigator |
| Night | `VanillaDayNight.isNighttime` | `EntityAIReturnToLocker` — path home all night; when close, stand in front facing locker until day; `resetTask` clears navigator |

Mutual exclusivity: opposite day/night gates + shared mutex bit 1; return task priority 2, wander priority 3.

### `/time set` expectations

- `/time set 20000` → night immediately → day AI `shouldExecute` / `continueExecuting` false; night return AI active
- On day AI stop (`resetTask`) / when night starts: navigator `clearPathEntity`
- Cycle forever with the same windows every 24000 ticks
