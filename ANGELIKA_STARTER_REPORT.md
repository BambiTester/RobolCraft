# Locker Worker — Starter Inspection Report (for Angelika / PM)

Date: 2026-09-22 (Europe/Warsaw, UTC+2)  
Scope: Unpack + inspect ExampleMod starter; public research only. **No full mod implementation.**

---

## 1. Unpacked starter path

**Exact path:** `/workspace/gtnh-locker-mod`

Source zip:  
`/home/box/agent-data/agents/8d420037-efd7-46c2-945e-91251b5c10cb/attachments/33ba88c40583451659607380a19abdf550c644c4e8a3b4fefbf16f40051154f3.zip`

This matches the official GTNH ExampleMod starter layout (not a git clone of the full ExampleMod repo). Official README warns: do **not** clone/fork the ExampleMod repo; use the starter zip.

Cite: https://github.com/GTNewHorizons/ExampleMod1.7.10/blob/master/README.md  
Raw README: https://raw.githubusercontent.com/GTNewHorizons/ExampleMod1.7.10/master/README.md

---

## 2. Project inspection (local files)

### Build system
| Item | Value (from files) |
|------|--------------------|
| Plugin | `com.gtnewhorizons.gtnhconvention` (`build.gradle.kts`) |
| Settings plugin | `com.gtnewhorizons.gtnhsettingsconvention` **2.0.20** (`settings.gradle.kts`) |
| Gradle wrapper | **9.3.1** (`gradle/wrapper/gradle-wrapper.properties`) |
| MC / Forge | **1.7.10** / **10.13.4.1614** (`gradle.properties`) |
| MCP | channel `stable`, mappings `12` |
| Java toolchain intent | `.java-version` = **25**; daemon JVM `toolchainVersion=25` |
| Modern syntax | `enableModernJavaSyntax = jabel` → syntax sugar, **Java 8 bytecode** |
| Mixins | `usesMixins = false` (keep off unless needed) |
| Deps file | `dependencies.gradle` (empty except commented NEI example) |

### Placeholders to rename
| Property / location | Current |
|---------------------|---------|
| `modName` | `MyMod` |
| `modId` | `mymodid` |
| `modGroup` | `com.myname.mymodid` |
| `generateGradleTokenClass` | `com.myname.mymodid.Tags` |
| Main class | `com.myname.mymodid.MyMod` |
| Proxies | `CommonProxy` / `ClientProxy` |
| `mcmod.info` | uses `${modId}`, `${modName}`, `${modVersion}`, `${minecraftVersion}` |

**How to rename (official README step 4):** edit `gradle.properties`, then rename package directory + Java classes to match `modGroup`; update `@Mod`, `@SidedProxy` strings, and any hard-coded `MODID` constants in `MyMod.java`.

### Source tree (starter)
```
src/main/java/com/myname/mymodid/
  MyMod.java, Config.java, CommonProxy.java, ClientProxy.java
src/main/resources/
  mcmod.info, LICENSE
```

### Build / run commands (from ExampleMod README + RFG/GTNH convention)

Official getting-started:
```bash
cd /workspace/gtnh-locker-mod
./gradlew setupDecompWorkspace
./gradlew build
```

Typical run tasks provided by the GTNH RetroFuturaGradle convention (same family as ExampleMod):
```bash
./gradlew runClient
./gradlew runServer
# optional username override (documented in gradle.properties comments):
./gradlew runClient --username=AnotherPlayer
```

**Note for this box:** `./gradlew` failed here with `JAVA_HOME is not set / no java in PATH`. Mod Dev needs JDK **25** (or whatever matches `.java-version`) installed before first setup. Pack players may run GTNH on Java 8 or 17–25; **bytecode target is still 8** via Jabel.

Cite: ExampleMod README “Getting started”; `gradle.properties` comments for `runClient` / username.

---

## 3. Recommended naming (Locker Worker)

| Role | Recommendation |
|------|----------------|
| Display name | `Locker Worker` |
| **modId** | `lockerworker` (lowercase, no spaces — Forge convention) |
| **modGroup / package** | `com.angelika.lockerworker` *(or team org if preferred)* |
| Main `@Mod` class | `LockerWorkerMod` |
| Tokens class | `com.angelika.lockerworker.Tags` |
| Proxies | `CommonProxy`, `ClientProxy` under same package |

`gradle.properties` sketch:
```
modName = Locker Worker
modId = lockerworker
modGroup = com.angelika.lockerworker
generateGradleTokenClass = com.angelika.lockerworker.Tags
```

`@Mod` should eventually declare GregTech dependency, e.g.  
`dependencies = "required-after:gregtech;"`  
(GregTech modid is literally **`gregtech`** — see Mods.ModIDs below.)

---

## 4. Detecting GregTech machines nearby (GTNH / GT5-Unofficial 1.7.10)

**Use GTNH GregTech (`GTNewHorizons/GT5-Unofficial`), not GregTechCE / CEu (1.12+).** Package names differ.

