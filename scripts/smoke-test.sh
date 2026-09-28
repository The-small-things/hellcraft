#!/usr/bin/env bash
# Starts itzg/minecraft-server with Hellcraft, generates chunks across every circle and checks the log.
# Usage: scripts/smoke-test.sh <dir-containing-hellcraft-jar>
# MC_VERSION / MC_IMAGE pick the Minecraft version and Java image (default 1.21.1 on java21).
set -euo pipefail
MODS_DIR=$(cd "${1:-build/libs}" && pwd)
rm -f "$MODS_DIR"/*-sources.jar
MC_VERSION=${MC_VERSION:-1.21.1}
MC_IMAGE=${MC_IMAGE:-itzg/minecraft-server:java21}
NAME=hellcraft-smoke
LOG=smoke-server.log
docker rm -f "$NAME" >/dev/null 2>&1 || true

# A short generated track so the boss-music pack is built and served
CONFIG_DIR=$(mktemp -d)
mkdir -p "$CONFIG_DIR/hellcraft/music"
echo '{"musicPackHost": "localhost"}' > "$CONFIG_DIR/hellcraft.json"
if command -v ffmpeg >/dev/null; then
  ffmpeg -loglevel error -f lavfi -i "sine=frequency=220:duration=3" -c:a libvorbis "$CONFIG_DIR/hellcraft/music/duel.ogg"
fi
chmod -R a+rwX "$CONFIG_DIR"

docker run -d --name "$NAME" \
  -e EULA=TRUE -e TYPE=FABRIC -e VERSION="$MC_VERSION" \
  -e MODRINTH_PROJECTS=fabric-api \
  -e LEVEL_TYPE=hellcraft:inferno \
  -e ONLINE_MODE=FALSE -e MEMORY=3G -e ENABLE_RCON=true -e RCON_PASSWORD=smoketest \
  -e VIEW_DISTANCE=4 -e SIMULATION_DISTANCE=4 \
  -e PAUSE_WHEN_EMPTY_SECONDS=0 \
  -v "$MODS_DIR":/mods:ro \
  -v "$CONFIG_DIR":/config:ro \
  -p 25566:25566 \
  "$MC_IMAGE"

cleanup() {
  status=$?
  docker logs "$NAME" > "$LOG" 2>&1 || true
  if [ "$status" != 0 ]; then
    echo "---- server log (warnings, errors, Hellcraft) ----"
    grep -iE 'WARN|ERROR|Exception|hellcraft|lucifer|summon|display' "$LOG" | grep -v 'Marked chunk' | tail -80 || true
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

if [ -f "$CONFIG_DIR/hellcraft/music/duel.ogg" ]; then
  curl -fsS -o music-pack.zip http://localhost:25566/hellcraft-music.zip || { echo "Music pack not served"; exit 1; }
  unzip -l music-pack.zip
  unzip -l music-pack.zip | grep -q 'assets/hellcraft/sounds.json' || { echo "Pack has no sounds.json"; exit 1; }
  unzip -l music-pack.zip | grep -q 'assets/hellcraft/sounds/music/duel.ogg' || { echo "Pack has no track"; exit 1; }
  unzip -p music-pack.zip assets/hellcraft/sounds.json
  if [ "$MC_VERSION" != "1.21.1" ]; then
    for f in assets/hellcraft/items/blood_heart.json assets/hellcraft/models/item/lucifer_morning_star.json \
             assets/hellcraft/models/item/lucifer_emperor.json assets/hellcraft/textures/entity/lucifer_emperor.png \
             assets/hellcraft/textures/item/tithe_axe.png; do
      unzip -l music-pack.zip | grep -q "$f" || { echo "Resource pack is missing $f"; exit 1; }
    done
  fi
  curl -s -o /dev/null -w '%{http_code}' http://localhost:25566/anything-else | grep -q 404 || { echo "Web server serves more than the pack"; exit 1; }
fi
rcon "hellcraft goto judecca" || true  # needs a player; checks the command is registered
if [ "$MC_VERSION" != "1.21.1" ]; then
  weapon=$(rcon "hellcraft giveweapon nobody bloodletter" || true)
  echo "$weapon"
  echo "$weapon" | grep -qi "unknown or incomplete command" && { echo "/hellcraft giveweapon is not registered"; exit 1; }
fi
rcon "locate biome hellcraft:judecca"
rcon "locate biome hellcraft:limbo"
rcon "locate structure minecraft:stronghold" || true

# Force-generate one chunk in every ring, from the rim down to Lucifer's pit (and the Walls of Dis gate)
for x in 5700 5200 4950 4600 4000 3600 3100 2700 2425 2200 1950 1750 1520 1400 1100 800 650 400 100 0; do
  rcon "forceload add $x 0"
done
rcon "forceload add 1715 1715"
echo "Generating..."
sleep 60
rcon "forceload query" || true
if [ "$MC_VERSION" != "1.21.1" ]; then
  # the Emperor's Spines: bone walkways over Cocytus at all four points of the compass
  for p in "400 0" "0 400" "-400 0" "0 -400"; do
    read -r px pz <<< "$p"
    rcon "forceload add $px $pz" > /dev/null
    deck=$(rcon "execute if block $px -13 $pz minecraft:bone_block" || true)
    echo "Spine deck at $px,$pz: $deck"
    echo "$deck" | grep -q "Test passed" || { echo "No Emperor's Spine at $px,$pz"; exit 1; }
  done
  stairs=$(rcon "execute if block 100 -26 0 minecraft:bone_block" || true)
  echo "Spine stairs at x=100: $stairs"
  echo "$stairs" | grep -q "Test passed" || { echo "The Spine's staircase is not where it should be"; exit 1; }
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
  echo "$objectives" | grep -q "hellcraft_hearts" || { echo "No hearts in the player list"; exit 1; }
  guide=$(rcon "guide" || true)
  echo "$guide"
  echo "$guide" | grep -qi "unknown or incomplete command" && { echo "/guide is not registered"; exit 1; }
fi

# Lucifer: run the whole fight against a dummy target in the pit
rcon "summon minecraft:villager 4 -45 0 {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b}"
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
if [ "$MC_VERSION" != "1.21.1" ]; then
  shown=$(rcon "execute if entity @e[type=minecraft:item_display,tag=hellcraft_lucifer_model]" || true)
  echo "Lucifer's model in the duel: $shown"
  echo "$shown" | grep -q "Test passed" || { echo "Lucifer has no model in the duel"; exit 1; }
fi
for a in slash fangs wings hellfire; do rcon "hellcraft lucifer attack $a"; sleep 3; done
rcon "hellcraft lucifer skip"   # -> enraged
sleep 8
rcon "hellcraft lucifer attack fangs"
sleep 3
rcon "hellcraft lucifer skip"   # -> true form
sleep 10
if [ "$MC_VERSION" != "1.21.1" ]; then
  shown=$(rcon "execute if entity @e[type=minecraft:item_display,tag=hellcraft_lucifer_model]" || true)
  echo "Lucifer's model in his true form: $shown"
  echo "$shown" | grep -q "Test passed" || { echo "Lucifer has no model in his true form"; exit 1; }
  status=$(rcon "hellcraft lucifer status")
  echo "$status"
  echo "$status" | grep -q "emperorStage=1" || { echo "The Emperor's attacks did not start"; exit 1; }
  floor=$(echo "$status" | sed -n 's/.*floor=\(-\{0,1\}[0-9]*\).*/\1/p')
  for a in hatred impotence ignorance mouths wingbeat; do
    out=$(rcon "hellcraft lucifer attack $a")
    echo "$out"
    echo "$out" | grep -q "The Emperor uses" || { echo "The Emperor could not use $a"; exit 1; }
    sleep 4
  done
  frozen=$(rcon "execute if entity @e[type=minecraft:wither,tag=hellcraft_lucifer,x=0.5,y=$floor,z=0.5,distance=..2]" || true)
  echo "Emperor frozen at the centre: $frozen"
  echo "$frozen" | grep -q "Test passed" || { echo "The Emperor left the centre of the pit"; exit 1; }
  # blow a hole in the pit floor: it must freeze back
  rcon "setblock 2 $((floor - 1)) 0 minecraft:air"
  sleep 2
  healed=$(rcon "execute unless block 2 $((floor - 1)) 0 minecraft:air" || true)
  echo "Pit floor healed: $healed"
  echo "$healed" | grep -q "Test passed" || { echo "The pit floor did not heal"; exit 1; }
