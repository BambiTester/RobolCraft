# Art

Textures are vanilla 1.7.10 sizes: entity skins 64×64, block faces 16×16. There is no custom block model. Lockers are two full cubes. Workers use the 1.7.10 villager model, not the player skin layout.

Paths are under `src/main/resources/assets/lockerworker/textures/`.

## Entity skins

| File | Use |
|------|-----|
| `entity/locker_worker.png` | Work outfit |
| `entity/supervisor.png` | Supervisor work outfit (hard hat) |
| `entity/worker_afterwork.png` | After-work clothes, shared by workers and supervisors |
| `entity/worker_pijama.png` | Pajamas, only while lying in bed |

Villager UV, in pixels:

| Part | Offset | Box |
|------|--------|-----|
| Head | (0, 0) | 8×10×8 |
| Headwear overlay | (32, 0) | 8×10×8, inflated by 0.5 |
| Nose | (24, 0) | 2×4×2 |
| Body | (16, 20) | 8×12×6 |
| Robe overlay | (0, 38) | 8×18×6, inflated by 0.5 |
| Legs | (0, 22) | 4×12×4, left mirrored |
| Arms | (44, 22) | 4×8×4 |
| Folded arms | (40, 38) | 8×4×4 |

The headwear overlay is drawn translucent so helmet pixels stay solid and the glasses lenses (about 30% alpha) stay see-through. After-work and pajama skins do not use the glasses.

Reference colors on the work skin: hard hat `#F5C400`, shirt `#E85A1C`, pants `#243A6B` / `#1A2F5A`, boots `#A8E000`, eyes `#2ECC40`, skin `#B57B67` / `#C48A6A`.

## Locker faces

Bottom block: front `locker_bottom_front.png`, sides and back `locker_side.png`, bottom `locker_bottom.png` or `locker_bottom_face.png`. Top is hidden against the upper block.

Top block: front `locker_top_front.png`, aggressive front `locker_top_front_aggressive.png`, sides and back `locker_side.png`, top `locker_top.png`.

Break particles use `locker_particle.png`.

The supervisor locker uses the same face roles with the `supervisor_locker_` prefix, including `supervisor_locker_top_front_aggressive.png`.

## Bed

`BlockWorkerBed` uses the vanilla bed face names:

- `blocks/bed_feet_top.png`, `bed_feet_end.png`, `bed_feet_side.png`
- `blocks/bed_head_top.png`, `bed_head_end.png`, `bed_head_side.png`

The item icon is `textures/items/worker_bed_item.png`. Blanket color is `#A8D1E7` with white stripes, pillow white, frame dark wood.

The worker lies with the head half as the anchor, shifted toward the feet so the head sits on the pillow. Facing uses the vanilla bed yaw (direction 0 = 90°, 1 = 0°, 2 = 270°, 3 = 180°).

## Other blocks

Trashcan: `trashcan_front.png`, `trashcan_side.png`, `trashcan_top.png`, `trashcan_bottom.png`.

Medkit: `medkit_front.png`, `medkit_side.png`, `medkit_back.png`.

The locker GUI background is `textures/gui/locker.png`.
