#!/usr/bin/env bash
# Builds ready-to-play single-player downloads from build/libs/hellcraft-<ver>.jar:
#   dist/Hellcraft-<ver>.mrpack        one-click import for Modrinth App / Prism / ATLauncher / CurseForge
#   dist/hellcraft-<ver>-mods.zip      Hellcraft + Fabric API jars + INSTALL.txt for the official launcher
# Needs network access (Modrinth API), curl, jq and python3. Run after ./gradlew build.
set -euo pipefail
cd "$(dirname "$0")/.."

prop() { grep "^$1=" gradle.properties | cut -d= -f2-; }
MC=$(prop minecraft_version)
LOADER=$(prop loader_version)
FAPI=$(prop fabric_api_version)
VER=$(prop mod_version)
JAR="build/libs/hellcraft-${VER}.jar"
[ -f "$JAR" ] || { echo "Missing $JAR - run ./gradlew build first"; exit 1; }

DIST=dist
WORK=$(mktemp -d)
rm -rf "$DIST" && mkdir -p "$DIST"

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
cp "$JAR" "$WORK/mrpack/overrides/mods/"
jq -n --arg ver "$VER" --arg mc "$MC" --arg loader "$LOADER" \
  --arg name "$FAPI_NAME" --arg url "$FAPI_URL" --arg sha1 "$FAPI_SHA1" --arg sha512 "$FAPI_SHA512" --argjson size "$FAPI_SIZE" '{
    formatVersion: 1, game: "minecraft", versionId: $ver,
    name: "Hellcraft \($ver)",
    summary: "Dante'"'"'s Inferno lifesteal. Create a new world with World Type: Inferno.",
    files: [{path: "mods/\($name)", hashes: {sha1: $sha1, sha512: $sha512},
             env: {client: "required", server: "required"}, downloads: [$url], fileSize: $size}],
    dependencies: {minecraft: $mc, "fabric-loader": $loader}
  }' > "$WORK/mrpack/modrinth.index.json"

# ---- Official launcher bundle
mkdir -p "$WORK/zip/mods"
cp "$JAR" "$WORK/$FAPI_NAME" "$WORK/zip/mods/"
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

python3 - "$WORK" "$DIST" "$VER" <<'PY'
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
cp "$JAR" "$DIST/"

# ---- sanity checks
unzip -l "$DIST/Hellcraft-${VER}.mrpack" | grep -q "overrides/mods/hellcraft-${VER}.jar"
unzip -p "$DIST/Hellcraft-${VER}.mrpack" modrinth.index.json | jq -e '.files | length == 1' >/dev/null
unzip -l "$DIST/hellcraft-${VER}-mods.zip" | grep -q "$FAPI_NAME"
echo "Fabric API: $FAPI_NAME"
ls -la "$DIST"
