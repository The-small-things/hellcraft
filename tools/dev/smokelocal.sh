#!/usr/bin/env bash
# smokelocal.sh: runs scripts/smoke-test.sh in a cloud container, where CI can't be used (Actions stuck, or before
# pushing). Usage: bash tools/dev/smokelocal.sh [mod jar] (default: the jar in versions/26.3/build/libs).
# Differences from CI, all in a scratch copy of the script (the script in the repo is untouched):
# - Modrinth is blocked, so Fabric API comes from maven.fabricmc.net instead of MODRINTH_PROJECTS;
# - Docker containers can't reach the agent proxy or trust its CA, so the server runs on the host network with the
#   proxy and the host's Java trust store passed in;
# - Mojang's services (minecraftservices.com) don't resolve here, so their errors are left out of the log scan.
# Needs Docker (start it with `dockerd &` if `docker info` fails). Takes about ten minutes.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
JAR=${1:-$(ls "$ROOT"/versions/26.3/build/libs/hellcraft-*.jar | grep -v sources | head -1)}
FAPI=$(grep '^fabric_api_version=' "$ROOT/versions/26.3/gradle.properties" | cut -d= -f2)
WORK=$(mktemp -d)
mkdir -p "$WORK/mods"
cp "$JAR" "$WORK/mods/"
curl -fsS -o "$WORK/mods/fabric-api-$FAPI.jar" \
  "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$FAPI/fabric-api-$FAPI.jar"

PROXY=${HTTPS_PROXY:-}
PORT=${PROXY##*:}
PORT=${PORT%%/*}
python3 - "$ROOT/scripts/smoke-test.sh" "$WORK/smoke-test.sh" "$PORT" <<'PY'
import sys
src, dst, port = sys.argv[1:]
s = open(src).read()
s = s.replace('  -e MODRINTH_PROJECTS=fabric-api \\\n', '')
run = 'docker run -d --name "$NAME" \\\n'
if port:
    proxy = f'http://127.0.0.1:{port}'
    java = (f'-Djavax.net.ssl.trustStore=/hostcacerts -Djavax.net.ssl.trustStorePassword=changeit '
            f'-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort={port} -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort={port} '
            f'-Dhttp.nonProxyHosts=localhost|127.*')
    s = s.replace(run, run +
        '  --network host -v /etc/ssl/certs/java/cacerts:/hostcacerts:ro -v /root/.ccr/ca-bundle.crt:/hostca.crt:ro \\\n'
        f'  -e HTTPS_PROXY={proxy} -e HTTP_PROXY={proxy} -e https_proxy={proxy} \\\n'
        '  -e SSL_CERT_FILE=/hostca.crt -e CURL_CA_BUNDLE=/hostca.crt \\\n'
        f'  -e JAVA_TOOL_OPTIONS="{java}" \\\n', 1)
s = s.replace("grep -vE 'rcon|RCON|Failed to fetch mob spawner entity'",
              "grep -vE 'rcon|RCON|Failed to fetch mob spawner entity|minecraftservices|Services are unavailable'")
open(dst, 'w').write(s)
PY
cd "$WORK"
echo "Smoke test in $WORK (server log: $WORK/smoke-server.log)"
bash "$WORK/smoke-test.sh" "$WORK/mods"
