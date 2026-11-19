#!/usr/bin/env bash
# Gercek Paper sunucusu indirir, eklentiyi kurar ve uctan uca testi calistirir.
# Kullanim: scripts/smoke_test.sh <mc-surumu> <plugin.jar>
set -euo pipefail
MC="$1"
JAR="$2"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVER="$ROOT/server-$MC"
UA="KayitPlugin-CI/2.0 (https://github.com/jesthub25/Kay-t-Sistem)"

rm -rf "$SERVER"
mkdir -p "$SERVER/plugins"
cp "$JAR" "$SERVER/plugins/"

URL=""
URL=$(curl -fsSL -H "User-Agent: $UA" "https://fill.papermc.io/v3/projects/paper/versions/$MC/builds/latest" 2>/dev/null \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["downloads"]["server:default"]["url"])' 2>/dev/null || true)
if [ -z "$URL" ]; then
  BUILD=$(curl -fsSL -H "User-Agent: $UA" "https://api.papermc.io/v2/projects/paper/versions/$MC/builds" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["builds"][-1]["build"])')
  URL="https://api.papermc.io/v2/projects/paper/versions/$MC/builds/$BUILD/downloads/paper-$MC-$BUILD.jar"
fi
echo "Downloading Paper $MC: $URL"
curl -fsSL -H "User-Agent: $UA" -o "$SERVER/paper.jar" "$URL"

echo "eula=true" > "$SERVER/eula.txt"
cat > "$SERVER/server.properties" <<PROPS
online-mode=false
enforce-secure-profile=false
level-type=flat
generate-structures=false
spawn-protection=0
view-distance=3
simulation-distance=3
max-players=10
motd=KayitPlugin CI
server-port=25565
PROPS

cd "$ROOT/scripts"
node e2e.js "$SERVER" "$MC"
