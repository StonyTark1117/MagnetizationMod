#!/usr/bin/env bash
set -Eeuo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_dir"
output=build/validation-audit
mkdir -p "$output/control"
exec 9>"$output/audit.lock"
flock -n 9 || { echo 'Lifecycle/presentation audit already running' >&2; exit 2; }
for role in server client; do
  path="run-validation-audit-$role"
  if [[ -L $path ]]; then rm -- "$path"; elif [[ -d $path ]]; then mv -- "$path" "$(mktemp -d "$repo_dir/$output/old.XXXXXX")/$role"; fi
  ln -s "$(mktemp -d "$repo_dir/$output/runtime-$role.XXXXXX")" "$path"
done
port=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')
cat > run-validation-audit-server/server.properties <<PROPS
online-mode=false
server-ip=127.0.0.1
server-port=$port
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
view-distance=6
simulation-distance=6
spawn-protection=0
sync-chunk-writes=false
PROPS
printf 'eula=true\n' > run-validation-audit-server/eula.txt
cat > run-validation-audit-client/options.txt <<'OPTIONS'
renderDistance:6
simulationDistance:6
maxFps:30
pauseOnLostFocus:false
onboardAccessibility:false
tutorialStep:none
soundCategory_master:0.0
guiScale:2
fov:0.0
OPTIONS
display_number=$((1450+$$%500))
while [[ -e /tmp/.X11-unix/X$display_number ]]; do display_number=$((display_number+1)); done
Xvfb ":$display_number" -screen 0 1440x900x24 -nolisten tcp > "$output/xvfb.log" 2>&1 &
xvfb=$!
server_runner=''; client_runner=''
stop_client() {
  pids=$(pgrep -f "$repo_dir/build/moddev/validationAuditClientRunProgramArgs.txt" || true)
  [[ -z $pids ]] || kill $pids 2>/dev/null || true
  [[ -z $client_runner ]] || kill -TERM -- "-$client_runner" 2>/dev/null || true
  [[ -z $client_runner ]] || wait "$client_runner" 2>/dev/null || true
  client_runner=''
}
cleanup() {
  stop_client
  pids=$(pgrep -f "$repo_dir/build/moddev/validationAuditServerRunProgramArgs.txt" || true)
  [[ -z $pids ]] || kill $pids 2>/dev/null || true
  [[ -z $server_runner ]] || kill -TERM -- "-$server_runner" 2>/dev/null || true
  kill "$xvfb" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM
set_config() {
  python3 - "$1" "$2" <<'PY'
from pathlib import Path
import re,sys
for role in ['server','client']:
 p=Path('run-validation-audit-'+role+'/config/magnetization-common.toml')
 if p.exists():
  t=p.read_text(); t,n=re.subn(r'(?m)^(\s*'+sys.argv[1]+r' = )(true|false)',lambda m:m[1]+sys.argv[2],t)
  if not n: raise SystemExit('Missing config key: '+sys.argv[1])
  p.write_text(t)
 else:
  p.parent.mkdir(parents=True,exist_ok=True);p.write_text('[compat]\n'+sys.argv[1]+' = '+sys.argv[2]+'\n')
PY
}
rm -f "$output/control/"{book-pages.jsonl,book-sweep-done}
for phase in initial disabled reenabled repeat gift-off; do
  mkdir -p "$output/$phase"
  rm -f "$output/control/"{server-ready,client-done,view-ready,view-request,package-off,package-on,client-package-off,client-package-on}
  user=Validation
  case $phase in
    disabled) set_config patchouliCompatEnabled false; user=DeferredAudit ;;
    reenabled) set_config patchouliCompatEnabled true; user=DeferredAudit ;;
    gift-off) set_config fieldManualAutoGive false; user=NoGiftValidation ;;
  esac
  [[ ${#user} -le 16 ]] || { echo "Invalid Minecraft test username: $user"; exit 1; }
  setsid ./gradlew runValidationAuditServer "-PvalidationPhase=$phase" --console=plain > "$output/$phase/server-launch.log" 2>&1 &
  server_runner=$!
  ready=false
  for ((i=0;i<180;i++)); do
    if rg -q 'Done \(' run-validation-audit-server/logs/latest.log 2>/dev/null; then ready=true; break; fi
    kill -0 "$server_runner" 2>/dev/null || { cat "$output/$phase/server-launch.log"; exit 1; }
    sleep 1
  done
  [[ $ready == true ]] || { echo 'Server startup timeout'; exit 1; }
  setsid env DISPLAY=":$display_number" LIBGL_ALWAYS_SOFTWARE=1 ./gradlew runValidationAuditClient -x compileJava -x processResources \
    "-PvalidationPhase=$phase" "-PvalidationUser=$user" "-PvalidationEndpoint=127.0.0.1:$port" --console=plain > "$output/$phase/client-launch.log" 2>&1 &
  client_runner=$!
  ready=false
  for ((i=0;i<1500;i++)); do
    if [[ ! -s $output/$phase/runtime-classpath.txt ]]; then
      python3 - "$repo_dir" "$output/$phase/runtime-classpath.txt" <<'PY'
from pathlib import Path
import os,sys
signature=(sys.argv[1]+'/build/moddev/validationAuditClientRunProgramArgs.txt').encode()
for process in Path('/proc').glob('[0-9]*/cmdline'):
 try:
  raw=process.read_bytes()
  if signature not in raw: continue
  argv=raw.decode().split('\0')
  for flag in ['-cp','-classpath','--class-path']:
   if flag in argv:
    Path(sys.argv[2]).write_text('\n'.join(argv[argv.index(flag)+1].split(os.pathsep))+'\n')
    raise SystemExit(0)
 except (OSError,UnicodeError): continue
PY
    fi
    if rg -q 'VALIDATION_(CLIENT|SERVER)_FAILED' run-validation-audit-{client,server}/logs/latest.log 2>/dev/null; then
      rg -A22 'VALIDATION_(CLIENT|SERVER)_FAILED' run-validation-audit-{client,server}/logs/latest.log; exit 1
    fi
    if rg -q "VALIDATION_SERVER_PASS phase=$phase" run-validation-audit-server/logs/latest.log 2>/dev/null; then ready=true; break; fi
    kill -0 "$server_runner" 2>/dev/null || { cat "$output/$phase/server-launch.log"; exit 1; }
    kill -0 "$client_runner" 2>/dev/null || { cat "$output/$phase/client-launch.log"; exit 1; }
    if rg -q 'Crash report saved|Mod loading has failed|Mod loading error has occurred' "$output/$phase/client-launch.log"; then
      tail -80 "$output/$phase/client-launch.log"; exit 1
    fi
    sleep 1
  done
  [[ $ready == true ]] || { echo "Audit timeout: $phase"; exit 1; }
  for ((i=0;i<300;i++)); do
    kill -0 "$server_runner" 2>/dev/null || break
    sleep 1
  done
  kill -0 "$server_runner" 2>/dev/null && { echo "Server shutdown timeout: $phase"; exit 1; }
  wait "$server_runner"; server_runner=''
  cp run-validation-audit-server/logs/latest.log "$output/$phase/server.log"
  cp run-validation-audit-client/logs/latest.log "$output/$phase/client.log"
  stop_client
  mkdir -p "$output/$phase/screenshots"
  cp run-validation-audit-client/screenshots/validation-*.png "$output/$phase/screenshots/"
  rg 'VALIDATION_(LOGIN|RECIPES|NATIVE_CRAFT|SERVER|CLIENT|PONDER|STYLE|POWER_VISUAL|MANUAL|CONFIG)' "$output/$phase/"{server,client}.log
  rg -q 'All dimensions are saved' "$output/$phase/server.log"
  # Previous logs must never be accepted as the next phase's startup markers.
  rm -f run-validation-audit-{server,client}/logs/latest.log
  rm -f run-validation-audit-client/screenshots/validation-*.png
 done
