#!/usr/bin/env bash
# Builds ready-to-play single-player downloads for one Minecraft version of Hellcraft:
#   dist/Hellcraft-<ver>-mc<mc>.mrpack       one-click import for Modrinth App / Prism / ATLauncher / CurseForge
#   dist/hellcraft-<ver>-mc<mc>-mods.zip     Hellcraft + Fabric API jars + INSTALL.txt for the official launcher
#   dist/hellcraft-<ver>-mc<mc>.jar          the mod itself (servers)
#   dist/hellcraft-pack-<ver>-mc<mc>.zip     its resource pack (26.3), which servers hand to players who
#                                            join through a tunnel (playit.gg) that can't reach port 25566
# Usage: scripts/package-singleplayer.sh [project-dir]   (default: the 1.21.1 project at the repo root;
# versions/26.3 for the 26.3 build). Run after ./gradlew build in that project.
# Needs network access (Modrinth API), curl, jq and python3.
set -euo pipefail
cd "$(dirname "$0")/.."
PROJECT=${1:-.}

prop() { grep "^$1=" "$PROJECT/gradle.properties" | cut -d= -f2-; }
MC=$(prop minecraft_version)
LOADER=$(prop loader_version)
FAPI=$(prop fabric_api_version)
VER=$(prop mod_version)
JAR=$(ls "$PROJECT"/build/libs/hellcraft-*.jar 2>/dev/null | grep -v -- '-sources' | head -1 || true)
[ -n "$JAR" ] && [ -f "$JAR" ] || { echo "No jar in $PROJECT/build/libs - run ./gradlew build first"; exit 1; }
LABEL="${VER}-mc${MC}"
MOD_JAR="hellcraft-${LABEL}.jar"

DIST=dist
WORK=$(mktemp -d)
mkdir -p "$DIST"
rm -f "$DIST"/*"-mc${MC}"*

echo "Looking up Fabric API ${FAPI} on Modrinth..."
curl -fsSL -G "https://api.modrinth.com/v2/project/fabric-api/version" \
  --data-urlencode "game_versions=[\"${MC}\"]" --data-urlencode 'loaders=["fabric"]' \
  -H "User-Agent: the-small-things/hellcraft (build script)" -o "$WORK/versions.json"
jq -e --arg v "$FAPI" 'map(select(.version_number == $v)) | length > 0' "$WORK/versions.json" >/dev/null || {
  echo "Fabric API ${FAPI} is not on Modrinth; using the newest ${MC} release instead"
  FAPI=$(jq -r 'map(select(.version_type == "release")) | .[0].version_number' "$WORK/versions.json")
}
jq --arg v "$FAPI" 'map(select(.version_number == $v))[0].files | map(select(.primary))[0] // .[0]' "$WORK/versions.json" > "$WORK/file.json"
FAPI_URL=$(jq -r .url "$WORK/file.json")
FAPI_NAME=$(jq -r .filename "$WORK/file.json")
FAPI_SHA1=$(jq -r .hashes.sha1 "$WORK/file.json")
FAPI_SHA512=$(jq -r .hashes.sha512 "$WORK/file.json")
FAPI_SIZE=$(jq -r .size "$WORK/file.json")

curl -fsSL "$FAPI_URL" -o "$WORK/$FAPI_NAME"
echo "${FAPI_SHA1}  $WORK/$FAPI_NAME" | sha1sum -c -

# ---- Modrinth modpack
mkdir -p "$WORK/mrpack/overrides/mods"
cp "$JAR" "$WORK/mrpack/overrides/mods/$MOD_JAR"
jq -n --arg ver "$VER" --arg mc "$MC" --arg loader "$LOADER" \
  --arg name "$FAPI_NAME" --arg url "$FAPI_URL" --arg sha1 "$FAPI_SHA1" --arg sha512 "$FAPI_SHA512" --argjson size "$FAPI_SIZE" '{
    formatVersion: 1, game: "minecraft", versionId: $ver,
    name: "Hellcraft \($ver) (Minecraft \($mc))",
    summary: "Dante'"'"'s Inferno lifesteal. Create a new world with World Type: Inferno.",
    files: [{path: "mods/\($name)", hashes: {sha1: $sha1, sha512: $sha512},
             env: {client: "required", server: "required"}, downloads: [$url], fileSize: $size}],
    dependencies: {minecraft: $mc, "fabric-loader": $loader}
  }' > "$WORK/mrpack/modrinth.index.json"

# ---- The resource pack, as the server builds it (without custom music); 26.x only
[[ "$MC" == 26* ]] && python3 - "$JAR" "$DIST/hellcraft-pack-${LABEL}.zip" <<'PY'
import sys, zipfile
jar, out = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(jar) as src:
    names = sorted(n for n in src.namelist() if n.startswith(("assets/hellcraft/", "assets/minecraft/")) and not n.endswith("/"))
    if names:
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as dst:
            meta = '{"pack": {"min_format": [97, 0], "max_format": [99, 0], "description": "Hellcraft"}}\n'
            dst.writestr(zipfile.ZipInfo("pack.mcmeta", (1980, 1, 1, 0, 0, 0)), meta)
            for n in names:
                dst.writestr(zipfile.ZipInfo(n, (1980, 1, 1, 0, 0, 0)), src.read(n), zipfile.ZIP_DEFLATED)
        print("Resource pack:", out, len(names), "assets")
PY

# ---- Official launcher bundle
mkdir -p "$WORK/zip/mods"
cp "$JAR" "$WORK/zip/mods/$MOD_JAR"
cp "$WORK/$FAPI_NAME" "$WORK/zip/mods/"
cat > "$WORK/zip/INSTALL.txt" <<EOF
Hellcraft ${VER} - single player (official Minecraft Launcher)

1. Install Fabric Loader for Minecraft ${MC}:
   download the installer from https://fabricmc.net/use/installer/ , run it,
   pick Minecraft ${MC} and click Install. A "fabric-loader-${MC}" profile appears in the launcher.
2. Copy both jars from the "mods" folder here into your .minecraft/mods folder
   (Windows: %APPDATA%\\.minecraft\\mods   macOS: ~/Library/Application Support/minecraft/mods
    Linux: ~/.minecraft/mods). Create the folder if it doesn't exist.
3. Start the "fabric-loader-${MC}" profile.
4. Singleplayer -> Create New World -> "World" tab -> click "World Type" until it says "Inferno".
   On the "Game" tab turn on "Allow Commands" if you want the test commands (/hellcraft goto <zone>).

Everything is explained in the README: https://github.com/the-small-things/hellcraft
EOF

python3 - "$WORK" "$DIST" "$LABEL" <<'PY'
import os, sys, zipfile
work, dist, ver = sys.argv[1:4]
def pack(src, out):
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(src):
            for f in files:
                full = os.path.join(root, f)
                z.write(full, os.path.relpath(full, src))
pack(os.path.join(work, "mrpack"), os.path.join(dist, f"Hellcraft-{ver}.mrpack"))
pack(os.path.join(work, "zip"), os.path.join(dist, f"hellcraft-{ver}-mods.zip"))
PY
cp "$JAR" "$DIST/$MOD_JAR"

# ---- sanity checks
unzip -l "$DIST/Hellcraft-${LABEL}.mrpack" | grep -q "overrides/mods/${MOD_JAR}"
unzip -p "$DIST/Hellcraft-${LABEL}.mrpack" modrinth.index.json | jq -e '.files | length == 1' >/dev/null
unzip -l "$DIST/hellcraft-${LABEL}-mods.zip" | grep -q "$FAPI_NAME"
echo "Fabric API: $FAPI_NAME"
ls -la "$DIST"
