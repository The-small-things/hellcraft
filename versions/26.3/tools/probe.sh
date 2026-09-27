#!/usr/bin/env bash
# Porting aid: prints Minecraft 26.3 class signatures (javap) for the classes listed in probe.txt.
# Lines: "find <regex>" lists matching class names; "<SimpleOrQualifiedName> [grep-regex]" prints members.
set -uo pipefail
cd "$(dirname "$0")/.."
./gradlew --no-daemon -q dependencies --configuration compileClasspath > /dev/null 2>&1 || true
JAR=""
for j in $(find ~/.gradle/caches -name '*.jar' -size +10M 2>/dev/null | grep -i minecraft); do
  if unzip -l "$j" 2>/dev/null | grep -q 'net/minecraft/server/MinecraftServer.class'; then JAR="$j"; break; fi
done
echo "Minecraft jar: $JAR"
[ -n "$JAR" ] || { find ~/.gradle/caches -name '*.jar' | grep -i -E 'minecraft|loom' | head -40; exit 1; }
LIBS=$(find ~/.gradle/caches/modules-2 -name '*.jar' | tr '\n' ':')
unzip -Z1 "$JAR" | grep '\.class$' | sed 's/\.class$//; s|/|.|g' > /tmp/classes.txt
echo "$(wc -l < /tmp/classes.txt) classes"
while IFS= read -r line; do
  [ -z "$line" ] && continue
  case "$line" in \#*) continue;; esac
  echo "################ $line"
  if [[ "$line" == find\ * ]]; then grep -E "${line#find }" /tmp/classes.txt | head -60; continue; fi
  cls=${line%% *}; pat=""; [[ "$line" == *" "* ]] && pat=${line#* }
  if [[ "$cls" != *.* ]]; then
    matches=$(grep -E "(^|\.)${cls}$" /tmp/classes.txt)
  else
    matches=$cls
  fi
  for m in $matches; do
    echo "== $m"
    if [ -n "$pat" ]; then javap -cp "$JAR:$LIBS" -p "$m" 2>&1 | grep -E "$pat|class |interface |record |enum "
    else javap -cp "$JAR:$LIBS" -p "$m" 2>&1 | head -150; fi
  done
done < tools/probe.txt