fi
rcon "hellcraft lucifer skip"   # -> defeat
sleep 16
leftover=$(rcon "execute if entity @e[tag=hellcraft_lucifer]" || true)
echo "leftover Lucifer entities: $leftover"
echo "$leftover" | grep -q "Test failed" || { echo "Lucifer entities left behind after the fight"; exit 1; }
rcon "hellcraft ghosts"
rcon "stop" || true
sleep 15

docker logs "$NAME" > "$LOG" 2>&1 || true
echo "---- hellcraft log lines ----"
grep -i 'hellcraft\|inferno\|Gate of Hell\|Lucifer\|Reliquary\|Arena' "$LOG" || true

fail=0
grep -q 'The Gate of Hell stands' "$LOG" || { echo "Landmarks were not built (is the overworld an Inferno world?)"; fail=1; }
grep -qE 'Gate wall: [1-9][0-9]* blocks' "$LOG" || { echo "The Gate of Hell wall was not generated"; fail=1; }
for phase in INTRO DUEL ENRAGED TRUE_FORM DEFEAT DONE; do
  grep -qE "Lucifer phase: ${phase}(\s|\r|$)" "$LOG" || { echo "Lucifer never reached phase $phase"; fail=1; }
done
grep -q 'Lucifer rewards:' "$LOG" || { echo "No reward hand-out"; fail=1; }
if [ -f "$CONFIG_DIR/hellcraft/music/duel.ogg" ]; then
  grep -q 'Music pack ready: 1 track' "$LOG" || { echo "Music pack was not built"; fail=1; }
fi
grep -q 'Arena unsealed' "$LOG" || { echo "Arena never unsealed"; fail=1; }
if [ "$MC_VERSION" != "1.21.1" ]; then
  grep -qE 'Hellcraft pack ready: [1-9][0-9]* asset files' "$LOG" || { echo "The Hellcraft resource pack was not built"; fail=1; }
  grep -q "The Emperor's Spine runs from" "$LOG" || { echo "The Emperor's Spine was not laid"; fail=1; }
  grep -q 'Lucifer model: lucifer_emperor' "$LOG" || { echo "The Emperor's model was never attached"; fail=1; }
fi
if grep -nE 'ERROR\]|Exception|Caused by|Feature order cycle' "$LOG" | grep -vE 'rcon|RCON' ; then
  echo "Errors found in server log"; fail=1
fi
exit $fail
