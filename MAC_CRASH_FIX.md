# Mac M1 (aarch64) Metal AGX crash fix — lockerworker

Date: 2026-09-22 18:39 CEST (Europe/Warsaw)

## Symptom

GTNH 2.8.4 on Apple Silicon: with `lockerworker-*.jar` present, client dies during early
BLS splash with `AGXG13GFamilyCommandBuffer` / exit 6. Pack boots cleanly **without** our jar.
No Java stacktrace naming lockerworker.

## Hypotheses addressed

### A) Client renderer too early / Angelica conflict

**Was:** `ClientProxy.preInit` called
`RenderingRegistry.registerEntityRenderingHandler(..., new RenderLockerWorker())`,
and `RenderLockerWorker` ctor did `super(new ModelVillager(0.0F), 0.5F)` — early model/GL-adjacent
work during preInit / splash.

**Now:**
1. **No** renderer registration in preInit (removed `ClientProxy.preInit` override entirely).
2. Register in `ClientProxy.init` (`FMLInitializationEvent`) only.
3. `RenderLockerWorker` passes `null` model to `super`; creates `ModelVillager` on first `doRender`
   (`mainModel` assign). No texture bind beyond what vanilla `RenderLiving` does in doRender.
4. `@SideOnly(Side.CLIENT)` on `ClientProxy` and `RenderLockerWorker`.

### B) Early GregTech class resolution on client discover

**Was:** Class-load chain
`CommonProxy.preInit` → `EntityLockerWorker` → `EntityAIWanderNearMachines` →
`GregTechMachineLookup` with compile-time `import gregtech.api.*` → JVM resolves GT API
very early when our entity is registered.

**Now:**
1. `GregTechMachineLookup` uses **reflection only** (nested `GtReflect` holder).
2. Zero `import gregtech` anywhere under `src/`.
3. GT classes resolve on first in-world machine scan (day AI), long after splash.
4. `ProcessingMachineIds` unchanged (no GT imports).
5. GT5 kept as `api` compile dependency in `dependencies.gradle` (unused by sources; fine).

### C) Left alone

- `EntityRegistry.registerGlobalEntityID` + egg colors
- `BlockLocker.registerBlockIcons` (texture stitch only)
- No static OpenGL / natives in jar

## Rebuild

```bash
cd /workspace/gtnh-locker-mod
source env.sh
./gradlew compileJava build -I /home/box/gradle-init/replace-overmind.init.gradle
```

Jar for Mac user: `build/libs/lockerworker-*.jar` (prefer the non-`-dev` / non-`-sources` artifact).

Prefer bootable client over perfect AI if further tradeoff needed — this build keeps AI via reflection.
