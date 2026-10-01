# Contributing

RobolCraft is a Minecraft 1.7.10 mod built with the GTNH Gradle toolchain. The mod id stays `lockerworker` and the Java package stays `com.angelika.lockerworker` so existing worlds keep working.

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

Put Vorbis `.ogg` files in `src/main/resources/assets/lockerworker/sounds/<category>/`. Category names and when they play are listed in [docs/SOUNDS.md](docs/SOUNDS.md). Rebuild and restart the client. An empty folder stays silent. `healing/` is reserved and currently empty; leave a `.gitkeep` in any category that has no clips yet.

## Version

`modVersion` in `gradle.properties` is the number inside the jar and the `release/` filename. Bump it, run `syncReleaseJar`, and add a section to `CHANGELOG.md`. `release/` should contain only the new jar.

## Checks

The GTNH plugin runs Checkstyle and Spotless as part of a normal build. Fix formatting with:

```bash
./gradlew spotlessApply
```
