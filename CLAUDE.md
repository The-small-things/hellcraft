# Hellcraft: notes for Claude

Hellcraft is a Fabric Minecraft mod: the overworld becomes Dante's Inferno (nine circles around Lucifer's pit at
0, 0), with lifesteal hearts, circle guardians, Lucifer, Purgatory, the Nether as the Forge of Dis and the End as
Paradiso. The owner plays it on a server with friends. README.md describes every feature for players. Since 3.0 it
is a client mod too: every player installs it (the .mrpack), at exactly the server's version (`JoinCheck`).

## Working rules
- **All work goes in `versions/26.3`** (Minecraft 26.3, Java 25, Loom 1.18, Fabric API 0.161, unobfuscated Mojang
  names). The repo root is the old 1.21.1 version, frozen since 3.0: don't touch it, and CI no longer builds it.
  `versions/26.3` is self-contained (its own copies of `InfernoGeometry`, `Zone`, `Circle`, `OggLength` and the tests).
- Develop on branch `claude/minecraft-dantes-inferno-mod-ukodio`; commit and push there. No pull requests unless
  asked.
- Write player-facing text in plain language: say what happened and what to do. Characters (Lucifer, the
  guardians, the Spine's whispers) may speak in Dante's voice, but every mechanic also gets a plain tip.
- Releases: a commit whose message contains `[release]` publishes a stable GitHub release tagged
  `v<mod_version>`. Bump `mod_version` in `versions/26.3/gradle.properties` and add a "New in x.y.z" block to the
  stable-release body in `.github/workflows/build.yml`. Every other push to the branch refreshes the
  `v<version>-test` pre-release. Publish stable only when the owner asks.

## Building and testing
- CI (`.github/workflows/build.yml`) builds 26.3, then boots a real server with
  `scripts/smoke-test.sh` (Docker `itzg/minecraft-server`), drives it over RCON and fails on errors in the log.
  Most features have a self-test command or a log line that the smoke test checks; add one for new features.
- **Client game tests** (`versions/26.3/src/gametest`, run with `bash tools/dev/clienttest.sh`): a real headless
  client (Xvfb; 26.3 falls back to software Vulkan, since Xvfb's GLX has no matching visual) joins a dedicated
  server and single-player worlds and takes screenshots into `versions/26.3/build/run/clientGameTest/screenshots`.
  Look at them (Read the PNGs; crop and enlarge small details with PIL). CI runs them too (`client-test-26`,
  screenshots uploaded as an artifact) and releases only if they pass. Use them to check every model, texture,
  animation and HUD change. Locally they need `apt-get install xvfb mesa-vulkan-drivers mesa-utils`.
- CI builds put the commit in the version (`3.0.0+26.3.abc1234`), so the join check tells test builds apart.
- **Local builds need network access** to `maven.fabricmc.net`, `meta.fabricmc.net`, `piston-meta.mojang.com`,
  `piston-data.mojang.com`, `launchermeta.mojang.com`, `launcher.mojang.com`, `libraries.minecraft.net` and
  `resources.download.minecraft.net`. If they are reachable: `cd versions/26.3 && ./gradlew build`. If not, push
  and wait for CI: `bash tools/dev/waitci.sh <sha>` (or `waitstable.sh` for a `[release]` commit) prints the
  first failing job and its id; fetch that job's log with the GitHub MCP `get_job_logs` tool, and
  `python3 tools/dev/joblog.py <saved log> <out>` strips the timestamps.
- API questions about 26.3: edit `versions/26.3/tools/probe.txt` and push; the `probe-26.3` workflow prints the
  answers (javap signatures, bytecode, data files, jar listings). Line formats are documented in `tools/probe.sh`.
- Java imports: `python3 tools/dev/sortimports.py <files>`, then restore the blank line after `package` (see the
  script's docstring). Tabs for indentation.

## Generated files (edit the generator, then re-run it; never hand-edit the output)
- `tools/gen_worldgen.py --26.3`: biomes, features, dimension, timelines (also runs `gen_realms.py` for the
  Nether and Paradiso, and `gen_advancements.py`).
- `tools/gen_models.py`: item and boss models, item definitions, and the hell weapon and blood armour recipes
  (their names and lore must match the Java code, which `RecipeCheck` verifies at startup).
- `tools/gen_textures.py`: all item, boss, armour and HUD textures (pixel art as code). Item-model textures
  must sit under `textures/item/` or `textures/block/`, or they render as the missing texture; the smoke test
  checks this.

## Code map (`versions/26.3/src/main/java/net/thesmallthings/hellcraft/`; client-only code in `src/client`)
- `blood/`: hearts and lifesteal (`Hearts`, `DeathHandler`, `HellState` = all saved data), Blood Altars and their
  menus, ghosts and revival, prestige (the Seven P's), hell weapons and blood armour, relics, boss spoils
  (`BossSpoils` cooldowns, `Judgement` style-rank rewards), the bounty, `Guide` (the Journal's how-to pages).
- `hazard/`: circle hazards; `BossModel` (boss models: item displays animated through their transformation),
  `BossRules` (boss damage scales with the victim's hearts, arrows weakened), `StyleMeter` (ULTRAKILL-style
  ranks); `guardian/` (the five guardians and Vulcan, `GuardianFight` base class) and `lucifer/` (the fight).
- `world/`: worldgen glue and landmarks: Gate of Hell, Virgil's Rests (`Shrines`, sites in `ShrineSites`), the
  Spines, Purgatory, the Great Forge, Paradiso and Heaven, the Hall of the Damned, the white room, villages.
  `InfernoGeometry` is the one source of the map's shape (bands, roads, ramps, rivers, walls); `Navigation` (the
  next way down) and `MapPainter` (the Map of Hell's colours, shared with `tools/RenderMap.java`) work from it
  alone, so the client draws the compass and the map without asking the server.
- `music/BossMusic`: Lucifer's tracks, shipped in the mod (none yet: vanilla discs until the owner sends some).
- `net/`: `JoinCheck` (version handshake while joining), `HudSync` (tells the client the Blood Heart HUD state and
  prestige rank), `MapSync` (whether the world is the Inferno, the Rests found, the bosses beaten, and the Guide's
  pages for the Journal). No resource pack is sent any more: all art ships in the jar.
- `entity/`: Hellcraft's own mobs (`HellEntities`); `TestRig` is the animated test dummy.
- `blood/HellItems`: the registered items (Blood Heart, Fragment, Bane, Vigil Candle, Soul Anchor) and the creative
  tab. Weapons, armour and relics are still vanilla items marked with custom_data (to convert later).
- Client (`src/client/.../client/`): `HellcraftClient`, `BloodHud` + `mixin/HeartTypeMixin` (blood hearts and rank
  rims, textures in `assets/hellcraft/textures/gui/sprites/hud/heart`), `nav/` (finding the way: `LayerCard` circle
  cards, `CompassHud`, `MapOfHellScreen` on M, `JournalScreen` on J, `NavKeys`; `ClientNav` holds what the server
  sent; the client test `MapTest` screenshots them all as `nav_*`), `render/` (entity models and renderers:
  `TestRigModel` + `TestRigAnimation` show the pattern bosses will follow: jointed parts animated by
  `AnimationDefinition` keyframes).
- `command/HellCommands`: `/hellcraft ...` admin and test commands (many smoke-test hooks live here).
- `config/HellConfig`: every tunable; `/hellcraft config <key> [value]` reads and sets them live.

## State at handover (2.0.3, released)
Recent work: boss textures fixed, animated boss models, anti-cheese boss rules, style ranks for spoils, a
plain-language pass over all text, and End cities and guidance in Paradiso. None of it had been playtested yet:
the style numbers, the heart scaling (`bossHeartScaling`) and the animation directions (forward = +Z in the
display transformation) are educated guesses waiting on feedback.

## The 3.0 revamp
The plan is `docs/REVAMP.md`, approved on 2026-10-13. The owner marks it up in a commentable Claude Docs copy
(https://claude.ai/code/artifact/4b8ddced-e5ed-48a4-9b7f-3bafdffe313d); keep the two in step. Its section 15
holds the answers: bosses never block the way down, no PvP in the Dark Wood and Limbo, the Seraph is Paradiso's
boss, music ships in the mod, and Claude makes all the art. Layer themes must be Hellcraft's own, not ULTRAKILL's:
ULTRAKILL is only the model for turning Dante's punishments into things to play with instead of debuffs.
Decided first (2026-10-12):
- Fresh world, so the map is rebuilt from scratch.
- **A client mod is now required.** The "vanilla client, no blocks, no items" rule is gone: real blocks, items,
  mobs, models, cutscenes and HUD. Bosses use Minecraft's own keyframe animation system (no GeckoLib).
- Medium world: radius about 3,500.
- Keep lifesteal and ghosts, blood weapons and armour, style ranks and spoils, and the P's, Hall and Journey.
- 1.21.1 is dropped for now (done: out of CI, `versions/26.3` self-contained, version 3.0.0).

**Phase 0 (foundations) is done** (2026-10-13): 26.3 stands alone, the client side and join check, real blood
items, no resource-pack server, the animated test rig, client tests in CI. **Phase 1 (the new map) is built**
(2026-10-14): the geometry and terrain at radius 3,500, the four Pilgrim Roads (`PilgrimRoadFeature`), a way down
every 30 degrees on every cliff, 40 Rests beside the roads, the guardians' lairs 40 blocks south of the east road,
circle cards, the compass, the Map of Hell (M) and the Journal (J). It needs a fresh world and waits on a play
session ("you can walk from spawn to the bottom without help"). **Next: Phase 2** (boss tech, proven on Minos): a
`.bbmodel` to Java converter, real boss entities, cutscenes, the Codex. Each phase ships as a test build for a play
session before the next.

Local environment notes: the Ubuntu package `openjdk-25-jdk-headless` provides JDK 25 (the container ships
21). Maven Central sometimes answers 429; re-run (or retry with a pause). Docker works, but Modrinth is blocked, so
a local smoke test must take Fabric API from maven.fabricmc.net. `./gradlew genSources` decompiles Minecraft into
`versions/26.3/.gradle/loom-cache/minecraftMaven/.../*-sources.jar`: read the real code instead of guessing.
