#!/usr/bin/env bash
# Starts itzg/minecraft-server with Hellcraft, generates chunks across every circle and checks the log.
# Usage: scripts/smoke-test.sh <dir-containing-hellcraft-jar>
set -euo pipefail
MODS_DIR=$(cd "${1:-build/libs}" && pwd)
rm -f "$MODS_DIR"/*-sources.jar
NAME=hellcraft-smoke
LOG=smoke-server.log
docker rm -f "$NAME" >/dev/null 2>&1 || true

docker run -d --name "$NAME" \
  -e EULA=TRUE -e TYPE=FABRIC -e VERSION=1.21.1 \
  -e MODRINTH_PROJECTS=fabric-api \
  -e LEVEL_TYPE=hellcraft:inferno \
  -e ONLINE_MODE=FALSE -e MEMORY=3G -e ENABLE_RCON=true -e RCON_PASSWORD=smoketest \
  -e VIEW_DISTANCE=4 -e SIMULATION_DISTANCE=4 \
  -v "$MODS_DIR":/mods:ro \
  itzg/minecraft-server:java21

cleanup() { docker logs "$NAME" > "$LOG" 2>&1 || true; docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "Waiting for the server to start..."
for i in $(seq 1 180); do
  if docker logs "$NAME" 2>&1 | grep -q 'Done ('; then break; fi
  if [ "$(docker inspect -f '{{.State.Running}}' "$NAME")" != "true" ]; then
    docker logs "$NAME" 2>&1 | tail -150; echo "Server exited during startup"; exit 1
  fi
  sleep 5
done
docker logs "$NAME" 2>&1 | grep -q 'Done (' || { docker logs "$NAME" 2>&1 | tail -150; echo "Server did not start in time"; exit 1; }

rcon() { docker exec "$NAME" rcon-cli "$@"; }

rcon "hellcraft where"
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
rcon "hellcraft ghosts"
rcon "stop" || true
sleep 15

docker logs "$NAME" > "$LOG" 2>&1 || true
echo "---- hellcraft log lines ----"
grep -i 'hellcraft\|inferno\|Gate of Hell' "$LOG" || true

fail=0
grep -q 'The Gate of Hell stands' "$LOG" || { echo "Landmarks were not built (is the overworld an Inferno world?)"; fail=1; }
if grep -nE 'ERROR\]|Exception|Caused by|Feature order cycle' "$LOG" | grep -vE 'rcon|RCON' ; then
  echo "Errors found in server log"; fail=1
fi
exit $fail
