# Contributing

RobolCraft is a Minecraft 1.7.10 mod built with the GTNH Gradle toolchain. The mod id is `robolcraft` and the Java package is `com.angelika.robolcraft`.

## Build

Install JDK 25. From the repository root:

```bash
./gradlew build -x test
```

To refresh the jar players download:

```bash
./gradlew syncReleaseJar -x test
```

`syncReleaseJar` deletes every jar in `release/` and copies `build/libs/robolcraft-<version>.jar` there. Commit that one jar. Do not commit `build/`, `.gradle/`, or `run/`.

## Sounds

Put Vorbis `.ogg` files in `src/main/resources/assets/robolcraft/sounds/<category>/`. Category names and when they play are listed in [docs/SOUNDS.md](docs/SOUNDS.md). Rebuild and restart the client. An empty folder stays silent. `healing/` is reserved and currently empty; leave a `.gitkeep` in any category that has no clips yet.

## Version

`modVersion` in `gradle.properties` is the number inside the jar and the `release/` filename. Bump it in the same change, run `syncReleaseJar`, and add a section to `CHANGELOG.md`. `release/` should contain only the new jar.

Which number moves:

- Small patch, a bugfix that does not add behavior: `0.0.+1` (1.1.0 becomes 1.1.1).
- Small feature, or a behavior change that belongs with the last update: `0.+1.0` (1.1.0 becomes 1.2.0).
- Major update, a new release the player should treat as a new version of the mod: `+1.0.0` (1.1.0 becomes 2.0.0).

## Checks

The GTNH plugin runs Checkstyle and Spotless as part of a normal build. Fix formatting with:

```bash
./gradlew spotlessApply
```