### Fact: one block, many machines (meta-tile entities)

- Shared block class: `gregtech.common.blocks.BlockMachines`  
  Cite: https://raw.githubusercontent.com/GTNewHorizons/GT5-Unofficial/master/src/main/java/gregtech/common/blocks/BlockMachines.java  
- Global block ref: `GregTechAPI.sBlockMachines`  
  Cite: https://raw.githubusercontent.com/GTNewHorizons/GT5-Unofficial/master/src/main/java/gregtech/api/GregTechAPI.java  
- Tile holder TE: `BaseMetaTileEntity` implements `IGregTechTileEntity`  
  Cite: https://raw.githubusercontent.com/GTNewHorizons/GT5-Unofficial/master/src/main/java/gregtech/api/metatileentity/BaseMetaTileEntity.java  
- Logic object: `IMetaTileEntity` via `IGregTechTileEntity.getMetaTileEntity()`  
  Cite: https://raw.githubusercontent.com/GTNewHorizons/GT5-Unofficial/master/src/main/java/gregtech/api/interfaces/tileentity/IGregTechTileEntity.java  

### Concrete approaches for entity AI (preferred order)

**A. TileEntity scan (best for “is this a GT machine?”)**  
In a radius around the entity (server tick / AI task):

```text
TileEntity te = world.getTileEntity(x, y, z);
if (te instanceof IGregTechTileEntity igt && igt.canAccessData()) {
    IMetaTileEntity mte = igt.getMetaTileEntity();
    // mte != null → valid machine (or pipe holder depending on TE type)
}
```

Helper already in GT API:
```text
IMetaTileEntity mte = GTUtility.getMetaTileEntity(te);
// returns null unless te instanceof IGregTechTileEntity && canAccessData()
```
Cite: `GTUtility.getMetaTileEntity(TileEntity)` in  
https://raw.githubusercontent.com/GTNewHorizons/GT5-Unofficial/master/src/main/java/gregtech/api/util/GTUtility.java (lines ~2816–2820)

**B. Block identity (fast pre-filter)**  
```text
Block b = world.getBlock(x, y, z);
if (b == GregTechAPI.sBlockMachines) { /* then check TE */ }
// or: b instanceof BlockMachines
```
`BlockMachines` registers itself as a machine block with meta mask `-1` (all metas).

**C. Multiblock casings / “machine block update” blocks (NOT the same as machines)**  
`GregTechAPI.isMachineBlock(block, meta)` / `sMachineIDs` marks blocks that **conduct machine-block updates** (casings etc.). Do **not** treat this alone as “working machine” for locker AI unless that is intentional.

**D. Pipes vs machines**  
Same `gt.blockmachines` block creates either `BaseMetaTileEntity` (machines) or `BaseMetaPipeEntity` (cables/pipes) depending on meta. If AI should ignore cables, require `instanceof BaseMetaTileEntity` (or check MTE type), not only `IGregTechTileEntity`.

**E. Optional: filter by MTE class / ID**  
Examples of real GTNH MTE types: `MTEBasicMachine`, hatches `MTEHatch`, multiblocks under `gregtech.common.tileentities.machines.multi.*`.  
`getMetaTileID()` / `GregTechAPI.METATILEENTITIES[id]` exist for ID-based checks — pin IDs carefully; they can change between pack versions.

### Compile dependency (do not copy GT sources into the mod)

GregTechAPI header explicitly warns **not** to ship/copy API files into your jar (breaks compatibility / version checks). Depend via Maven instead.

Example pattern used by NH CoreMod (version pin to **2.8.4’s** GT jar in practice):
```gradle
api("com.github.GTNewHorizons:GT5-Unofficial:<version-matching-2.8.4>:dev")
```
Cite: https://raw.githubusercontent.com/GTNewHorizons/NewHorizonsCoreMod/master/dependencies.gradle  
(`api("com.github.GTNewHorizons:GT5-Unofficial:5.09.54.170:dev")` — **that number is from master CoreMod, not verified as the exact 2.8.4 pin**; Mod Dev must match the GregTech jar version shipped in GTNH 2.8.4.)

GregTech `@Mod` modid constant: `Mods.ModIDs.GREG_TECH = "gregtech"`  
Cite: https://raw.githubusercontent.com/GTNewHorizons/GT5-Unofficial/master/src/main/java/gregtech/api/enums/Mods.java

---

## 5. GTNH 2.8.4 client + server compatibility expectations

