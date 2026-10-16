#!/usr/bin/env bash
# Starts itzg/minecraft-server with Hellcraft, generates chunks across every circle and checks the log.
# Usage: scripts/smoke-test.sh <dir-containing-hellcraft-jar>
# MC_VERSION / MC_IMAGE pick the Minecraft version and Java image (default 26.3 on java25; 1.21.1 is frozen).
set -euo pipefail
MODS_DIR=$(cd "${1:-build/libs}" && pwd)
rm -f "$MODS_DIR"/*-sources.jar
MC_VERSION=${MC_VERSION:-26.3}
MC_IMAGE=${MC_IMAGE:-itzg/minecraft-server:java25}
NAME=hellcraft-smoke
LOG=smoke-server.log
docker rm -f "$NAME" >/dev/null 2>&1 || true

CONFIG_DIR=$(mktemp -d)
mkdir -p "$CONFIG_DIR/hellcraft/music"
PORTS=()
if [ "$MC_VERSION" = "1.21.1" ]; then
  # the frozen 1.21.1 build still serves a resource pack with boss music: a short generated track
  echo '{"musicPackHost": "localhost"}' > "$CONFIG_DIR/hellcraft.json"
  if command -v ffmpeg >/dev/null; then
    ffmpeg -loglevel error -f lavfi -i "sine=frequency=220:duration=3" -c:a libvorbis "$CONFIG_DIR/hellcraft/music/duel.ogg"
  fi
  PORTS=(-p 25566:25566)
else
  # a config written by 1.0.0 (old balance defaults, no configVersion, an old pack setting): it must be moved
  # to the new defaults
  echo '{"musicPackHost": "localhost", "monsterCapMultiplier": 1.75, "mobHealthPerDepth": 0.06, "fragmentChanceBase": 0.02}' > "$CONFIG_DIR/hellcraft.json"
fi
chmod -R a+rwX "$CONFIG_DIR"

if [ "$MC_VERSION" != "1.21.1" ]; then
  # the art ships in the mod (the client loads it from the jar)
  JAR=$(ls "$MODS_DIR"/hellcraft-*.jar | head -1)
  for f in assets/hellcraft/items/blood_heart.json assets/hellcraft/models/item/lucifer_morning_star.json \
           assets/hellcraft/models/item/lucifer_emperor.json assets/hellcraft/textures/item/boss/lucifer_emperor.png \
           assets/hellcraft/textures/item/tithe_axe.png assets/hellcraft/items/vigil_candle.json assets/hellcraft/items/soul_anchor.json \
           assets/hellcraft/equipment/blood.json assets/hellcraft/textures/entity/equipment/humanoid/blood.png \
           assets/hellcraft/textures/entity/equipment/humanoid_leggings/blood.png \
           assets/hellcraft/textures/gui/sprites/hud/heart/full.png assets/hellcraft/textures/gui/sprites/hud/heart/rank_3/container.png \
           hellcraft.client.mixins.json; do
    unzip -l "$JAR" | grep -q "$f" || { echo "The mod jar is missing $f"; exit 1; }
  done
  # item models (the boss models included) can only use textures stitched into the item or block atlas,
  # anything else draws as the black and magenta missing texture
  python3 - "$JAR" <<'PY' || exit 1
import json, sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
names = set(z.namelist())
bad = []
for n in sorted(names):
    if n.startswith("assets/hellcraft/models/") and n.endswith(".json"):
        for key, tex in json.loads(z.read(n)).get("textures", {}).items():
            if tex.startswith("#"):
                continue
            ns, path = tex.split(":", 1) if ":" in tex else ("minecraft", tex)
            if not path.startswith(("item/", "block/")):
                bad.append(f"{n}: {key} = {tex} is outside the item and block atlases")
            elif ns == "hellcraft" and f"assets/hellcraft/textures/{path}.png" not in names:
                bad.append(f"{n}: {key} = {tex} is not in the jar")
print("\n".join(bad) if bad else "Every model texture is in the jar and an atlas")
sys.exit(1 if bad else 0)
PY
fi

docker run -d --name "$NAME" \
  -e EULA=TRUE -e TYPE=FABRIC -e VERSION="$MC_VERSION" \
  -e MODRINTH_PROJECTS=fabric-api \
  -e LEVEL_TYPE=hellcraft:inferno \
  -e ONLINE_MODE=FALSE -e MEMORY=3G -e ENABLE_RCON=true -e RCON_PASSWORD=smoketest \
  -e VIEW_DISTANCE=4 -e SIMULATION_DISTANCE=4 \
  -e PAUSE_WHEN_EMPTY_SECONDS=0 \
  -v "$MODS_DIR":/mods:ro \
  -v "$CONFIG_DIR":/config:ro \
  "${PORTS[@]}" \
  "$MC_IMAGE"

cleanup() {
  status=$?
  docker logs "$NAME" > "$LOG" 2>&1 || true
  if [ "$status" != 0 ]; then
    echo "---- server log (warnings, errors, Hellcraft) ----"
    grep -iE 'WARN|ERROR|Exception|hellcraft|lucifer|summon|display|paradiso|forge|vulcan|seraph|hall|prestige|ascend|advancement' "$LOG" | grep -v 'Marked chunk' | tail -80 || true
  fi
  docker rm -f "$NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Waiting for the server to start..."
# grep a snapshot of the log: piping `docker logs` into `grep -q` trips pipefail (SIGPIPE) when grep exits early
started=0
for i in $(seq 1 180); do
  docker logs "$NAME" > "$LOG" 2>&1 || true
  if grep -q 'Done (' "$LOG"; then started=1; break; fi
  if [ "$(docker inspect -f '{{.State.Running}}' "$NAME")" != "true" ]; then
    tail -150 "$LOG"; echo "Server exited during startup"; exit 1
  fi
  sleep 5
done
[ "$started" = 1 ] || { tail -150 "$LOG"; echo "Server did not start in time"; exit 1; }

# rcon that fails fast (with a thread dump and the log tail) if the server's main thread stops answering
rcon() {
  local out
  if ! out=$(timeout 60 docker exec "$NAME" rcon-cli "$@" 2>&1) || echo "$out" | grep -q "i/o timeout"; then
    if echo "$out" | grep -q "i/o timeout" || [ -z "$out" ]; then
      echo "RCON did not answer '$*' - the server thread is stuck. Thread dump:"
      docker exec "$NAME" sh -c 'kill -3 $(pgrep -f "java" | head -1)' || true
      sleep 3
      docker logs "$NAME" 2>&1 | grep -A40 '"Server thread"' | head -80 || true
      docker logs "$NAME" 2>&1 | grep -v "^\s" | tail -60
      exit 1
    fi
  fi
  echo "$out"
}

rcon "hellcraft where"
rcon "hellcraft givebane nobody" || true  # needs a player; checks the command is registered
if [ "$MC_VERSION" != "1.21.1" ]; then
  help=$(rcon "revive")
  echo "$help"
  echo "$help" | grep -q "How to revive a ghost" || { echo "/revive does not explain reviving"; exit 1; }
  echo "$help" | grep -q "Gate of Hell at" || { echo "/revive does not say where the starter altar is"; exit 1; }
fi

if [ "$MC_VERSION" = "1.21.1" ] && [ -f "$CONFIG_DIR/hellcraft/music/duel.ogg" ]; then
  curl -fsS -o music-pack.zip http://localhost:25566/hellcraft-music.zip || { echo "Music pack not served"; exit 1; }
  unzip -l music-pack.zip
  unzip -l music-pack.zip | grep -q 'assets/hellcraft/sounds.json' || { echo "Pack has no sounds.json"; exit 1; }
  unzip -l music-pack.zip | grep -q 'assets/hellcraft/sounds/music/duel.ogg' || { echo "Pack has no track"; exit 1; }
  curl -s -o /dev/null -w '%{http_code}' http://localhost:25566/anything-else | grep -q 404 || { echo "Web server serves more than the pack"; exit 1; }
fi
rcon "hellcraft goto judecca" || true  # needs a player; checks the command is registered
if [ "$MC_VERSION" != "1.21.1" ]; then
  weapon=$(rcon "hellcraft giveweapon nobody bloodletter" || true)
  echo "$weapon"
  echo "$weapon" | grep -qi "unknown or incomplete command" && { echo "/hellcraft giveweapon is not registered"; exit 1; }
  armour=$(rcon "hellcraft givearmour nobody" || true)
  echo "$armour"
  echo "$armour" | grep -qi "unknown or incomplete command" && { echo "/hellcraft givearmour is not registered"; exit 1; }
  item=$(rcon "hellcraft giveitem nobody vigil" || true)
  echo "$item"
  echo "$item" | grep -qi "unknown or incomplete command" && { echo "/hellcraft giveitem is not registered"; exit 1; }
  cfg=$(docker exec "$NAME" cat /data/config/hellcraft.json)
  for want in '"configVersion": 3' '"bossHeartScaling": 0.5' '"monsterCapMultiplier": 1.0' '"mobHealthPerDepth": 0.03' '"fragmentChanceBase": 0.05' '"pveDeathsCostHearts": false' '"bindCostHearts": 0'; do
    echo "$cfg" | grep -qF "$want" || { echo "$cfg"; echo "Config was not migrated: missing $want"; exit 1; }
  done
  ghost=$(rcon "ghost" || true)
  echo "$ghost"
  echo "$ghost" | grep -q "Ghost powers" || { echo "/ghost does not explain the ghost powers"; exit 1; }
fi
rcon "locate biome hellcraft:judecca"
rcon "locate biome hellcraft:limbo"
rcon "locate structure minecraft:stronghold" || true

# Force-generate one chunk in every ring along the east road, from the rim down to Lucifer's pit (the Walls of Dis'
# east gate at 1395), and one off the road (a way down at 30 degrees, in Lust)
for x in 3450 3300 3080 2980 2770 2425 2100 1800 1530 1395 1240 1065 970 855 640 450 265 55 0; do
  rcon "forceload add $x 0"
done
rcon "forceload add 2165 1250"
echo "Generating..."
sleep 60
rcon "forceload query" || true
if [ "$MC_VERSION" != "1.21.1" ]; then
  # the Emperor's Spines: bone walkways over Cocytus at all four points of the compass, bent off the straight
  for p in "400 0" "0 400" "-400 0" "0 -400" "60 0"; do
    read -r px pz <<< "$p"
    rcon "forceload add $(( px - 16 )) $(( pz - 16 )) $(( px + 16 )) $(( pz + 16 ))" > /dev/null
  done
  spines=$(rcon "hellcraft spine test")
  echo "$spines"
  echo "$spines" | grep -q "Spine test OK" || { echo "The Emperor's Spines are not where they should be"; exit 1; }
  rcon "summon minecraft:zombie 402 -12 0"
  sleep 2
  zombie=$(rcon "execute if entity @e[type=minecraft:zombie,x=402,y=-12,z=0,distance=..8]" || true)
  echo "Zombie on the spine: $zombie"
  echo "$zombie" | grep -q "Test failed" || { echo "A monster survived on the Emperor's Spine"; exit 1; }
  rcon "summon minecraft:zombie 0 -12 -402"
  sleep 2
  zombie=$(rcon "execute if entity @e[type=minecraft:zombie,x=0,y=-12,z=-402,distance=..8]" || true)
  echo "Zombie on the north spine: $zombie"
  echo "$zombie" | grep -q "Test failed" || { echo "A monster survived on the north spine"; exit 1; }
  objectives=$(rcon "scoreboard objectives list")
  echo "$objectives"
  echo "$objectives" | grep -q "\[Hearts\]" || { echo "No hearts in the player list"; exit 1; }
  guide=$(rcon "guide" || true)
  echo "$guide"
  echo "$guide" | grep -qi "unknown or incomplete command" && { echo "/guide is not registered"; exit 1; }

  # Virgil's Rests: build Limbo's four (one beside each road), then check the altar, the safe ring and every new loot table
  rests=$(rcon "hellcraft shrine build limbo")
  echo "$rests"
  echo "$rests" | grep -q "Built 4 Virgil's Rest" || { echo "Limbo's Virgil's Rests were not built"; exit 1; }
  read -r _ _ rx ry rz <<< "$(echo "$rests" | grep -oE 'limbo_east at -?[0-9]+ -?[0-9]+ -?[0-9]+')"
  rcon "forceload add $rx $rz" > /dev/null
  altar=$(rcon "execute if block $rx $ry $rz minecraft:respawn_anchor" || true)
  echo "Rest altar at $rx $ry $rz: $altar"
  echo "$altar" | grep -q "Test passed" || { echo "No Blood Altar at Virgil's Rest limbo_east"; exit 1; }
  rcon "summon minecraft:zombie $rx $((ry + 1)) $((rz + 3))"
  sleep 2
  zombie=$(rcon "execute if entity @e[type=minecraft:zombie,x=$rx,y=$ry,z=$rz,distance=..10]" || true)
  echo "Zombie at the Rest: $zombie"
  echo "$zombie" | grep -q "Test failed" || { echo "A monster survived inside Virgil's Rest"; exit 1; }
  for t in chests/virgils_rest_upper chests/virgils_rest_middle chests/virgils_rest_lower chests/heretic_tomb chests/altar_ruin \
           chests/feast_table chests/vault chests/fen_cache gameplay/guardian_spoils; do
    drop=$(rcon "loot spawn $rx $((ry + 2)) $rz loot hellcraft:$t" || true)
    echo "$t: $drop"
    echo "$drop" | grep -q "Dropped" || { echo "Loot table hellcraft:$t does not work"; exit 1; }
  done
  rcon "kill @e[type=minecraft:item]" > /dev/null || true
  cane=$(rcon "place feature hellcraft:sugar_cane $rx $((ry + 1)) $rz" || true)
  echo "Sugar cane: $cane"
  echo "$cane" | grep -qiE "unknown|can't find|invalid" && { echo "The sugar cane feature is missing"; exit 1; }
  # the Seven P's: a throwaway soul climbs all seven terraces, and the eighth is refused
  prest=$(rcon "hellcraft prestige test" || true)
  echo "$prest"
  echo "$prest" | grep -q "7 P's burned" || { echo "The Seven P's do not climb"; exit 1; }
  echo "$prest" | grep -q "the eighth refused" || { echo "An eighth P was burned"; exit 1; }
  # the Hall of the Damned beside the spawn: five pillars, each signed with its top three
  hall=$(rcon "hellcraft hall build" || true)
  echo "$hall"
  hpos=$(echo "$hall" | grep -oE -- '-?[0-9]+, -?[0-9]+, -?[0-9]+' | head -1 | tr -d ',')
  [ -n "$hpos" ] || { echo "The Hall of the Damned was not built"; exit 1; }
  read -r hx hy hz <<< "$hpos"
  rcon "forceload add $hx $hz" > /dev/null
  sign=$(rcon "execute if block $hx $hy $hz minecraft:dark_oak_sign" || true)
  echo "Hall sign: $sign"
  echo "$sign" | grep -q "Test passed" || { echo "The Hall of the Damned has no signs"; exit 1; }
  board=$(rcon "hellcraft hall" || true)
  echo "$board"
  for heading in "MOST HEARTS" "P'S BURNED" "LUCIFER SLAIN" "GUARDIANS SLAIN" "MOST DAMNED"; do
    echo "$board" | grep -q "$heading" || { echo "The Hall has no $heading board"; exit 1; }
  done
  # keeping the mighty in check: boss spoils once per cooldown, live settings, the bounty
  spoils=$(rcon "hellcraft spoils test" || true)
  echo "$spoils"
  echo "$spoils" | grep -q "Spoils test: FIRST TOO_SOON AGAIN" || { echo "Boss spoils can be farmed"; exit 1; }
  echo "$spoils" | grep -q "Style test: D B S SSS" || { echo "Style ranks are wrong"; exit 1; }
  echo "$spoils" | grep -q "Boss animation test: feet=true lunge=true topple=true" || { echo "Boss models don't animate right"; exit 1; }
  ceiling=$(rcon "hellcraft config heartCeiling" || true)
  echo "$ceiling"
  echo "$ceiling" | grep -q "heartCeiling = 40" || { echo "The heart ceiling is not 40"; exit 1; }
  set=$(rcon "hellcraft config bountyMinHearts 30" || true)
  echo "$set"
  echo "$set" | grep -q "bountyMinHearts = 30" || { echo "Settings can't be changed live"; exit 1; }
  rcon "hellcraft config bountyMinHearts 25" > /dev/null
  bad=$(rcon "hellcraft config noSuchThing" || true)
  echo "$bad" | grep -q "No such setting" || { echo "Unknown settings are not refused"; exit 1; }
  bounty=$(rcon "hellcraft bounty now" || true)
  echo "$bounty"
  echo "$bounty" | grep -q "Nobody online has enough hearts" || { echo "The bounty marked somebody on an empty server"; exit 1; }
  for cmd in "hellcraft inspect nobody" "hellcraft setbonus nobody 0" "hellcraft reset nobody" "hellcraft strip nobody"; do
    out=$(rcon "$cmd" || true)
    echo "$cmd: $out"
    echo "$out" | grep -qi "unknown or incomplete command" && { echo "/$cmd is not registered"; exit 1; }
  done
  # the white room: a spare cell papered with white maps, then taken down again
  white=$(rcon "hellcraft whiteroom test" || true)
  echo "$white"
  echo "$white" | grep -q "1806/1806 frames, lit=true, white map=true, frames left after release=0" || { echo "The white room is not right"; exit 1; }
  for cmd in "hellcraft travel nobody gate" "hellcraft shrine visit nobody all" "hellcraft prestige nobody" "hellcraft whiteroom nobody" "hellcraft whiteroom release nobody"; do
    out=$(rcon "$cmd" || true)
    echo "$cmd: $out"
    echo "$out" | grep -qi "unknown or incomplete command" && { echo "/$cmd is not registered"; exit 1; }
  done
  # the Forge of Dis: generate some of the reshaped Nether, then check its features and loot
  for p in "0 0" "300 0" "0 300" "-300 -300"; do
    read -r nx nz <<< "$p"
    rcon "execute in minecraft:the_nether run forceload add $nx $nz" > /dev/null
  done
  sleep 15
  for f in forge_ruin slag_heap chain_pillar; do
    out=$(rcon "execute in minecraft:the_nether run place feature hellcraft:$f 0 80 0" || true)
    echo "Nether $f: $out"
    echo "$out" | grep -qiE "unknown|can't find|invalid" && { echo "Nether feature hellcraft:$f is missing"; exit 1; }
  done
  # the Forge of Dis's own map (Phase 6): each region where ForgeGeometry puts it, crust on the Slag Sea's causeways
  forge=$(rcon "hellcraft forge test")
  echo "$forge"
  echo "$forge" | grep -q "Forge test OK" || { echo "The Forge of Dis is not laid out as it should be"; exit 1; }
  drop=$(rcon "loot spawn $rx $((ry + 2)) $rz loot hellcraft:chests/forge_ruin" || true)
  echo "$drop" | grep -q "Dropped" || { echo "$drop"; echo "The forge ruin loot table does not work"; exit 1; }
  rcon "kill @e[type=minecraft:item]" > /dev/null || true
  # Heaven: the Rose, the Ascent, the Seraph's angels, the relics and the wings in end city treasure
  rose=$(rcon "hellcraft paradiso rose")
  echo "$rose"
  rcon "execute in minecraft:the_end run forceload add 7000 0" > /dev/null
  bell=$(rcon "execute in minecraft:the_end if block 7000 92 0 minecraft:bell" || true)
  echo "The Rose's bell: $bell"
  echo "$bell" | grep -q "Test passed" || { echo "The Celestial Rose has no bell"; exit 1; }
  angels=$(rcon "execute in minecraft:the_end positioned 7000 95 0 run hellcraft paradiso angels" || true)
  echo "$angels"
  echo "$angels" | grep -qE "[1-9][0-9]* angels descend" || { echo "No angels came"; exit 1; }
  rcon "kill @e[tag=hellcraft_angel]" > /dev/null || true
  gate=$(rcon "execute if block 0 291 8 minecraft:end_gateway" || true)
  echo "The Ascent: $gate"
  echo "$gate" | grep -q "Test passed" || { echo "The Ascent is not on Purgatory's summit"; exit 1; }
  for r in halo seraph_wings beatrices_rose; do
    out=$(rcon "hellcraft giveitem nobody $r" || true)
    echo "$out" | grep -qi "unknown item" && { echo "Relic $r is unknown"; exit 1; }
  done
  drop=$(rcon "execute in minecraft:the_end run loot spawn 7000 95 0 loot minecraft:chests/end_city_treasure" || true)
  echo "$drop" | grep -q "Dropped" || { echo "$drop"; echo "End city treasure does not work"; exit 1; }
  # the End's gateways drop players about 1000 blocks out, in the first ring: End cities must be there too
  city=$(rcon "execute in minecraft:the_end positioned 1100 64 0 run locate structure minecraft:end_city" || true)
  echo "$city"
  cxz=$(echo "$city" | grep -oE '\[-?[0-9]+, ~, -?[0-9]+\]' | tr -d '[]~ ' | tr ',' ' ' | head -1)
  [ -n "$cxz" ] || { echo "No End city could be located near the gateway exits"; exit 1; }
  read -r cx cz <<< "$cxz"
  # within a walk of where the gateways drop players (where exactly depends on the seed, which changes every run: a
  # ring is over a thousand blocks wide, so allow most of one)
  [ $(( (cx - 1100) * (cx - 1100) + cz * cz )) -lt $(( 1200 * 1200 )) ] || { echo "The nearest End city ($cx, $cz) is too far from the gateway exits"; exit 1; }
  rcon "kill @e[type=minecraft:item]" > /dev/null || true
  for p in "0 0" "1500 0" "3000 0" "6600 0"; do
    read -r ex ez <<< "$p"
    rcon "execute in minecraft:the_end run forceload add $ex $ez" > /dev/null
  done
  sleep 10
  # Paradiso: the End's outer islands belong to the nine spheres
  rcon "hellcraft paradiso status" || true
  echo "Generated biome at 1500 0: $(rcon "execute in minecraft:the_end if biome 1500 64 0 hellcraft:paradiso_moon" || true)"
  for sphere in moon venus sun primum_mobile empyrean; do
    out=$(rcon "execute in minecraft:the_end positioned 0 64 0 run locate biome hellcraft:paradiso_$sphere" || true)
    echo "Paradiso $sphere: $out"
    echo "$out" | grep -qi "nearest" || { echo "The sphere hellcraft:paradiso_$sphere is nowhere in the End"; exit 1; }
  done
  list=$(rcon "hellcraft shrine list")
  echo "$list" | grep -q "heresy_east" || { echo "/hellcraft shrine list does not list the Rests"; exit 1; }
fi

if [ "$MC_VERSION" != "1.21.1" ]; then
  # fair play: no PvP in the Dark Wood, the Shore and Limbo; killing a soul with under half your hearts takes nothing
  fair=$(rcon "hellcraft fairness test")
  echo "$fair"
  echo "$fair" | grep -q "dark_wood=true limbo=true lust=false; 10 vs 25 bullying=true, 15 vs 25 bullying=false" || { echo "The fairness rules are wrong"; exit 1; }
  # Limbo's Noble Castle: its walls stand, and its nine shades are home
  rcon "forceload add 2704 -56 2816 56" > /dev/null
  sleep 3
  castle=$(rcon "hellcraft castle test")
  echo "$castle"
  echo "$castle" | grep -q "standing=true; shades=9/9; Shades test: 9 shades, misplaced: none" || { echo "The Noble Castle or its shades are missing"; exit 1; }
  rcon "forceload remove 2704 -56 2816 56" > /dev/null
  # the upper circles' rules: Lust's wind streams, Gluttony's mud, Greed's hoarders, Wrath's sullen
  layers=$(rcon "hellcraft layers test")
  echo "$layers"
  echo "$layers" | grep -q "mud soft=true roots soft=false stone soft=false; 36 wind streams, in Lust=true; sullen spots on a line across the Styx=true" \
    || { echo "The layer rules are wrong"; exit 1; }
  echo "$layers" | grep -q "4 ingots draw 0, 20 draw 1, a stack of blocks (576) draws 6; a gold block is worth 9 ingots" || { echo "Greed's hoarders are wrong"; exit 1; }
  rcon "summon hellcraft:hoarder 2165 200 1250 {NoAI:1b,Tags:[\"smoke_hoarder\"]}"
  hoarder=$(rcon "execute if entity @e[type=hellcraft:hoarder,tag=smoke_hoarder]" || true)
  echo "Hoarder: $hoarder"
  echo "$hoarder" | grep -q "Test passed" || { echo "A hoarder could not be summoned"; exit 1; }
  rcon "kill @e[type=hellcraft:hoarder,tag=smoke_hoarder]" > /dev/null || true
  # Hell's own monsters, upper and lower
  # Lower Hell's rules: the tombs, the Sands' rhythm, the centaurs' posts by the fords, Malebolge's lies
  lower=$(rcon "hellcraft lower test")
  echo "$lower"
  echo "$lower" | grep -q "=false" && { echo "Lower Hell's rules are wrong"; exit 1; }
  echo "$lower" | grep -q "16 centaur posts" || { echo "The fords have no centaur posts"; exit 1; }
  echo "$lower" | grep -q "8 fake Rests" || { echo "Malebolge has no fake Rests"; exit 1; }
  for m in lovers glutton wrathful heretic centaur harpy mimic thief malebranche malacoda; do
    rcon "summon hellcraft:$m 2165 200 1250 {NoAI:1b,Tags:[\"smoke_monster\"]}" > /dev/null
    there=$(rcon "execute if entity @e[type=hellcraft:$m,tag=smoke_monster]" || true)
    echo "$m: $there"
    echo "$there" | grep -q "Test passed" || { echo "A $m could not be summoned"; exit 1; }
  done
  rcon "kill @e[tag=smoke_monster]" > /dev/null || true
  # the blood gear is real items, and the circles' own enchantments loaded
  gear=$(rcon "hellcraft gear test")
  echo "$gear"
  echo "$gear" | grep -q "real items=true, old ones count=true; 5 circle enchantments" || { echo "The blood gear or the circle enchantments are broken"; exit 1; }
  # Antaeus stands in the ice below the Well, his hand on the rim, and the cleft below it is open
  antaeus=$(rcon "hellcraft antaeus test")
  echo "$antaeus"
  echo "$antaeus" | grep -q "Antaeus OK" || { echo "Antaeus's ride is broken"; exit 1; }
  # Cocytus: braziers stand on the ice (unlit), and Lucifer's wings beat across Hell
  brazier=$(rcon "hellcraft brazier test")
  echo "$brazier"
  echo "$brazier" | grep -q "Brazier OK" || { echo "No brazier on the ice of Cocytus"; exit 1; }
  rcon "hellcraft wingbeat" > /dev/null
  # the Fen's ferry: lighting a watchtower's beacons calls it
  ferry=$(rcon "hellcraft ferry test")
  echo "$ferry"
  echo "$ferry" | grep -q "called to tower 1" || { echo "The ferry could not be called"; exit 1; }
  # the dialogue box (what characters say goes there, not in chat)
  dialogue=$(rcon "hellcraft dialogue test")
  echo "$dialogue"
  echo "$dialogue" | grep -q "Dialogue OK" || { echo "The dialogue test failed"; exit 1; }
  # the danger language: red and purple blows can't be blocked (hellcraft:judgement is in minecraft:bypasses_shield)
  danger=$(rcon "hellcraft danger test")
  echo "$danger"
  echo "$danger" | grep -q "bypasses shields: true" || { echo "Unblockable boss blows can be blocked"; exit 1; }
  # the circle guardians: wake each one, give it something to fight, run every attack, then slay it
  declare -A ATTACKS=([minos]="strike tail sentence coil" [cerberus]="bite maws filth howl" [plutus]="fist gold lunge pape" [phlegyas]="oar sweep wave grab" \
                      [furies]="talons shriek lash tear medusa" [farinata]="disdain pyres exile" [minotaur]="gore charge stomp frenzy" \
                      [geryon]="sting falseface coil dive" [vulcan]="strike hammerfall molten chains forgeborn seams")
  for g in minos cerberus plutus phlegyas furies farinata minotaur geryon vulcan; do
    out=$(rcon "hellcraft guardian $g summon")
    echo "$out"
    echo "$out" | grep -q "awakens" || { echo "Guardian $g could not be summoned"; exit 1; }
    sleep 3
    rcon "execute at @e[tag=hellcraft_guardian_body,limit=1] run summon minecraft:villager ~4 ~ ~ {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"smoke_dummy\"]}"
    # every guardian has a real body (animated on the client)
    if [ "$g" = furies ]; then
      shown=$(rcon "execute if entity @e[type=hellcraft:fury,tag=hellcraft_guardian_body]" || true)
    else
      shown=$(rcon "execute if entity @e[type=hellcraft:$g,tag=hellcraft_guardian_body]" || true)
    fi
    echo "$g's model: $shown"
    echo "$shown" | grep -q "Test passed" || { echo "Guardian $g has no model"; exit 1; }
    for a in ${ATTACKS[$g]}; do
      out=$(rcon "hellcraft guardian $g attack $a")
      echo "$out"
      echo "$out" | grep -q " uses $a" || { echo "Guardian $g could not use $a"; exit 1; }
      sleep 3
    done
    rcon "hellcraft guardian $g status"
    rcon "hellcraft guardian $g slay"
    # a real body plays its death (Minos's takes 3.5 s) before it goes
    sleep 6
    left=$(rcon "execute if entity @e[tag=hellcraft_guardian_body]" || true)
    echo "$g left behind: $left"
    echo "$left" | grep -q "Test failed" || { echo "Guardian $g's body was left behind"; exit 1; }
    if [ "$g" = furies ]; then
      gone=$(rcon "execute if entity @e[type=hellcraft:medusa]" || true)
      echo "Medusa after the Furies: $gone"
      echo "$gone" | grep -q "Test failed" || { echo "Medusa outlived the Furies"; exit 1; }
    fi
    if [ "$g" = geryon ]; then
      # beaten, Geryon stays at the brink to fly souls down
      # (by the log: the lair's chunks unload once the fight is over, so a selector can miss him)
      sleep 1
      docker logs "$NAME" > "$LOG" 2>&1 || true
      grep "Geryon perches at the brink" "$LOG" || { echo "Geryon did not stay to carry souls down"; exit 1; }
    fi
    rcon "kill @e[tag=smoke_dummy]"
  done
fi

# Lucifer: run the whole fight against a dummy target in the pit (between the Emperor's body and the ice pillars)
rcon "summon minecraft:villager 20 -50 4 {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b}"
rcon "hellcraft lucifer summon"
second=$(rcon "hellcraft lucifer summon")
echo "$second"
echo "$second" | grep -q "already fighting" || { echo "A second Lucifer could be summoned"; exit 1; }
sleep 18
# the pit must stay loaded (and Lucifer alive, not 'defeated') even if every chunk is unloaded
rcon "forceload remove all"
sleep 8
status=$(rcon "hellcraft lucifer status")
echo "$status"
echo "$status" | grep -q "phase=DUEL" || { echo "Fight advanced on its own after the chunks were unloaded"; exit 1; }
echo "$status" | grep -q "avatar=present" || { echo "Lucifer went missing after the chunks were unloaded"; exit 1; }
echo "$status" | grep -q "trueForm=present" || { echo "The Emperor isn't asleep in the pit during the duel"; exit 1; }
echo "$status" | grep -q "awake=false" || { echo "The Emperor woke too early"; exit 1; }
seraph=$(rcon "execute if entity @e[type=hellcraft:fallen_seraph,tag=hellcraft_lucifer]" || true)
echo "The Fallen Seraph in the duel: $seraph"
echo "$seraph" | grep -q "Test passed" || { echo "The Fallen Seraph is missing from the duel"; exit 1; }
for a in slash sweep stars lunge; do
  out=$(rcon "hellcraft lucifer attack $a")
  echo "$out"
  echo "$out" | grep -q "The Seraph uses" || { echo "The Seraph could not use $a"; exit 1; }
  sleep 4
done
rcon "hellcraft lucifer skip"   # -> enraged
sleep 8
rcon "hellcraft lucifer attack lunge"
sleep 3
rcon "hellcraft lucifer skip"   # -> the Seraph breaks, the Emperor wakes
sleep 16
status=$(rcon "hellcraft lucifer status")
echo "$status"
echo "$status" | grep -q "phase=TRUE_FORM" || { echo "The Emperor's fight did not start"; exit 1; }
echo "$status" | grep -q "awake=true" || { echo "The Emperor did not wake"; exit 1; }
echo "$status" | grep -q "avatar=none\|avatar=missing" || { echo "The Fallen Seraph outlived his break"; exit 1; }
floor=$(echo "$status" | sed -n 's/.*floor=\(-\{0,1\}[0-9]*\).*/\1/p')
out=$(rcon "hellcraft lucifer attack slam")
echo "$out"
echo "$out" | grep -q "The Emperor uses" || { echo "The Emperor could not slam"; exit 1; }
# the hand that lands stays down, glowing: the only place he can be hurt
opened=""
for i in 1 2 3 4 5 6 7 8; do
  sleep 1
  opened=$(rcon "execute if entity @e[type=hellcraft:emperor_weak_point]" || true)
  echo "$opened" | grep -q "Test passed" && break
