#!/usr/bin/env bash
# clienttest.sh: runs the client game tests (versions/26.3/src/gametest) in a real, headless Minecraft client and
# lists the screenshots they took (versions/26.3/build/run/clientGameTest/screenshots). Needs Xvfb, Mesa (software
# OpenGL) and JDK 25. Locally: apt-get install xvfb mesa-utils mesa-vulkan-drivers openjdk-25-jdk-headless.
set -uo pipefail
cd "$(dirname "$0")/../../versions/26.3"
if [ -z "${DISPLAY:-}" ]; then
  export DISPLAY=:99
  Xvfb :99 -screen 0 1280x720x24 +extension GLX +render -noreset >/dev/null 2>&1 &
  XVFB=$!
  trap 'kill $XVFB 2>/dev/null' EXIT
  sleep 2
fi
export LIBGL_ALWAYS_SOFTWARE=1
export XDG_RUNTIME_DIR=${XDG_RUNTIME_DIR:-$(mktemp -d)}
[ -d /usr/lib/jvm/java-25-openjdk-amd64 ] && export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 PATH=/usr/lib/jvm/java-25-openjdk-amd64/bin:$PATH
./gradlew runClientGameTest --console=plain "$@"
status=$?
echo "---- screenshots"
find build/run/clientGameTest/screenshots -name '*.png' 2>/dev/null | sort
exit $status
