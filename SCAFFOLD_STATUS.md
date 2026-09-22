# Locker Worker — Scaffold Status

Date: 2026-09-22 (Europe/Warsaw, UTC+2)
Updated: 2026-09-22 18:39 CEST (Mac M1 Metal AGX crash mitigations — see MAC_CRASH_FIX.md)

## Mac M1 Metal AGX crash mitigations (2026-09-22 18:39 CEST)

A/B: pack boots without our jar; with jar → AGXG13GFamilyCommandBuffer / exit 6 during BLS splash.

Mitigations (details in `MAC_CRASH_FIX.md`):
- **Renderer:** registration moved from `ClientProxy.preInit` → `ClientProxy.init`; `RenderLockerWorker` lazy `ModelVillager`; `@SideOnly(CLIENT)`.
- **GT classload:** `GregTechMachineLookup` reflection-only — **zero** `import gregtech` in sources; GT resolves on first in-world machine scan.

## Naming (done)

| Field | Value |
|-------|-------|
| modName | Locker Worker |
| modId | lockerworker |
| modGroup / package | com.angelika.lockerworker |
| Main class | LockerWorkerMod |
| Tags | com.angelika.lockerworker.Tags (Gradle-generated) |
| Mixins | usesMixins = false |
| @Mod deps | required-after:gregtech |

Old package `com.myname.mymodid` removed (only leftover mentions are example comments inside `gradle.properties`).

## Files created / updated

### Build / metadata
- `gradle.properties` — renamed modName / modId / modGroup / generateGradleTokenClass
- `dependencies.gradle` — GT5-Unofficial **`5.09.51.471:dev`** (pinned to NewHorizonsCoreMod **2.7.268** / GTNH 2.8.4 NHCore cite) + exclude `thaumcraft` / `ThaumicTinkerer`
- `env.sh` — `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64` (Angelika)
- `src/main/resources/mcmod.info` — Angelika / Locker Worker description
- `SCAFFOLD_STATUS.md` — this file

### Java (`com.angelika.lockerworker`)
- `LockerWorkerMod.java` — @Mod, proxies, instance
- `CommonProxy.java` — block/TE/entity registration (preInit), 2×2 recipe (init)
- `ClientProxy.java` — entity renderer registration in **init** (not preInit); `@SideOnly(CLIENT)`
- `Config.java` — greeting + machine scan radius/interval
- `block/BlockLocker.java` — 2-tall door-style meta (UPPER=0x8), break both halves, kill worker
- `tileentity/TileEntityLocker.java` — worker UUID / entity id, spawn on place, respawn on death, kill on destroy
- `entity/EntityLockerWorker.java` — EntityCreature, villager health, panic flee, silent, home locker NBT
- `entity/ai/EntityAIWanderNearMachines.java` — day wander / look-at-machine stubs
- `entity/ai/EntityAIReturnToLocker.java` — night return-to-locker stub
- `util/GregTechMachineLookup.java` — **reflection-only** GT lookup + **ProcessingMachineIds whitelist** (rejects pipes/cables; no `import gregtech`)
- `util/ProcessingMachineIds.java` — explicit GT5U meta-tile ID whitelist (506 IDs)
- `WHITELIST.md` — full name→id table + source cites
- `client/render/RenderLockerWorker.java` — RenderLiving + lazy ModelVillager + placeholder skin
- Spotless applied to scaffold sources (format-only)

### Assets (placeholders)
- `assets/lockerworker/textures/blocks/locker.png` — 16×16 grey PNG
- `assets/lockerworker/textures/entity/locker_worker.png` — 16×16 grey PNG (not a real villager skin layout)
- **TODO Texture Artist:** replace both; entity skin should match biped/villager UV layout

## Recipe (registered in init)

```
Iron bars | Rotten flesh
Iron bars | Dirt
→ 1× Locker block
```

## Compile / Java status (UPDATED)

| Check | Result |
|-------|--------|
| JDK | **OK** — Debian OpenJDK **25.0.4.1** at `/usr/lib/jvm/java-25-openjdk-amd64` (Angelika); `source env.sh` |
| `java -version` | openjdk 25.0.4.1 LTS |
| `JAVA_HOME` | `/usr/lib/jvm/java-25-openjdk-amd64` |
| `.java-version` / toolchain | **25** (bytecode still 8 via Jabel) |
| `./gradlew setupDecompWorkspace` | **SUCCESS** (2026-09-22 18:15 CEST) |
| `./gradlew compileJava` | **SUCCESS** (Jabel initialized; no scaffold compile errors) |
| `./gradlew build` | **SUCCESS** after `spotlessApply` (jars in `build/libs/`) |

