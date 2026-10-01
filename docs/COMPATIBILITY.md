# Compatibility

## Required

GregTech, as shipped in GregTech New Horizons. The mod declares `required-after:gregtech`. Machine detection and fault checks use reflection, so the client does not touch GregTech classes during the early loading splash.

The machine lists in [MACHINES.md](MACHINES.md) were taken from GT5-Unofficial. Recheck them when you move to a pack whose GregTech build renumbers meta tile ids.

## Optional

**MalisisDoors.** If the mod is present, workers and supervisors open its doors, trapdoors, fence gates, and garage doors when those block a path. Digicode locks and doors set to redstone-only are skipped. With the mod absent, this does nothing and vanilla wooden trapdoors still open.

**JourneyMap.** **[show me on the map]** on a supervisor report creates a red waypoint through JourneyMap's API. Tested against the JourneyMap build used by GTNH (5.2.10 fairplay). With JourneyMap absent, the link does nothing and the rest of the report still works.

**[point me to it]** does not need either mod. It only turns the camera and draws a client-side red box for 10 seconds.

## Left alone

NEI is not required. The recipes are vanilla crafting recipes and show up in NEI when NEI is installed. No other mod is referenced at load time.