done
echo "Weak point after the slam: $opened"
echo "$opened" | grep -q "Test passed" || { echo "The Emperor's hand never opened a weak point"; exit 1; }
sleep 4
for a in bite wingbeat weep; do
  out=$(rcon "hellcraft lucifer attack $a")
  echo "$out"
  echo "$out" | grep -q "The Emperor uses" || { echo "The Emperor could not use $a"; exit 1; }
  sleep 6
done
frozen=$(rcon "execute if entity @e[type=hellcraft:emperor,tag=hellcraft_lucifer,x=0.5,y=$floor,z=0.5,distance=..2]" || true)
echo "Emperor at the centre: $frozen"
echo "$frozen" | grep -q "Test passed" || { echo "The Emperor left the centre of the pit"; exit 1; }
# blow a hole in the pit floor: it must freeze back
rcon "setblock 20 $((floor - 1)) 0 minecraft:air"
sleep 2
healed=$(rcon "execute unless block 20 $((floor - 1)) 0 minecraft:air" || true)
echo "Pit floor healed: $healed"
echo "$healed" | grep -q "Test passed" || { echo "The pit floor did not heal"; exit 1; }
rcon "hellcraft lucifer skip"   # -> defeat: he freezes, the climb out
sleep 20
leftover=$(rcon "execute if entity @e[tag=hellcraft_lucifer]" || true)
echo "leftover Lucifer entities: $leftover"
echo "$leftover" | grep -q "Test failed" || { echo "Lucifer entities left behind after the fight"; exit 1; }
# the way out: the burrow in the ice at the Emperor's back (LuciferFight.burrowAt: 16 blocks west of the centre),
# and the Gate of Return on Purgatory's summit
rcon "forceload add -16 0" > /dev/null
burrow=$(rcon "execute if block -16 $floor 0 minecraft:end_gateway" || true)
echo "Burrow in the pit: $burrow"
echo "$burrow" | grep -q "Test passed" || { echo "No burrow opened behind Lucifer"; exit 1; }
summit=$(rcon "execute if block 0 291 0 minecraft:end_gateway" || true)
echo "Gate of Return on the summit: $summit"
echo "$summit" | grep -q "Test passed" || { echo "The Mountain of Purgatory has no Gate of Return"; exit 1; }
purgatory=$(rcon "hellcraft purgatory" || true)
echo "$purgatory"
echo "$purgatory" | grep -qi "unknown or incomplete command" && { echo "/hellcraft purgatory is not registered"; exit 1; }
rcon "hellcraft ghosts"
rcon "stop" || true
sleep 15

