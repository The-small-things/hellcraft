# Hellcraft

**Lasciate ogne speranza, voi ch'intrate.**

A server-side Fabric mod for Minecraft **1.21.1** that reshapes the overworld into Dante's *Inferno*. The world is a funnel of nine circles terracing down to frozen Cocytus. The rules are Lifesteal SMP, where **blood is fuel** and **hell is full**.

Players join with a **plain vanilla client**. The mod adds no blocks and no items; everything is made from vanilla blocks, mobs, particles and sounds.

![map](docs/inferno_map.png)

## Play it in single player

Grab the newest **"Hellcraft … (test build)"** from the repo's [Releases page](https://github.com/the-small-things/hellcraft/releases).

- **Modrinth App, Prism Launcher, ATLauncher or CurseForge (easiest):** download `Hellcraft-<version>.mrpack` and import it as a modpack. That installs Minecraft 1.21.1, Fabric Loader and Fabric API for you.
- **Official Minecraft Launcher:** download `hellcraft-<version>-mods.zip` and follow the `INSTALL.txt` inside. In short:
  1. Install Fabric Loader for 1.21.1 with the [Fabric installer](https://fabricmc.net/use/installer/).
  2. Put both jars in `.minecraft/mods`.
  3. Play the `fabric-loader-1.21.1` profile.

Then go to **Singleplayer → Create New World → World tab** and click **World Type** until it says **Inferno**. On the **Game** tab, turn on **Allow Commands** if you want the test commands below.

### Testing solo

| Command | What it does |
|---|---|
| `/hellcraft goto <zone>` | Teleport to any zone (tab-completes). For example `limbo`, `greed`, `walls_of_dis`, `malebolge_pitch`, `judecca`. |
| `/hellcraft gate` | Back to the Gate of Hell |
| `/hellcraft giveheart @s 5`, `/hellcraft givefragment @s 8` | Blood to test with (right-click to use) |
| `/hellcraft sethearts @s 1`, then die | Test elimination: you become a ghost and your revenant rises |
| `/hellcraft revive <yourname>` | Come back from being a ghost |

The Gate of Hell has an altar next to it for trying the rites. To test PvP lifesteal, open the world to LAN and bring a friend.

## Hosting a server (Docker)

1. Download `hellcraft-<version>.jar` from the [Releases page](https://github.com/the-small-things/hellcraft/releases), or build it yourself with `./gradlew build` (the jar ends up in `build/libs/`).
2. Put the jar in `docker/mods/`.
3. From the `docker/` folder, run:

   ```sh
   docker compose up -d
   ```

The compose file uses [`itzg/minecraft-server`](https://github.com/itzg/docker-minecraft-server) with `TYPE=FABRIC` and `VERSION=1.21.1`. It downloads Fabric API automatically and creates the world with `LEVEL_TYPE=hellcraft:inferno`.

> The Inferno only generates in a **new world**. If you already have a world, stop the server and delete (or move) `docker/data/world` first. Lifesteal still works on an old world, but the land won't change.

Already running itzg? Add these to your existing container:
- env `TYPE=FABRIC`, `VERSION=1.21.1`, `MODRINTH_PROJECTS=fabric-api` and `LEVEL_TYPE=hellcraft:inferno`
- the jar, mounted into `/mods`

You need `LEVEL_TYPE=hellcraft:inferno` (`level-type=hellcraft:inferno` in `server.properties`). Without it the world generates as a normal overworld, and only the lifesteal rules apply.

## The shape of Hell

The world is a disc 12,000 blocks across, with the world border at radius 6000. You spawn at the rim, just outside the **Gate of Hell** (look for the inscription). Walking toward 0,0 always leads down. Each circle sits on a terrace below the last, separated by cliffs. Every cliff has a few *ruined slopes* (ramps) cut into it, so you'll need to find them, or bridge and pillar your way down.

| Ring | Circle | Land | Its torment |
|---|---|---|---|
| Dark Wood | – | Dark oak forest rising into mountains at the border. Animals, wolves. | – |
| Vestibule & Acheron | – | Grey gravel plain. The river Acheron. Bees stand in for the stinging wasps. | – |
| **Limbo** | I | Pale meadows of calcite and birch. The only villages in Hell, plus trail ruins. | none: Limbo is only longing |
| **Lust** | II | Jagged tuff and deepslate spires with amethyst and crying obsidian. Breezes. | Hurricane gusts hurl you sideways |
| **Gluttony** | III | Mud, mangrove roots, filthy pools. Hoglins and slimes. | Constant Hunger |
| **Greed** | IV | Blackstone, gilded blackstone and raw gold. Boulders, piglins, buried bastions. | Carrying gold, diamonds, emeralds or netherite slows you |
| **Wrath** | V | The marsh of Styx. Drowned, and vindicators with Strength. | Weakness and Mining Fatigue while in its water |
| Walls of Dis | – | A ring wall of blackstone brick with burning towers and 4 gates. | – |
| **Heresy** | VI | Soul sand and basalt, with fields of burning open tombs. Blazes, wither skeletons. | Pulses of Darkness |
| **Violence** | VII | Phlegethon, a river of lava with crimson banks, where skeleton "centaurs" ride skeleton horses. The Wood of the Suicides (leafless trees, webs). The Burning Sands. | Breaking the trees hurts you. Fire rains on the sands. |
| **Fraud** | VIII | Malebolge: 10 concentric ditches crossed by 12 stone bridges. The ditch floors are pitch (magma and lava deltas) or blight (sculk). Ancient cities lie below. | 25% of monsters are invisible |
| Well of Giants | – | Nimrod, Ephialtes and Antaeus stand chained. | – |
| **Treachery** | IX | Cocytus, a frozen lake of packed and blue ice. At the very centre is Lucifer's pit. | Freezing cold that gets worse toward the centre. Leather armor protects, as in vanilla. |

At the bottom, a Wither named **Lucifer** wakes when someone reaches the pit (at most once per hour).

Some other rules of Hell:
- It is always dusk, so monsters never burn.
- Beds explode and respawn anchors don't work.
- Monsters get tougher the deeper you go.
- The monster mob cap is 1.75× vanilla.
- Titles announce each circle as you enter it.

## Blood is fuel

- **Hearts.** You start with 10 hearts and can hold up to 20.
  - Killing a player steals one of their hearts.
  - Dying to anything else costs a heart, and it drops as a **Blood Heart** where you fell, so you can go back for it.
- **Blood Heart** (a glinting *fermented spider eye*). Right-click to gain a heart. `/withdraw [n]` turns your own hearts into Blood Hearts you can trade.
- **Blood Fragment** (a named *red dye*). Hostile mobs killed by players drop fragments. The chance rises from 2% in the outer circles to about 12% in Cocytus. Right-click with 8 in one stack to clot them into a Blood Heart.
- **Boss rewards.** The Wither gives 1 Blood Heart (Lucifer gives 3). The Warden gives 1 and the Ender Dragon gives 2.

### Blood altars

An altar is a **respawn anchor on a 3×3 of crying obsidian**. There is one at the Gate of Hell, and ruined altars with loot chests are scattered through Hell. Offer blood at an altar:

| Offering | Rite |
|---|---|
| Blood Heart | **Ward:** 30 minutes of immunity to every circle's torment, plus Regeneration and Absorption |
| Sneak + Blood Heart | **Bind:** you respawn at this altar (the only way to move your spawn, since beds explode) |
| Name tag with a ghost's name + 4 Blood Hearts in your inventory | **Revive** that ghost here with 3 hearts |

## Hell is full

When you lose your last heart you are not banned. *There is no more room in hell*:
- You become a **ghost**, a spectator tethered to where you died.
- Your corpse rises as a **revenant**. It's a zombie (drowned, husk or stray depending on the circle) with your name, wearing your head and the armor and weapon you died with. Kill it to get your gear back.
- The living can buy you back at a blood altar.

## Commands

| Command | Who | |
|---|---|---|
| `/hearts [player]` | all | Show hearts |
| `/withdraw [n]` | all | Bleed hearts into Blood Hearts |
| `/circle` | all | Where am I in Hell? |
| `/hellcraft sethearts <player> <n>` | op | |
| `/hellcraft giveheart\|givefragment <player> [n]` | op | |
| `/hellcraft revive <name>` | op | Revive a ghost without an altar |
| `/hellcraft ghosts` | op | List ghosts |
| `/hellcraft goto <zone>` / `gate` | op | Teleport to a zone / the Gate (testing) |
| `/hellcraft where` | op | Debug: geometry at your position |
| `/hellcraft reload` | op | Reload `config/hellcraft.json` |

## Config

`config/hellcraft.json` is created on first start. You can change:
- start, max and revive hearts, and the revive cost
- fragment drop rates and how many fragments make a heart
- whether natural deaths drop a heart
- the mob cap multiplier and mob health per depth
- the ghost tether radius
- ward length
- toggles for hazards, titles and revenants
- Lucifer's health and cooldown

## Development

- `./gradlew build` builds the mod and runs the geometry unit tests.
- `python3 tools/gen_worldgen.py` regenerates every biome, surface rule, feature and tag. Edit the script, not the JSON.
- `tools/RenderMap.java` renders the map and cross-section in `docs/` without Minecraft (see its header for the command).
- `scripts/smoke-test.sh` boots a real server in Docker, generates every circle and checks the log. CI runs it on every push.
- `scripts/package-singleplayer.sh` builds the `.mrpack` and the mods zip into `dist/`. CI runs it and publishes the results as the test pre-release.
- All of the funnel's geometry lives in `InfernoGeometry.java`, which is pure Java. Terrain, biomes, features and hazards all read from it.
