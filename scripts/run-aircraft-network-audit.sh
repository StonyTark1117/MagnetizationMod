#!/usr/bin/env bash
set -uo pipefail
ordinary=false
if [[ ${1:-} == --ordinary ]]; then ordinary=true; shift; fi
if [[ $# -ne 0 ]]; then echo 'usage: run-aircraft-network-audit.sh [--ordinary]' >&2; exit 2; fi
proxy_pid=''
pilot_endpoint=127.0.0.1:25585
observer_endpoint=127.0.0.1:25585
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_dir"
mkdir -p build/compat-audit
exec 9>build/compat-audit/aircraft-network.lock
flock -n 9 || { echo 'An aircraft network audit is already running' >&2; exit 2; }
# As with the other Sable audits, keep disposable region/chunk I/O on tmpfs.
# Preserve prior evidence, including config, in the archived runtime.
if [[ -L run-aircraft-audit-server ]]; then
    rm -- run-aircraft-audit-server
elif [[ -d run-aircraft-audit-server ]]; then
    archive=$(mktemp -d /tmp/magnetization-aircraft-audit-old.XXXXXX)
    mv run-aircraft-audit-server "$archive/server"
fi
runtime=$(mktemp -d /tmp/magnetization-aircraft-audit-server.XXXXXX)
ln -s "$runtime" run-aircraft-audit-server
command -v Xvfb >/dev/null || exit 2
display_number=$((200 + $$ % 700))
while [[ -e /tmp/.X11-unix/X$display_number ]]; do display_number=$((display_number + 1)); done
display=":$display_number"
mkdir -p run-aircraft-audit-{server,pilot,observer}
cat > run-aircraft-audit-server/server.properties <<'PROPS'
online-mode=false
server-ip=127.0.0.1
server-port=25585
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
view-distance=5
simulation-distance=5
spawn-protection=0
allow-flight=true
PROPS
printf 'eula=true\n' > run-aircraft-audit-server/eula.txt
for role in pilot observer; do
cat > "run-aircraft-audit-$role/options.txt" <<'OPTIONS'
renderDistance:5
simulationDistance:5
maxFps:40
pauseOnLostFocus:false
onboardAccessibility:false
soundCategory_master:0.0
guiScale:2
OPTIONS
done
Xvfb "$display" -screen 0 1280x720x24 -nolisten tcp >/tmp/aircraft-xvfb.log 2>&1 &
xvfb=$!
for role in server pilot observer; do
  rm -f "run-aircraft-audit-$role/logs/latest.log"
done
cleanup() {
  for signature in aircraftAuditServer aircraftAuditPilot aircraftAuditObserver; do
    pids=$(pgrep -f "${repo_dir}/build/moddev/${signature}RunProgramArgs.txt" || true)
    [[ -z $pids ]] || kill $pids 2>/dev/null || true
    sleep 2
    [[ -z $pids ]] || kill -KILL $pids 2>/dev/null || true
  done
  [[ -z $proxy_pid ]] || kill "$proxy_pid" 2>/dev/null || true
  kill "$xvfb" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM
if $ordinary; then
  rm -f build/compat-audit/aircraft-latency.json
  python3 tools/aircraft_latency_proxy.py --output build/compat-audit/aircraft-latency.json > /tmp/aircraft-latency-proxy.log 2>&1 &
  proxy_pid=$!
  for ((i=0;i<50;i++)); do
    [[ -f build/compat-audit/aircraft-latency.json ]] && break
    kill -0 "$proxy_pid" 2>/dev/null || exit 1
    sleep 0.1
  done
  read -r pilot_port observer_port < <(python3 -c 'import json; print(*json.load(open("build/compat-audit/aircraft-latency.json"))["ports"])')
  [[ -n $pilot_port && -n $observer_port ]] || exit 1
  pilot_endpoint="127.0.0.1:$pilot_port"
  observer_endpoint="127.0.0.1:$observer_port"
fi
./gradlew runAircraftAuditServer -PaircraftOrdinary="$ordinary" --console=plain > /tmp/aircraft-server-launch.log 2>&1 &
server_runner=$!
for ((i=0;i<180;i++)); do
  if rg -q 'Done \(' run-aircraft-audit-server/logs/latest.log 2>/dev/null; then break; fi
  kill -0 "$server_runner" 2>/dev/null || exit 1
  sleep 1
done
rg -q 'Done \(' run-aircraft-audit-server/logs/latest.log || exit 1
env DISPLAY="$display" LIBGL_ALWAYS_SOFTWARE=1 ./gradlew runAircraftAuditPilot -x compileJava -x processResources -PaircraftOrdinary="$ordinary" -PaircraftAuditEndpoint="$pilot_endpoint" --console=plain > /tmp/aircraft-pilot-launch.log 2>&1 &
for ((i=0;i<180;i++)); do
  if rg -q 'AuditPilot joined the game' run-aircraft-audit-server/logs/latest.log; then break; fi
  if rg -q "/FATAL|\[Render thread/FATAL" run-aircraft-audit-pilot/logs/latest.log 2>/dev/null; then exit 1; fi
  sleep 1
done
rg -q 'AuditPilot joined the game' run-aircraft-audit-server/logs/latest.log || exit 1
env DISPLAY="$display" LIBGL_ALWAYS_SOFTWARE=1 ./gradlew runAircraftAuditObserver -x compileJava -x processResources -PaircraftOrdinary="$ordinary" -PaircraftAuditEndpoint="$observer_endpoint" --console=plain > /tmp/aircraft-observer-launch.log 2>&1 &
for ((i=0;i<700;i++)); do
  if rg -q 'AIRCRAFT_NETWORK_(SERVER_PASS|FAILED)' run-aircraft-audit-server/logs/latest.log; then sleep 3; break; fi
  if rg -q '/FATAL' run-aircraft-audit-observer/logs/latest.log 2>/dev/null; then exit 1; fi
  sleep 1
done
rg 'AIRCRAFT_(PASS|NETWORK_)' run-aircraft-audit-server/logs/latest.log
if $ordinary; then
  [[ $(rg -c 'UDP authentication complete' run-aircraft-audit-server/logs/latest.log) -ge 2 ]] || { echo 'Missing Sable UDP authentication through latency relay' >&2; exit 1; }
fi
python3 tools/analyze_aircraft_network.py run-aircraft-audit-server/logs/latest.log \
  run-aircraft-audit-pilot/logs/latest.log run-aircraft-audit-observer/logs/latest.log \
  --output build/compat-audit/aircraft-network-results.json
