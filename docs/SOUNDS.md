# Sounds

Clips are Vorbis `.ogg` files inside the mod jar. Players do not drop files into a config folder, and `sounds.json` stays empty on purpose. On startup the client scans `assets/robolcraft/sounds/<category>/` and registers every clip it finds. An empty category plays nothing.

Each worker or supervisor plays at most one clip at a time.

- Free-roam, working, break, and after-work ambience finish the current clip before another clip from that family starts.
- One-shots such as smoking, clothes, and getting up wait until that ambience ends.
- Schedule cues (`breaktime_start`, `breaktime_end`, `day_end`) interrupt ambience so they are heard.
- Fighting, fleeing, changing clothes, and a right-click interaction cut in immediately.

Volume and hear distance are per player in `robolcraft-client.cfg`. Enable flags and the silence gaps are in `robolcraft.cfg`. See [CONFIG.md](CONFIG.md).

New clips require a rebuild and a client restart. F3+T only reloads clips already in the jar that is loaded.

## Categories

Folders live at `src/main/resources/assets/robolcraft/sounds/`.

| Folder | When it plays |
|--------|----------------|
| `free_roaming/` | Wandering or walking toward a machine during work |
| `working/` | Standing at a machine |
| `interaction/` | Right-click a worker |
| `breaktime/` | Break ambience, with a random gap |
| `breaktime_start/` | 75% chance when break starts |
| `breaktime_end/` | 50% chance when break ends |
| `day_start/` | 75% chance at the locker when changing into the work outfit |
| `day_end/` | 75% chance when night starts |
| `smoking/` | Each exhale during break |
| `work_exit/` | 75% chance at the locker when changing into after-work clothes |
| `locker_sound/` | At the locker on the evening and morning outfit change |
| `changing_clothes/` | Every outfit swap, including pajamas at the bed |
| `get_into_bed/` | 75% chance when getting into the linked bed |
| `get_up/` | 75% chance when waking |
| `afterwork_roaming/` | Walking to bed, or walking back to the locker in the morning |
| `waiting_for_bed/` | Waiting at the locker at night because no bed is linked |
| `fighting/` | While the attack AI is running. Starts immediately, then a gap of 20–100 ticks |
| `fleeing/` | While panic is running. Same timing as fighting |
| `healing/` | While regenerating at a medkit. This folder is empty in 1.0.0, so healing is silent until clips are added |
| `supervisor_ask_report/` | Right-click a supervisor, or the GUI Report button |
| `supervisor_report/` | Automatic walk-up report |

Death uses the vanilla villager death sound. There is no `death/` folder.

## Adding a clip

1. Export Vorbis `.ogg` (not mp3).
2. Put it in the matching category folder. The file name becomes the clip id.
3. Rebuild with `./gradlew syncReleaseJar -x test` and restart the game.

Keep a `.gitkeep` in a category that has no clips, so the empty folder stays in git.
