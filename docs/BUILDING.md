# Building

The repository root is the Gradle project. JDK 25 is required (`.java-version` and the Gradle daemon toolchain). The mod compiles to Java 8 bytecode through Jabel, which is what Minecraft 1.7.10 runs.

```bash
./gradlew build -x test
```

The player jar is `build/libs/robolcraft-<version>.jar`. Ignore the `-sources` and `-dev` jars next to it.

To put that jar where the repository keeps the latest release:

```bash
./gradlew syncReleaseJar -x test
```

`syncReleaseJar` runs the build, deletes every `.jar` in `release/`, and copies `robolcraft-<version>.jar` there. That folder should hold one file, matching `modVersion` in `gradle.properties`.

`build/`, `.gradle/`, and `run/` are gitignored. Do not commit them.

## Apple Silicon crash on the GTNH splash

On some M-series Macs, registering the worker renderer or touching GregTech classes during preInit killed the client in the early splash (`AGXG13GFamilyCommandBuffer`, no Java stack from this mod). The pack booted when the jar was removed.

The current code avoids that:

- Entity renderers are registered in init, not preInit.
- The worker model is created on the first render, not in the renderer constructor.
- GregTech is used only through reflection, on the first in-world machine scan, not via `import gregtech`.

If a new crash appears only with this jar present, check that a new class is not pulling GregTech or OpenGL into preInit.
