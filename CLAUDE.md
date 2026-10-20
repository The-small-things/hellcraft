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
  `v<version>-beta` pre-release (the beta; older pre-releases are removed). Publish stable only when the owner asks.

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
- Bosses with real bodies (3.0): `tools/art/<boss>.py` builds the model, its texture and its animations in code
  (with `tools/art/bbmodel.py`) and writes `versions/26.3/art/<boss>.bbmodel`, which Blockbench opens (look at it,
  play the animations). `tools/gen_bosses.py` turns every .bbmodel into `client/render/gen/<Boss>Mesh` and
  `<Boss>Animations` (Minecraft keyframes), `entity/boss/gen/<Boss>Anim` (each animation's length and named events,
  which the server times blows by) and `textures/entity/<boss>.png` (+ `_glow`). Run both after any change; the
  client test `BossTest` screenshots every animation (`minos_*`). `LuciferTest` plays Lucifer's whole fight in an Inferno world (`lucifer_*`). Rotations in tools/art follow Blockbench: +X swings a hanging
  arm forward and tips a body or head back (the model faces -Z). `HELLCRAFT_POSE_AUDIT=1 bash tools/dev/clienttest.sh`
  (with only BossTest listed) films every model's key frames from its side, front on the right (`audit_*`): check
  that blows swing forward. `python3 tools/art/render.py <model> [--anim <name> --times 0.4,0.8] [--views front,three,side,back]`
  renders a .bbmodel offline in a second (versions/26.3/build/art-preview/): use it while building a model, then confirm in game.

