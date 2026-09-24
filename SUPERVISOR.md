# Shift Supervisor (lockerworker-14)

Red-steel supervisor locker + `EntityShiftSupervisor` that inspects machines and reports faults.

## Placement / recipe

- Block: `supervisor_locker` (2-tall, same place/TE/UUID/bed-link/forced-stay as worker locker)
- Recipe: iron bars + **gold ingot** / iron bars + dirt (worker locker uses rotten flesh)
- Spawns `EntityShiftSupervisor` (work skin `textures/entity/supervisor.png` — white hard hat)
- Night outfits: reuse `worker_afterwork.png` / `worker_pijama.png`
- Aggressive skull toggle: same as workers (`supervisor_locker_top_front_aggressive.png`)

## Schedule / shift window

Same phases as workers (`WorkerSchedule`):

| Phase | Ticks |
|-------|-------|
| WORK | 0–5999 & 8001–11999 |
| BREAK | 6000–8000 |
| LOCKER | 12000–23999 |

**Shift** = WORK + BREAK (morning leave through tick 12000). Overnight afterwork/bed does **not** chase reports.

## Machine whitelist

`SupervisorMachineIds` = all `ProcessingMachineIds` **plus** single-block generators / fuel burners:

- Boilers 100, 101, 102, 105, 114
- Semi-fluid 837–839, 993–994
- Combustion/diesel 1110–1114
- Gas turbines 1115–1119
- Steam turbines 1120–1122
- Magic energy 1123–1125, 1127–1130
- Lightning rods 1174–1176
- Naquadah reactors 1188–1192
- Plasma generators 1196–1198, 10752–10753
- Solar panels 2729, 2733–2740
- Acid generators 12726–12728, 12742, 12793

Workers still use `ProcessingMachineIds` only (unchanged).

## Fault phrases (exact)

Joined with `"; "` when multiple apply:

1. `its output is blocked` — `MTEBasicMachine.mOutputBlocked > 0`
2. `it has no power while there is work waiting` — `mStuttering` or no EU with inputs / mid-recipe
3. `it is missing lubricant` — recipe map accepts lubricant fluid but fillable empty (with inputs)
4. `it has run out of fuel` — generators/boilers with empty fuel fluid/slot (`mProcessingEnergy` for boilers)

Chat line:

```
N. [Machine name] at this coordinates X, Y, Z had a problem: [phrases], it happened today
```

Age wording on right-click / aged memory: `it happened today` or `it happened X.Y days ago` (`worldTime/24000`).

## Auto-report / memory

- Nearest player within `reportPlayerRadius` (default 100)
- Run → look → dump **all** undelivered → `supervisor_report` sound → follow `reportFollowTicks` (default 60) → resume
- No player: keep working; store in memory
- End of shift: stop chasing that day’s undelivered set; leave in memory
- Player enters mid-shift with pending: interrupt and deliver all
- Memory: up to **3** machine reports (unique machine per calendar day, FIFO); delivered entries stay for right-click
- **4th slot (combat, aggressive):** does not consume machine slots — defeated / died fighting / got away / died other

Right-click: dump last reports + `supervisor_ask_report` only (no generic interaction sound).

## Config (`supervisor` category)

| Key | Default |
|-----|---------|
| `supervisorMachineSwitchInterval` | 200 |
| `reportPlayerRadius` | 100 |
| `reportFollowTicks` | 60 |

## Sounds (auto-scan folders)

- `sounds/supervisor_ask_report/` — 100% on right-click report ask
- `sounds/supervisor_report/` — 100% on auto-deliver
- All other ambient folders shared with workers

## GT reflection limits (Angelika)

**Supported (single-block):** `MTEBasicMachine` output/power/lube; `MTEBasicGenerator` / `MTEBoiler` fuel-empty.

**Skipped / not reliable for v14:**

- **Multiblock controllers** (`MTEMultiBlockBase` and subclasses) — lubricant / maintenance / hatch power / output-blocked vary too much by type; supervisor **skips** these rather than guessing. Flagged via `FaultResult.skippedMultiblock`.
- Formed-structure hatch aggregation (energy hatches, output buses) — not probed.
- GT++ / GoodGenerator / TecTech multiblocks — out of scope.
- Solar / lightning / magic absorbers — not flagged as “out of fuel” (not fuel-burning).

If Angelika wants multiblock coverage later: per-controller reflection allowlist with tested field/method names only.

## Textures (Martyna)

Exact paths under `assets/lockerworker/textures/`:

- Blocks: `supervisor_locker_*.png` (bottom, bottom_face, bottom_front, side, top, top_front, top_front_aggressive, particle, optional supervisor_locker.png)
- Entity work: `entity/supervisor.png`
- Night: reuse worker afterwork/pijama
