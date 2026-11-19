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
# Ek eklentiler (AdvancedBan, EssentialsX ...) - bosluklu liste
for extra in ${EXTRA_JARS:-}; do
  cp "$extra" "$SERVER/plugins/"
done

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

# Eklenti ayarlari: e-posta testleri icin yerel sahte SMTP sunucusu
mkdir -p "$SERVER/plugins/KayitPlugin"
unzip -p "$JAR" config.yml > "$SERVER/plugins/KayitPlugin/config.yml"
python3 - "$SERVER/plugins/KayitPlugin/config.yml" <<'PY'
import re, sys
p = sys.argv[1]
s = open(p, encoding="utf-8").read()
rep = {
    "smtp-host": '"127.0.0.1"', "smtp-port": "2525", "security": "NONE",
    "username": '"ci@kayit.test"', "password": '"ci-password"', "test-on-startup": "true",
}
for k, v in rep.items():
    s = re.sub(r"(?m)^(  " + re.escape(k) + r":).*$", r"\1 " + v, s, count=1)
open(p, "w", encoding="utf-8").write(s)
PY
grep -nE "smtp-host|smtp-port|security:|username|password:" "$SERVER/plugins/KayitPlugin/config.yml" | head -8

MAILS="$SERVER/mails"
python3 "$ROOT/scripts/mail_sink.py" 2525 "$MAILS" > "$SERVER/mail_sink.log" 2>&1 &
SINK=$!
trap 'kill $SINK 2>/dev/null || true' EXIT
sleep 2

cd "$ROOT/scripts"
MAIL_DIR="$MAILS" node e2e.js "$SERVER" "$MC"