## Code map (`versions/26.3/src/main/java/net/thesmallthings/hellcraft/`; client-only code in `src/client`)
- `blood/`: hearts and lifesteal (`Hearts`, `DeathHandler`, `HellState` = all saved data), Blood Altars and their
  menus, ghosts and revival, prestige (the Seven P's), hell weapons and blood armour, relics, boss spoils
  (`BossSpoils` cooldowns, `Judgement` style-rank rewards), the bounty, `Guide` (the Journal's how-to pages).
- `hazard/`: circle hazards; `BossModel` (boss models: item displays animated through their transformation),
  `BossRules` (boss damage scales with the victim's hearts, arrows weakened), `StyleMeter` (ULTRAKILL-style
  ranks); `guardian/` (the five guardians and Vulcan, `GuardianFight` base class) and `lucifer/` (the fight:
  `LuciferFight` from the ready check to the climb out, `SeraphAttacks`, `EmperorAttacks`, `LuciferArena` the pit and its pillars;
  the bodies are `entity/boss/FallenSeraphEntity`, `EmperorEntity` (hurt only through `EmperorWeakPoint`s he opens)).
- The Forge of Dis (Phase 6, the Nether of an Inferno world): `world/ForgeGeometry` is its one source of shape (the Great
  Forge's mesa, the Slag Sea, four regions; `tools/RenderForge.java` previews it offline), read by `ForgeDensity` and
  `ForgeBiomeSource` (the preset's Nether: `hellcraft:forge` noise settings and biomes from `tools/gen_realms.py`) and by the
  features (`SlagCrustFeature`, `FoundryPillarFeature`, `ChainedIslandFeature`, `CyclopsCampFeature`: each builds only what
  falls in its own chunk, worked out from the geometry). `block/HellBlocks` (the mod's own blocks: `SlagCrustBlock`, its
  art from gen_textures/gen_models), `world/ForgeOfDis` (region tips, `/hellcraft forge test`), `GreatForge` (the hall and its
  stairways), client `layer/HeatColumn` and the compass's Forge mode. Vulcan (`VulcanFight`, `entity/boss/VulcanEntity`) and
  the Cyclops (`entity/boss/CyclopsEntity`, a mini-boss that fights on its own clock) are real bodies from tools/art.
  `NetherTest` films it all (`forge_*`, `vulcan_fight_*`, `cyclops_*`).
- Purgatory and Paradiso (Phase 7) are their own dimensions in the Inferno preset (`hellcraft:purgatory`, `hellcraft:paradiso`;
  data from `tools/gen_realms.py`). `world/Purgatory` builds the island in its sea once and runs the terraces, streams, burrow,
  climb out and gates; `Heaven` holds the Ascent, the Angelic Guard and the Rose's bell. `world/ParadisoGeometry` is Paradiso's
  one source of shape (nine spheres stacked `STEP` apart, centres round a circle so none sits over the one below, columns of
  light halfway between), read by `ParadisoBiomeSource` (sphere by height), `feature/ParadisoIslandFeature` (every island,
  centrepiece and the Rose, chunk by chunk), `Paradiso` (columns lift you, falls caught, spheres open one at a time, gates),
  `ParadisoTrials` (a trial per sphere, `HellState.Soul.spheres`) and the client (`layer/LightColumns`, the compass's
  Paradiso mode). The Seraph is a guardian (`Guardian.SERAPH`, `SeraphFight`, `entity/boss/SeraphEntity`, tools/art/seraph.py)
  whose lair is the Primum Mobile's island. The angels are `entity/npc/Angel` (tools/art/angel.py): the host that `Heaven.host` keeps
  circling each occupied sphere and swarming the Rose (invulnerable, passes through islands), and the Angelic Guard (`Heaven.angels`). The End is vanilla again and the Inferno has no strongholds. `ParadisoTest`
  films it (`purgatory_*`, `paradiso_*`, `seraph_fight_*`, `style_panel`).
- From the 3.0 playtest: a shield raised against a boss's or Hell's monster's blow blocks at once (`BossRules.raisingShieldAgainst`,
  `LivingEntityMixin`); Lust's lovers are stunned by a blocked dive and dive one at a time; Lucifer has his own `luciferTempo`;
  the Fallen Seraph's guard (`SeraphAttacks.guard`: 30% through until a parried slash breaks it, then 160%; trading blows
  draws a riposte); the Emperor's random blows, `BOTH_HANDS`, `DRAG`, `BREATH` (he turns in the ice, `EmperorEntity.turnBy`) and
  icefall throughout; roads with stairs and blended stone (`PilgrimRoadFeature.lookAt`); danger marks over snow; the Wood of the Suicides as a thicket
  (`world/feature/SuicideTreeFeature`, which keeps off the roads, and `block/ThornsBlock`, the `hellcraft:thorns` damage type; `LowerRules.woodTest`); the art
  painters in `tools/art/bbmodel.py` (value noise, tufted fur, metal sheen, `edge_shade` on every face).
- Phase 8 additions: `blood/Loadout` (`/hellcraft loadout [players] [kit]`: journey kits `upper`, `lower`, `lucifer`,
  `paradiso` with about what a player has by then, hearts too, for fair boss tests; `full`/`blood`, everything);
  `hazard/guardian/Arenas` (`/hellcraft arena <boss> [lair] [players]`, to the edge of any boss's arena); four lairs per
  guardian of Hell (`InfernoGeometry.LAIRS_EACH`, `Guardian.lairColumn(k)`, one fight at a time in whichever is woken,
  `GuardianManager.awakeIn`; Phlegyas has a landing by every watchtower, `Fen.landing(k)`; set-pieces keep out of
  arenas with `InfernoGeometry.nearLair`); eight Rests per circle (`ShrineSites`: four between the roads, not in
  Malebolge); the travel menu has pages; the style
  panel (`StyleMeter` sends `net/StylePayload`, client `StylePanel` draws it; games without the mod keep the boss bar);
  `hazard/guardian/LairLifts` (a beaten guardian's lair keeps a soul-fire lift to the next circle, REVAMP Q2;
  `/hellcraft lifts test`); `Codex.RUMOURS` (each guardian of Hell says something about Lucifer as it falls, and the
  `emperor` Codex page fills in, REVAMP L1); `ForgeOfDis.chainLifts` (the Foundry pillars' chain-lifts); the relics as real items (`HellItems.relic`).
- 3.1 (beta): the title screen (client `title/HellTitle` with `mixin/TitleScreenMixin` (logo, splash, version) and
  `mixin/ScreenMixin` (red glow and embers over every menu's panorama); the logo is `gen_textures.py` `title_logo`, the
  panorama of Hell is painted by `tools/art/panorama.py` (a ray-marched heightfield of the funnel, Lucifer in the pit) into
  `assets/minecraft/textures/gui/title/background`; `TitleTest` screenshots it, `HELLCRAFT_PANORAMA=1` also films in-game
  panoramas, which came out as fog); boss blows erupt as their mark lands (client `danger/Impacts`, from `DangerMarks.tick`;
  camera shake via `CameraMixin`) and the fights' particles go through `hazard/BossFx.send` (doubled, forced visible);
  `/hellcraft danger show` and `ImpactTest` (`impact_*`); Limbo without colour (client `layer/LimboGrey` picks one of five
  `hellcraft:limbo_<n>` post effects from `tools/gen_post.py`, added by `mixin/GameRendererMixin`; `LimboTest`).
- The damned (3.1): `entity/npc/Damned` (`hellcraft:damned`, body `tools/art/damned.py`: invulnerable, unpushable, not
  pickable, never saved, gone when nobody is within 72 blocks) suffers one `Damned.Punishment` per circle; the server names
  its looping animation (synched `DATA_BASE`) and plays scream, sigh or flinch over it (`DATA_ACT`); client
  `render/npc/DamnedModel` shows its props by punishment (Greed's weight, the Vestibule leader's banner, Malebolge's gilded
  hood and cloak). `hazard/Torments` brings them out near players every 2 s (`site` per punishment: off roads, lairs and
  Rests), caps cries at one per 3 s per player (`mayCry`, sound events `hellcraft:entity.damned.scream`/`sigh` in
  `assets/hellcraft/sounds.json`), makes the castle's Shades sigh, and has `/hellcraft damned spawn|test`; config
  `damnedSouls`. `DamnedTest` films every punishment (`damned_*`).
- 3.1 (beta), Minos's judgement: `blood/Sins` works out each soul's worst sin from vanilla statistics (and `HellState.Soul`'s
  `tamedKilled`, `weakKills`), each count over an ordinary pilgrim's share, Limbo below 1; kept in `Soul.judged`/`judgedFact`.
  `MinosFight.judge` wraps his tail once per circle (`MinosAnim.JUDGE` from tools/art/minos.py, event `count`) for souls
  walking in for the first time or judged differently; `Sins.tick` judges walk-ins while he sleeps; the title in the tab
  list is `mixin/ServerPlayerTabMixin` (`judgementTitles`), the Journal page comes with `MapSync.sendJournal`; `/judge`,
  `/hellcraft judge [player|test]`. The champions of the circles: `hazard/Champions` (names and monster kinds per circle,
  `Affix`, state kept in entity tags so it survives restarts, spoils to the top damager; `/hellcraft champion [circle]
  [affixes] | list | test`). `JudgementTest` films both (`minos_judgement*`, `champion_*`).
- 3.1: secret ways down (`world/SecretWays`: three per circle from Limbo to Malebolge, 27 sites worked out from geometry alone,
  each a `Kind` of landmark with its mouth and a landing cairn in the next circle at the same angle; the ground round a mouth is
  levelled in `InfernoGeometry.roughness`, and Heresy's tombs keep clear (`Tombs.allowed`); `feature/SecretWayFeature` builds
  them chunk by chunk (in every overworld biome, TOP step, after the roads); `world/SecretWayLift` carries whoever drops into a
  mouth or right-clicks it (a float, then down; no damage), remembers them in `HellState.Soul.secrets` and sends them
  (`net/SecretsPayload`, client `nav/SecretMarks`: the Map of Hell's cyan rings, the Journal's count); `/hellcraft secrets
  test|list|goto|find|forget`, `SecretWaysTest`). The Pilgrimage (`blood/Pilgrimage`: an optional one-life run from the Gate to
  Purgatory, `Soul.pilgrimTicks`/`pilgrimDeepest`, `HellState.pilgrims` records, Blood Altar travel refused, `pilgrimageHardcore`
  makes a fallen pilgrim a ghost; `/pilgrimage`, `/hellcraft pilgrimage test`; `net/PilgrimPayload`, client `PilgrimHud` at the
  top left) and `world/PilgrimStone` (the lectern that starts it, across the road from the Hall, with the fastest and the
  deepest-fallen pilgrims; `HallOfTheDamned.describe` lists them too); the Pilgrim's Shell (`HellItems.PILGRIM_SHELL`).
  `SecretsTest` films it all (`secret_*`, `pilgrimage_hud`).
- 3.1: the Journal as an open book (client `nav/JournalScreen`: contents on the left page in sections with a ribbon on the
  open page, the page on the right, Back/Next and arrow keys; every page has item pictures, `nav/JournalIcons`, by Guide title
  or Codex id: an icon in the contents and a plate of them under the title) and the Map of Hell on parchment (`MapPainter`
  grain and hillshade, a leather frame, circle names, a compass rose and scale bar) and waymarks (`world/Waymarks`: kept in `HellState.waymarks`,
  each player sent their own and the shared ones, `net/WaymarksPayload`; placed and changed with `net/WaymarkEditPayload`;
  client `nav/ClientWaymarks` holds them and which is followed, `MapOfHellScreen` places them (right-click, a panel with
  widgets drawn over the map) and draws pins, `CompassHud` points to the followed one; `/hellcraft waymarks test`,
  `WaymarkTest` films it all: `journal_*`, `waymark_*`).
- `world/`: worldgen glue and landmarks: Gate of Hell, Virgil's Rests (`Shrines`, sites in `ShrineSites`), the
  Spines, Purgatory, the Great Forge, Paradiso and Heaven, the Hall of the Damned, the white room, villages.
  `InfernoGeometry` is the one source of the map's shape (bands, roads, ramps, rivers, walls); `Navigation` (the
  next way down) and `MapPainter` (the Map of Hell's colours, shared with `tools/RenderMap.java`) work from it
  alone, so the client draws the compass and the map without asking the server.
- `music/BossMusic`: Lucifer's tracks, shipped in the mod (none yet: vanilla discs until the owner sends some).
- `net/`: `Dialogue` (what characters say and Hellcraft's notices go to a box above the hotbar, `client/dialogue/DialogueHud`,
  not the chat; chat keeps server-wide news), `JoinCheck` (version handshake while joining), `HudSync` (tells the client the Blood Heart HUD state and
  prestige rank), `MapSync` (whether the world is the Inferno, the Rests found, the bosses beaten, and the Guide's
  pages for the Journal). No resource pack is sent any more: all art ships in the jar.
- `entity/`: Hellcraft's own mobs (`HellEntities`); `TestRig` is the animated test dummy. `entity/boss/`: bosses with
  real bodies: `BossEntity` (plays the animation the server names, shows the danger warnings, plays its own death),
  `MinosEntity`; `boss/gen/` is generated (tools/gen_bosses.py).
- `hazard/Danger`: the danger language (red = get out, white = block now, a parry staggers, purple = can't be
  blocked); marks on the ground, sent to clients (`client/danger/DangerMarks` draws them), and the unblockable
  `hellcraft:judgement` damage type. `GuardianFight` gives real-bodied guardians an intro, a parry window
  (`openParry`, `onParried`) and invulnerable spells (`shield`).
- Hint bits (`CircleHazards.hintOnce`, 64 of them in `HellState.Soul.hints`): 0-31 upper Hell, 32-38 lower Hell
  (36 is Malacoda's one-time trick, spent with `spendHint`), 39 the Dash, 40 an unlit brazier, 41 the wingbeat,
  42 the Emperor's open hand, 43 his wingbeat in the fight,
  44 the Slag Sea's crust, 45-50 the Forge of Dis's regions (ForgeOfDis.TIPS), 51-60 Paradiso's spheres (their trials),
  61 the Emperor's frozen breath.
- Boss pacing: `GuardianFight` runs each fight on its own clock at `bossTempo`, stretching its `Danger` marks to match
  (`Danger.stretched`), and `BossEntity.tempo` slows the model's blows (`speedOf`: not its idle, walk, flinch, intro or
  death, nor Lucifer's SNAP, REVEAL and DORMANT). `LuciferFight` has the same clock (`update` per real tick, `step` per
  fight tick; `realTicks` converts). Time new fights with `schedule` and anim events as usual.
- Blood gear (3.0): real items in `HellItems` (and `infernal_*` forms the Hellforge makes); `BloodArmour.of` and
  `HellWeapons.of` also accept pieces made before 3.0 (vanilla items with custom data). The circles' enchantments:
  `tools/gen_enchants.py` (data), `tools/gen_loot.py` (`circle_book`, where each is found), `blood/LayerEnchants`
  (Sidestep's dash, Steadfast, Judgement; client `DashKey`).
- `cutscene/`: `Cutscene` (camera shots in the anchor's frame, captions, shakes, a name card), `Cutscenes` (by id),
  `CutsceneManager` (plays one, keeps the player from harm, remembers it was seen so it can be skipped). Client:
  `client/cutscene/CutscenePlayer` + the `CameraMixin`, `HudMixin`, `KeyboardInputMixin` and `MouseHandlerMixin`.
- `codex/Codex`: the boss entries (who, how to fight, a quote); opened on meeting a boss (`MapSync.unlockCodex`) and
  read in the Journal.
- Phase 3 (Upper Hell): `blood/Fairness` (sanctuary, newcomer grace, Cain's Mark); `hazard/LayerRules` (Lust's wind,
  Gluttony's mud, Wrath's sullen; the geometry is `world/WindStreams` and `world/Sullen`, the client side
  `client/layer/LayerFx`), `hazard/Hoarders` (Greed) and `hazard/LayerMonsters` (brings out the lovers, gluttons and
  wrathful: `entity/monster/SoulMonster` and kin, one generic client renderer `render/monster/SoulMonsterRenderer`);
  `world/NobleCastle` (+ `feature/NobleCastleFeature`, its own `noble_castle` biome) and `world/Shades` (the castle's
  NPCs, `entity/npc/Shade`); `world/Fen` (watchtowers, boardwalks, Phlegyas's landing) and `world/Ferries` (beacons call
  the ferry, `entity/Ferry`); set-piece features in `world/feature` (rotting trees, bloated fungus, feast tables, coin
  hills, vaults, scales, reeds, boardwalks, watchtowers); `client/layer/SoulFlocks` (Lust's sky). Cerberus, Plutus
  (with `entity/boss/RollingCoin`) and Phlegyas are real-bodied guardians like Minos.
- `blood/HellItems`: the registered items (Blood Heart, Fragment, Bane, Vigil Candle, Soul Anchor, the hell weapons and
  blood armour, Paradiso's relics) and the creative tab. The relics (`blood/Relics`: the Halo, Seraph Wings, Beatrice's
  Rose) are real items since Phase 8, with their own worn looks (equipment assets `halo`, `seraph_wings`); `Relics.of`
  still accepts relics made before (vanilla items with custom data).
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
- Medium world: radius about 3,500; since the 3.0 playtest every distance across the map is drawn at that size and
  multiplied by `InfernoGeometry.SCALE` (1.5: radius 5,250) with `InfernoGeometry.scaled(...)`. Lucifer's pit, arenas, cliffs,
  ramps and roads keep their size; tests and the smoke test use scaled coordinates too.
- Keep lifesteal and ghosts, blood weapons and armour, style ranks and spoils, and the P's, Hall and Journey.
- 1.21.1 is dropped for now (done: out of CI, `versions/26.3` self-contained, version 3.0.0).

**Phase 0 (foundations) is done** (2026-10-13): 26.3 stands alone, the client side and join check, real blood
items, no resource-pack server, the animated test rig, client tests in CI. **Phase 1 (the new map) is built**
(2026-10-14): the geometry and terrain at radius 3,500, the four Pilgrim Roads (`PilgrimRoadFeature`), a way down
every 30 degrees on every cliff, Rests beside the roads (a third of the way into each circle; four more between them since Phase 8), the guardians' lairs (spread round the funnel since
3.0's playtest: `InfernoGeometry.LAIRS`, four per guardian since Phase 8, each in its own circle, checked by `LairsTest`),
circle cards, the compass, the Map of Hell (M) and the Journal (J). It needs a fresh world. **Phase 2 (boss tech,
proven on Minos) is built** (2026-10-14): the model pipeline (tools/art, tools/gen_bosses.py), Minos's real body
and fight in the danger language, cutscenes (his entrance), the Codex, level lair arenas; the written guide book is
gone (the Journal holds it). Not done: multi-part hitboxes (26.3 hard-wires them to the Ender Dragon; add them with
the first boss that needs them), boss music (none sent yet). **Phase 3 (Upper Hell) is built** (2026-10-15): the fairness
rules, a rule to play with in each upper circle, the Noble Castle and its shades, set-pieces in every circle, Cerberus,
Plutus and Phlegyas on real bodies (Phlegyas is called by the east watchtower's beacons; any watchtower's beacons call
his ferry to Dis), and a new monster per circle. **Phase 4 (Lower Hell) is built** (2026-10-15): a rule in each lower
circle (`hazard/LowerRules`, `world/Tombs`, `hazard/Frauds`, `world/FraudSites`), their monsters (`hazard/LowerMonsters`;
`entity/monster` Heretic, Centaur, Harpy, Mimic, Thief, Malebranche, Malacoda), and the Furies with Medusa, Farinata,
the Minotaur and Geryon on real bodies. Later bosses use the generic `entity/boss/AnimatedBoss` and
`client/render/boss/AnimatedBossRenderer` (no per-boss entity or renderer code beyond the fight). Danger marks lie on the
ground near the mark's own height (`DangerMarks.ground`), not the heightmap (the Nether's roof, Paradiso's stacked spheres). Geryon, beaten, becomes
`GeryonCarrier` (rides down the great cliff); `world/AntaeusCleft` places `AntaeusEntity`, whose hand carries riders
along the same path its animation follows (keep the `LOWER_PATH` and `RAISE_PATH` of `tools/art/antaeus.py` and `AntaeusEntity` in step); riders of
either can't be hurt (`entity/Carrier`). **Phase 5 (Lucifer) is built** (2026-10-16): Cocytus's cold, braziers and
wingbeat (`hazard/Wingbeat`, client `layer/WingbeatFx` and the `Colossus` windmill), the ready check, the Fallen Seraph's
duel and the Emperor on real bodies, three cutscenes (descent, reveal, the climb out with a camera roll), the burrow at
his back. **Phase 6 (the Nether) is built** (2026-10-16): the Forge of Dis
(see the code map), Vulcan and the Cyclopes on real bodies. **Phase 7 (Purgatory and Paradiso) is built** (2026-10-16):
Purgatory's own sea, Paradiso's nine stacked spheres with their columns of light and trials, the Seraph on a real body,
the Rose on top, no strongholds, the story arc in the Journal. **Phase 8 (polish) is under way** (2026-10-17): the
loadout command, the style panel, the guardians' lifts and lines about Lucifer, the chain-lifts, the relics as real items, journey kits, four lairs per guardian and eight Rests per circle, the arena teleport. Still to come: balance
from the play session, boss music (none sent yet), in later 3.0 updates. **3.0.0 was released** on 2026-10-19 at the owner's request, and 3.1.0 is in beta (2026-10-20: title screen, boss impacts, the damned, grey Limbo, Minos's judgement, champions, secret ways, the Pilgrimage; ideas from comparing other Inferno mods); later stable releases (3.0.1 and on) bump `mod_version` and add a "New in" block as usual. Each phase ships as a test build for a play session before the next.

Local environment notes: the Ubuntu package `openjdk-25-jdk-headless` provides JDK 25 (the container ships
21). Maven Central sometimes answers 429; re-run (or retry with a pause). Docker works, but Modrinth is blocked, so
a local smoke test must take Fabric API from maven.fabricmc.net. `./gradlew genSources` decompiles Minecraft into
`versions/26.3/.gradle/loom-cache/minecraftMaven/.../*-sources.jar`: read the real code instead of guessing.
