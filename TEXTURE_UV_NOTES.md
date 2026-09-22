# GTNH Factory Worker / Locker UV Notes

## Villager skin
- **File:** `factory_worker_villager.png`
- **Size:** 64×64 PNG, RGBA, nearest-neighbor pixel art
- **UV:** Standard Minecraft **villager** layout (pre-1.14 / 1.7.10 `ModelVillager`), **not** player-skin layout
- Reference matched against vanilla `assets/minecraft/textures/entity/villager/villager.png` (1.7.10)

### ModelVillager UV (verified)
| Part | Texture offset | Box (W×H×D) |
|------|----------------|-------------|
| Head | (0, 0) | 8×10×8 |
| Hat overlay | (32, 0) | 8×10×8 (+0.5) |
| Nose | (24, 0) | 2×4×2 |
| Body | (16, 20) | 8×12×6 |
| Robe overlay | (0, 38) | 8×18×6 (+0.5) |
| Legs (both; left mirrored) | (0, 22) | 4×12×4 |
| Arms (L/R boxes) | (44, 22) | 4×8×4 each |
| Arms middle (folded) | (40, 38) | 8×4×4 |

**Assumption:** Head is **8×10×8** (classic villager), not 8×8. Hat brim/ridge and goggle highlights live on the hat overlay; green eyes + goggle frames on head front; short-sleeve orange on arms (upper) with skin forearms; robe overlay = orange upper + navy lower so pants read under the outer layer; legs = navy + lime boots + black soles.

## Locker (2-tall, no custom model)
Simple two-block column. Mod Dev wires cube faces to these PNGs.

### Bottom block (y = base)
| Face | Texture |
|------|---------|
| Front / North (door) | `locker_bottom_front.png` |
| Back | `locker_side.png` (or same as side) |
| Left / Right | `locker_side.png` |
| Top | (hidden against upper block — unused) |
| Bottom | `locker_bottom.png` / `locker_bottom_face.png` |

### Top block (y = base+1)
| Face | Texture |
|------|---------|
| Front / North (door) | `locker_top_front.png` |
| Back | `locker_side.png` |
| Left / Right | `locker_side.png` |
| Top | `locker_top.png` |
| Bottom | (hidden against lower block — unused) |

### Particles
- `locker_particle.png` — average steel for break/hit particles

Handle/latch are aligned on the right of both front doors so the 2-tall unit reads as one locker.

## Color hex summary
| Role | Hex |
|------|-----|
| Hard hat / latch | `#F5C400` |
| Shirt / orange stripe | `#E85A1C` |
| Pants / navy accent | `#243A6B` / `#1A2F5A` |
| Boots lime | `#A8E000` |
| Eyes | `#2ECC40` |
| Skin | `#B57B67` / `#C48A6A` |
| Steel light | `#8A93A0` |
| Steel mid | `#6B7380` |
| Steel dark | `#4A5160` |

## Notes for Mod Dev
- No custom JSON/Techne model required: vanilla villager entity skin + two full blocks stacked.
- All textures are vanilla 1.7.10 resolutions (entity 64×64, blocks 16×16).
- Unused UV pixels left transparent.
