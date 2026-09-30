#!/usr/bin/env bash
set -Eeuo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_dir"
mkdir -p build/compat-audit/ui
exec 9>build/compat-audit/ui.lock
flock -n 9 || { echo 'UI audit already running' >&2; exit 2; }
port=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')
for role in server client; do
  path="run-ui-audit-$role"
  if [[ -L $path ]]; then rm -- "$path"; elif [[ -d $path ]]; then mv -- "$path" "$(mktemp -d /tmp/magnetization-ui-old.XXXXXX)/$role"; fi
  runtime=$(mktemp -d "/tmp/magnetization-ui-$role.XXXXXX")
  ln -s "$runtime" "$path"
done
cat > run-ui-audit-server/server.properties <<PROPS
online-mode=false
server-ip=127.0.0.1
server-port=$port
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
view-distance=4
simulation-distance=4
spawn-protection=0
PROPS
printf 'eula=true\n' > run-ui-audit-server/eula.txt
cat > run-ui-audit-client/options.txt <<'OPTIONS'
renderDistance:4
simulationDistance:5
maxFps:30
pauseOnLostFocus:false
onboardAccessibility:false
tutorialStep:none
hideServerAddress:true
soundCategory_master:0.0
guiScale:2
OPTIONS
display_number=$((950+$$%500))
while [[ -e /tmp/.X11-unix/X$display_number ]]; do display_number=$((display_number+1)); done
Xvfb ":$display_number" -screen 0 1440x900x24 -nolisten tcp > build/compat-audit/ui/xvfb.log 2>&1 &
xvfb=$!
server_runner=''; client_runner=''
cleanup() {
  for role in server client; do
    [[ ! -f run-ui-audit-$role/logs/latest.log ]] || cp "run-ui-audit-$role/logs/latest.log" "build/compat-audit/ui/$role.log"
  done
  [[ ! -d run-ui-audit-client/screenshots ]] || cp run-ui-audit-client/screenshots/ui-*.png build/compat-audit/ui/ 2>/dev/null || true
  for role in Server Client; do
    signature="$repo_dir/build/moddev/uiAudit${role}RunProgramArgs.txt"
    pids=$(pgrep -f "$signature" || true)
    [[ -z $pids ]] || kill $pids 2>/dev/null || true
  done
  for runner in "$server_runner" "$client_runner"; do
    [[ -z $runner ]] || kill -TERM -- "-$runner" 2>/dev/null || true
  done
  kill "$xvfb" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM
setsid ./gradlew runUiAuditServer --console=plain > build/compat-audit/ui/server-launch.log 2>&1 &
server_runner=$!
for ((i=0;i<150;i++)); do
  if rg -q 'Done \(' run-ui-audit-server/logs/latest.log 2>/dev/null; then break; fi
  kill -0 "$server_runner" 2>/dev/null || exit 1
  sleep 1
done
rg -q 'Done \(' run-ui-audit-server/logs/latest.log
setsid env DISPLAY=":$display_number" LIBGL_ALWAYS_SOFTWARE=1 ./gradlew runUiAuditClient -x compileJava -x processResources -PuiAuditEndpoint="127.0.0.1:$port" --console=plain > build/compat-audit/ui/client-launch.log 2>&1 &
client_runner=$!
for ((i=0;i<180;i++)); do
  if rg -q 'UI_AUDIT_(PASS|FAILED)' run-ui-audit-client/logs/latest.log 2>/dev/null; then break; fi
  kill -0 "$client_runner" 2>/dev/null || exit 1
  sleep 1
done
rg 'UI_AUDIT_|UI_CURIOS_|UI_EMI_|UI_CAPTURE' run-ui-audit-client/logs/latest.log
rg -q 'UI_AUDIT_PASS' run-ui-audit-client/logs/latest.log
! rg -q 'UI_AUDIT_FAILED' run-ui-audit-client/logs/latest.log
