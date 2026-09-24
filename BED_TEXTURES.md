# Worker bed + outfit textures (v13 — Martyna)

See also `/workspace/gtnh-textures/NIGHT_UV_NOTES.md`.

## Entity skins (`assets/lockerworker/textures/entity/`)

| File | Use |
|------|-----|
| `locker_worker.png` | Work outfit (unchanged; aggressive skull on locker front unchanged) |
| `worker_afterwork.png` | After-work classic brown-robe villager |
| `worker_pijama.png` | Pajamas — **only while lying in bed** (pastel `#A8D1E7` + white vertical stripes, no hard hat) |
| `worker_pajama.png` | Alt spelling, same bytes as `worker_pijama` (code prefers `worker_pijama`) |

## Bed block faces (`assets/lockerworker/textures/blocks/`)

`BlockWorkerBed` registers vanilla-style names (`lockerworker:bed_*`):

| Registered | File on disk |
|------------|--------------|
| `bed_feet_top` | `bed_feet_top.png` |
| `bed_head_top` | `bed_head_top.png` |
| `bed_feet_end` | `bed_feet_end.png` |
| `bed_head_end` | `bed_head_end.png` |
| `bed_feet_side` | `bed_feet_side.png` |
| `bed_head_side` | `bed_head_side.png` |

Aliases also present: `worker_bed_feet_top.png` … `worker_bed_head_side.png` (same art).
Item icon: `worker_bed_item.png`.

Colors: blanket `#A8D1E7` / white; pillow white; frame dark wood.
