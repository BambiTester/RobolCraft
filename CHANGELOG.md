# Changelog

## 1.1.0

- Per-dimension `data/robolcraft.json` for worker and supervisor overrides. An empty entry keeps today's behavior.
- Named behavior slots can be turned off or pointed at a registered behavior. Heal threshold, panic speed, and look range can be set per id.
- Schedule windows and sound categories can be overridden per id or globally.
- Addon-written settings are stamped with the addon id and version. On world load, `migrate` can update or drop them.
- Ops can edit the same file with `/robolcraft slot`.

## 1.0.0

First public release.

- Worker lockers and shift supervisor lockers, each bound to one entity and one bed
- Day schedule on the overworld clock: work, noon break, night routine
- Work near whitelisted GregTech processing machines; supervisors also watch single-block generators
- Break at a trashcan, with smoking
- Night outfit change, walk to the linked bed, sleep, morning change back into work clothes
- Stay (GUI or redstone into the locker) holds the worker after morning
- Aggressive mode with line-of-sight combat and pack aggro; creepers flee and do not detonate next to workers
- Medkit stations: under half health, workers walk over and heal half a heart every 2.5 seconds
- Workers open wooden trapdoors and, when MalisisDoors is installed, its doors, trapdoors, and gates
- Workers path around GregTech pipes and cables
- Supervisor fault reports in chat, kept on the locker, with **[point me to it]** and **[show me on the map]** (JourneyMap)
- Voice clips shipped in the jar; volume and hear distance are per client