### JDK install notes (for the box)
Angelika installed/system JDK; use project env — **do not reinstall**:

```bash
source /workspace/gtnh-locker-mod/env.sh
# JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
```

(Also present from earlier attempt: Temurin tarball under `/home/box/jdk/current` — unused; prefer `env.sh`.)

### Overmind HTTP 429 workaround (required for deps while rate-limited)
`gregtech.overminddl1.com` returned **HTTP 429** for Forge-style `org.scala-lang:scala-parser-combinators_2.11:1.0.1` (and would block Thaumcraft). Mitigation used for successful builds:

1. Local sparse Maven mirror: `/home/box/forge-maven-mirror/`  
   - Seeded `org.scala-lang:scala-parser-combinators_2.11:1.0.1` and `scala-xml_2.11` (jars from Maven Central `org.scala-lang.modules`, POM groupId rewritten to Forge coords).
2. Gradle init script: `/home/box/gradle-init/replace-overmind.init.gradle`  
   - Rewrites Overmind repo URL → `file:///home/box/forge-maven-mirror/` so 429 becomes fall-through 404 for missing artifacts.
3. `dependencies.gradle` excludes `thaumcraft` / `ThaumicTinkerer` from GT5 (not needed by this mod; Thaumcraft only lived on Overmind).

**Re-run recipe while Overmind is 429:**

```bash
source /workspace/gtnh-locker-mod/env.sh
cd /workspace/gtnh-locker-mod
./gradlew setupDecompWorkspace --no-configuration-cache -I /home/box/gradle-init/replace-overmind.init.gradle
./gradlew compileJava --no-configuration-cache -I /home/box/gradle-init/replace-overmind.init.gradle
./gradlew build --no-configuration-cache -I /home/box/gradle-init/replace-overmind.init.gradle
```

When Overmind recovers, the `-I` init script can be omitted (or keep it for scala coords).

### Artifacts produced
- `build/libs/lockerworker-NO-GIT-TAG-SET.jar`
- `build/libs/lockerworker-NO-GIT-TAG-SET-dev.jar`
- `build/libs/lockerworker-NO-GIT-TAG-SET-sources.jar`

Version string is `NO-GIT-TAG-SET` (no git tags / `gtnh.modules.gitVersion` still on). Optional: init git + tag, or set `modVersion` / disable gitVersion.


## Day / night AI (vanilla sun)

**Check:** `World.isDaytime()` via `util/VanillaDayNight.java`  
→ `WorldProvider.isDaytime()` → `skylightSubtracted < 4` (same as undead burn).  
See `DAY_NIGHT.md`. No custom day-length config.

- Day: `EntityAIWanderNearMachines`
- Night: `EntityAIReturnToLocker` (stand in front until sunrise)

## Known scaffold TODOs / risks

1. **GT5 pin** — DONE for CoreMod 2.7.268 cite (`5.09.51.471`). Optional: confirm filename of GregTech jar inside the 2.8.4 Prism ZIP matches.
2. **Pipe / processing whitelist** — DONE: `ProcessingMachineIds` + whitelist check. TODO: verify IDs vs GTNH 2.8.4 jar.
3. **Textures / sounds** — placeholders only; silent entity by design for now.
4. **AI polish** — hop/stand/look timings are stubs; facing-aware “stand in front of locker” is approximate. Day/night gate uses vanilla `World.isDaytime()` (done).
5. **Tags.java** — generated at Gradle configure/compile time; IDE may show missing until first Gradle sync.
6. **Git versioning** — jars named `NO-GIT-TAG-SET`; not a compile blocker.
7. **Overmind** — still rate-limited at last check; keep init-script workaround until clear.
8. No mixins; no GT sources copied into the mod.

## Success criteria checklist

| Criterion | Status |
|-----------|--------|
| Package renamed; no live `com.myname.mymodid` sources | Done |
| Locker 2-tall block + TE scaffolded | Done |
| Entity + AI stubs + GT lookup util | Done |
| Recipe registered | Done |
| dependencies.gradle has GT5-Unofficial | Done — **5.09.51.471** (CoreMod 2.7.268) |
| Status report written | Done |
| Java available | Done |
| setupDecompWorkspace | Done (SUCCESS) |
| compileJava / build | Done (SUCCESS) |
| Processing-machine whitelist + docs | Done (`ProcessingMachineIds`, `WHITELIST.md`) |
| Vanilla day/night AI (`World.isDaytime`) | Done (`VanillaDayNight`, `DAY_NIGHT.md`) |