docker logs "$NAME" > "$LOG" 2>&1 || true
echo "---- hellcraft log lines ----"
grep -i 'hellcraft\|inferno\|Gate of Hell\|Lucifer\|Reliquary\|Arena' "$LOG" || true

fail=0
grep -q 'The Gate of Hell stands' "$LOG" || { echo "Landmarks were not built (is the overworld an Inferno world?)"; fail=1; }
grep -qE 'Gate wall: [1-9][0-9]* blocks' "$LOG" || { echo "The Gate of Hell wall was not generated"; fail=1; }
for phase in INTRO DUEL ENRAGE_TRANSITION ENRAGED TRUE_FORM_TRANSITION TRUE_FORM DEFEAT DONE; do
  grep -qE "Lucifer phase: ${phase}(\s|\r|$)" "$LOG" || { echo "Lucifer never reached phase $phase"; fail=1; }
done
grep -q 'Lucifer rewards:' "$LOG" || { echo "No reward hand-out"; fail=1; }
if [ "$MC_VERSION" = "1.21.1" ] && [ -f "$CONFIG_DIR/hellcraft/music/duel.ogg" ]; then
  grep -q 'Music pack ready: 1 track' "$LOG" || { echo "Music pack was not built"; fail=1; }
fi
grep -q 'Arena unsealed' "$LOG" || { echo "Arena never unsealed"; fail=1; }
if [ "$MC_VERSION" != "1.21.1" ]; then
  grep -q 'Boss music: ' "$LOG" || { echo "Boss music was not set up"; fail=1; }
  grep -q "The Emperor's Spine runs from" "$LOG" || { echo "The Emperor's Spine was not laid"; fail=1; }
  grep -q "The Hall of the Damned at" "$LOG" || { echo "The Hall of the Damned was never raised"; fail=1; }
  grep -q "Villager clock running" "$LOG" || { echo "The villagers' own clock is missing, so they would never idle or breed"; fail=1; }
  adv=$(grep -oE 'Hellcraft advancements: [0-9]+' "$LOG" | tail -1 | grep -oE '[0-9]+$' || echo 0)
  [ "${adv:-0}" -ge 25 ] || { echo "Only ${adv:-0} Dante's Journey advancements loaded"; fail=1; }
  grep -qE "Hellcraft recipes: 9; take Blood Hearts: 3; take Blood Fragments: 8(\s|\r|$)" "$LOG" || { grep "Hellcraft recipes" "$LOG"; echo "Real blood items don't fit the recipes"; fail=1; }
  grep -q "Hellcraft config updated from version 0 to 3" "$LOG" || { echo "Config migration was not logged"; fail=1; }
  [ "$(grep -c "Virgil's Rest (limbo_" "$LOG")" -ge 3 ] || { echo "The Virgil's Rests were not logged"; fail=1; }
  grep -q "Paradiso: 10 spheres ready" "$LOG" || { echo "The Paradiso biomes were not found"; fail=1; }
  grep -q "The Great Forge of Dis at" "$LOG" || { echo "The Great Forge of Dis was not built"; fail=1; }
  grep -q "Guardian slain: vulcan" "$LOG" || { echo "Vulcan was never slain"; fail=1; }
  grep -q "The Celestial Rose blooms at" "$LOG" || { echo "The Celestial Rose was not built"; fail=1; }
  grep -q "The Ascent opens on the summit" "$LOG" || { echo "The Ascent was not built"; fail=1; }
  grep -q "The Mountain of Purgatory rises" "$LOG" || { echo "Purgatory was not raised"; fail=1; }
  grep -q "The burrow opens" "$LOG" || { echo "The burrow never opened"; fail=1; }
  # the true form is killed with real damage: its death must reach the fight (the event, or the fight's own watch)
  grep -E "Lucifer's true form is (slain|dead)" "$LOG" || { echo "Lucifer's death never reached the fight"; fail=1; }
  grep -q "Antaeus stands in the ice at" "$LOG" || { echo "Antaeus was not placed"; fail=1; }
  grep -q "Lucifer's wings beat" "$LOG" || { echo "Lucifer's wingbeat never came"; fail=1; }
  for g in minos cerberus plutus phlegyas furies farinata minotaur geryon; do
    grep -q "Lair of $g at" "$LOG" || { echo "No lair for $g"; fail=1; }
    grep -q "Guardian slain: $g" "$LOG" || { echo "Guardian $g was never slain"; fail=1; }
  done
fi
if grep -nE 'ERROR\]|Exception|Caused by|Feature order cycle' "$LOG" | grep -vE 'rcon|RCON' ; then
  echo "Errors found in server log"; fail=1
fi
exit $fail