| Topic | Expectation | Cite |
|-------|-------------|------|
| Pack version | Client and server must run **exact same GTNH version** (e.g. both 2.8.4). Java major can differ between client and server. | https://wiki.gtnewhorizons.com/wiki/Server_Setup |
| 2.8.4 status | Stable release (2025-12-23); bugfix over 2.8.3 | https://wiki.gtnewhorizons.com/wiki/Version_2.8.4 |
| Custom mod | Soft-required mods: put jar in **both** client and server `mods/` if it has game logic / entities. Client-only rendering must stay client-side (`@SideOnly` / proxy). Missing/mismatched mods → Forge “Mod rejections”. | GTNH issues / FML reject logs (e.g. pack issue discussions) |
| `@Mod dependencies` | Declare `required-after:gregtech` (and any other hard deps) so load order and missing-deps errors are clear. Soft/optional: `after:…` + `Loader.isModLoaded` / `Mods.GregTech.isModLoaded()`. | GTMod.java dependency string pattern |
| Mixins | Starter has `usesMixins = false`. Enabling mixins pulls UniMixins automatically (ExampleMod README). **Avoid mixins for v1** of Locker Worker — high breakage risk across GTNH updates; prefer public API (`IGregTechTileEntity` / `IMetaTileEntity`). | ExampleMod README Mixins section |
| API stability | Prefer public interfaces under `gregtech.api.interfaces.*`. `BaseMetaTileEntity` file header: “NEVER INCLUDE THIS FILE IN YOUR MOD!!!” | BaseMetaTileEntity.java |
| Dev vs pack | Dev `runClient` with only GT5-Unofficial ≠ full 2.8.4 pack. Integration-test against a 2.8.4 instance before release. | Pack practice / Server Setup |

---

## 6. First code files Mod Dev should create (after rename)

Do **not** implement the full feature set yet — scaffold only:

1. **Rename placeholders** — `gradle.properties` + move package to `com.angelika.lockerworker` + rename `MyMod` → `LockerWorkerMod`.
2. **`dependencies.gradle`** — add `api("com.github.GTNewHorizons:GT5-Unofficial:<2.8.4-matching>:dev")` (+ transitive needs if compile fails: GTNHLib / StructureLib usually come transitively).
3. **`LockerWorkerMod.java`** — `@Mod(modid=…, dependencies="required-after:gregtech;")`, keep proxy pattern.
4. **`entity/EntityLockerWorker.java`** (or similar) — extends `EntityCreature` / appropriate AI host; register in `init`.
5. **`entity/ai/EntityAIFindGregTechMachine.java`** — radius scan using approach **A/B** above; cache last target every N ticks.
6. **`util/GregTechMachineLookup.java`** — thin wrapper around `GTUtility.getMetaTileEntity` / `GregTechAPI.sBlockMachines` so AI stays readable and GT-optional-safe.
7. Update **`mcmod.info`** description/authors; fill **LICENSE** from `LICENSE-template`.

Optional later: `Config.java` entries for search radius / tick interval; client render only if custom model.

---

## 7. Risks checklist (hand to Mod Dev)

1. Confusing **GT CEu / BlockMachine (1.12)** APIs with **GT5U `BlockMachines` (1.7.10)** — wrong packages.
2. Treating **casings** (`isMachineBlock`) as interactable machines.
3. Counting **cables/pipes** (`BaseMetaPipeEntity`) as locker targets.
4. Copying GregTech source into the mod jar (API version check / legal / update hell).
5. Enabling **mixins** without need — pack update breakages.
6. Shipping client-only vs common mismatch → multiplayer reject.
7. Pinning wrong GT5 Maven version vs jars inside GTNH **2.8.4**.
8. Scanning every tick in large radius — TPS cost; throttle + chunk-loaded checks.

---

## 8. Evidence index (no private APIs)

| Claim | Evidence |
|-------|----------|
| Starter usage / rename / setupDecompWorkspace / build | ExampleMod README |
| Mixins caution / UniMixins | ExampleMod README Mixins |
| `IGregTechTileEntity.getMetaTileEntity()` | GT5-Unofficial IGregTechTileEntity.java |
| `BlockMachines` / `gt.blockmachines` | BlockMachines.java |
| `GregTechAPI.sBlockMachines`, `isMachineBlock` | GregTechAPI.java |
| `GTUtility.getMetaTileEntity(TileEntity)` | GTUtility.java |
| modid `gregtech` | Mods.java `ModIDs.GREG_TECH` |
| Maven dep pattern | NewHorizonsCoreMod dependencies.gradle |
| Client/server same pack version | wiki Server_Setup; Version_2.8.4 |

**GitHub MCP** was `needsAuth` in this environment; all GT sources were fetched via public raw.githubusercontent.com / wiki / WebSearch. Exact GT5 version **inside** the 2.8.4 zip was **not** verified from pack manifests (API rate-limited) — Mod Dev should open the 2.8.4 `mods/` GregTech jar name or pack changelog to pin Maven.

---

## Success criteria status

| Criterion | Status |
|-----------|--------|
| Starter unpacked and readable | **Done** → `/workspace/gtnh-locker-mod` |
| Report with cited findings | **Done** (this file) |
| No guessing private APIs without source link | **Done** — all GT APIs linked to GT5-Unofficial raw sources |
| Full mod not implemented | **Honored** |
