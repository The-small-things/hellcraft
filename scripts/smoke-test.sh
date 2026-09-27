#!/usr/bin/env bash
# Starts itzg/minecraft-server with Hellcraft, generates chunks across every circle and checks the log.
# Usage: scripts/smoke-test.sh <dir-containing-hellcraft-jar>
set -euo pipefail
MODS_DIR=$(cd "${1:-build/libs}" && pwd)
rm -f "$MODS_DIR"/*-sources.jar
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
  -e EULA=TRUE -e TYPE=FABRIC -e VERSION=1.21.1 \
  -e MODRINTH_PROJECTS=fabric-api \
  -e LEVEL_TYPE=hellcraft:inferno \
  -e ONLINE_MODE=FALSE -e MEMORY=3G -e ENABLE_RCON=true -e RCON_PASSWORD=smoketest \
  -e VIEW_DISTANCE=4 -e SIMULATION_DISTANCE=4 \
  -v "$MODS_DIR":/mods:ro \
  -v "$CONFIG_DIR":/config:ro \
  -p 25566:25566 \
  itzg/minecraft-server:java21

cleanup() { docker logs "$NAME" > "$LOG" 2>&1 || true; docker rm -f "$NAME" >/dev/null 2>&1 || true; }
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

rcon() { docker exec "$NAME" rcon-cli "$@"; }

rcon "hellcraft where"
rcon "hellcraft givebane nobody" || true  # needs a player; checks the command is registered

if [ -f "$CONFIG_DIR/hellcraft/music/duel.ogg" ]; then
  curl -fsS -o music-pack.zip http://localhost:25566/hellcraft-music.zip || { echo "Music pack not served"; exit 1; }
  unzip -l music-pack.zip
  unzip -l music-pack.zip | grep -q 'assets/hellcraft/sounds.json' || { echo "Pack has no sounds.json"; exit 1; }
  unzip -l music-pack.zip | grep -q 'assets/hellcraft/sounds/music/duel.ogg' || { echo "Pack has no track"; exit 1; }
  unzip -p music-pack.zip assets/hellcraft/sounds.json
  curl -s -o /dev/null -w '%{http_code}' http://localhost:25566/anything-else | grep -q 404 || { echo "Web server serves more than the pack"; exit 1; }
fi
rcon "hellcraft goto judecca" || true  # needs a player; checks the command is registered
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

# Lucifer: run the whole fight against a dummy target in the pit
rcon "summon minecraft:villager 4 -45 0 {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b}"
rcon "hellcraft lucifer summon"
sleep 18
for a in slash fangs wings hellfire; do rcon "hellcraft lucifer attack $a"; sleep 3; done
rcon "hellcraft lucifer skip"   # -> enraged
sleep 8
rcon "hellcraft lucifer attack fangs"
sleep 3
rcon "hellcraft lucifer skip"   # -> true form
sleep 10
rcon "hellcraft lucifer skip"   # -> defeat
sleep 16
rcon "hellcraft ghosts"
rcon "stop" || true
sleep 15

docker logs "$NAME" > "$LOG" 2>&1 || true
echo "---- hellcraft log lines ----"
grep -i 'hellcraft\|inferno\|Gate of Hell\|Lucifer\|Reliquary\|Arena' "$LOG" || true

fail=0
grep -q 'The Gate of Hell stands' "$LOG" || { echo "Landmarks were not built (is the overworld an Inferno world?)"; fail=1; }
for phase in INTRO DUEL ENRAGED TRUE_FORM DEFEAT DONE; do
  grep -qE "Lucifer phase: ${phase}(\s|\r|$)" "$LOG" || { echo "Lucifer never reached phase $phase"; fail=1; }
done
grep -q 'Lucifer rewards:' "$LOG" || { echo "No reward hand-out"; fail=1; }
if [ -f "$CONFIG_DIR/hellcraft/music/duel.ogg" ]; then
  grep -q 'Music pack ready: 1 track' "$LOG" || { echo "Music pack was not built"; fail=1; }
fi
grep -q 'Arena unsealed' "$LOG" || { echo "Arena never unsealed"; fail=1; }
if grep -nE 'ERROR\]|Exception|Caused by|Feature order cycle' "$LOG" | grep -vE 'rcon|RCON' ; then
  echo "Errors found in server log"; fail=1
fi
exit $fail
