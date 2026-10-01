#!/usr/bin/env bash
set -Eeuo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_dir"
output="$repo_dir/build/ae-meteorite-audit"
mkdir -p "$output"
exec 9>"$output/audit.lock"
flock -n 9 || { echo 'AE2 meteorite runtime audit already running' >&2; exit 2; }
# Other audits may be compiling or editing this checkout. Capture the current
# tracked diff and untracked sources into an independent build directory.
snapshot=$(mktemp -d /tmp/magnetization-ae-meteorite-validation.XXXXXX)
git archive HEAD | tar -x -C "$snapshot"
git diff HEAD --binary > "$output/source.patch"
git -C "$snapshot" apply "$output/source.patch"
python3 - "$repo_dir" "$snapshot" <<'PY'
from pathlib import Path
import shutil,subprocess,sys
root=Path(sys.argv[1]); target=Path(sys.argv[2])
for raw in subprocess.check_output(['git','ls-files','--others','--exclude-standard','-z'],cwd=root).split(b'\0'):
 if not raw: continue
 path=Path(raw.decode())
 if path.parts[0] not in ('src','scripts'): continue
 dest=target/path; dest.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(root/path,dest)
PY
printf '%s\n' "$snapshot" > "$output/snapshot-path.txt"
git rev-parse HEAD > "$output/base-commit.txt"
sha256sum src/main/java/com/stonytark/magnetization/{client/CompassPropertyHooks.java,network/CosmicCompassTargetPayload.java,content/meteorite/MeteoriteFieldRegistry.java,content/meteorite/AeMeteoriteScanner.java,gametest/AeMeteoriteRuntimeAudit.java,client/AeMeteoriteAuditClient.java} > "$output/source-sha256.txt"
cd "$snapshot"
mkdir -p build/ae-meteorite-audit/control
control="$snapshot/build/ae-meteorite-audit/control"
runtime=$(mktemp -d /dev/shm/magnetization-ae-meteorite.XXXXXX)
printf '%s\n' "$runtime" > "$output/runtime-path.txt"
mkdir -p "$runtime/server" "$runtime/client"
ln -s "$runtime/server" run-ae-meteorite-audit-server
ln -s "$runtime/client" run-ae-meteorite-audit-client
port=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')
cat > "$runtime/server/server.properties" <<PROPS
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
printf 'eula=true\n' > "$runtime/server/eula.txt"
cat > "$runtime/client/options.txt" <<'OPTIONS'
renderDistance:6
simulationDistance:6
maxFps:30
pauseOnLostFocus:false
onboardAccessibility:false
tutorialStep:none
soundCategory_master:0.0
guiScale:2
OPTIONS
Xvfb -displayfd 4 -screen 0 1280x720x24 -nolisten tcp -noreset 4>"$output/display" >"$output/xvfb.log" 2>&1 &
xvfb=$!; server_runner=''; client_runner=''
stop_client() {
  pids=$(pgrep -f "$snapshot/build/moddev/aeMeteoriteAuditClientRunProgramArgs.txt" || true)
  [[ -z $pids ]] || kill $pids 2>/dev/null || true
  [[ -z $client_runner ]] || kill -TERM -- "-$client_runner" 2>/dev/null || true
  [[ -z $client_runner ]] || wait "$client_runner" 2>/dev/null || true
  client_runner=''
}
cleanup() {
  stop_client
  pids=$(pgrep -f "$snapshot/build/moddev/aeMeteoriteAuditServerRunProgramArgs.txt" || true)
  [[ -z $pids ]] || kill $pids 2>/dev/null || true
  [[ -z $server_runner ]] || kill -TERM -- "-$server_runner" 2>/dev/null || true
  kill "$xvfb" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM
for ((i=0;i<30;i++)); do [[ ! -s $output/display ]] || break; sleep 1; done
[[ -s $output/display ]] || { echo 'Private X server failed to start'; exit 1; }
display_number=$(cat "$output/display")
for phase in create verify; do
  mkdir -p "$output/$phase/screenshots"
  rm -f "$control/stage" "$control/server-done-$phase" "$runtime/server/logs/latest.log" "$runtime/client/logs/latest.log"
  setsid env JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew runAeMeteoriteAuditServer "-PaeMeteoritePhase=$phase" --no-daemon --max-workers=1 --console=plain > "$output/$phase/server-launch.log" 2>&1 9>&- &
  server_runner=$!
  ready=false
  for ((i=0;i<240;i++)); do
    if rg -q 'Done \(' "$runtime/server/logs/latest.log" 2>/dev/null; then ready=true; break; fi
    kill -0 "$server_runner" 2>/dev/null || { tail -80 "$output/$phase/server-launch.log"; exit 1; }
    sleep 1
  done
  [[ $ready == true ]] || { echo 'AE2 audit server startup timeout'; exit 1; }
  setsid env JAVA_HOME=/usr/lib/jvm/java-21-openjdk DISPLAY=":$display_number" LIBGL_ALWAYS_SOFTWARE=1 ./gradlew runAeMeteoriteAuditClient "-PaeMeteoritePhase=$phase" "-PaeMeteoriteEndpoint=127.0.0.1:$port" -x compileJava -x processResources --no-daemon --max-workers=1 --console=plain > "$output/$phase/client-launch.log" 2>&1 9>&- &
  client_runner=$!
  ready=false
  for ((i=0;i<600;i++)); do
    if rg -q 'AE_AUDIT_(SERVER|CLIENT)_FAILED' "$runtime/"{server,client}/logs/latest.log 2>/dev/null; then
      rg -A20 'AE_AUDIT_(SERVER|CLIENT)_FAILED' "$runtime/"{server,client}/logs/latest.log; exit 1
    fi
    if [[ -f $control/server-done-$phase ]] && ! kill -0 "$server_runner" 2>/dev/null; then ready=true; break; fi
    kill -0 "$server_runner" 2>/dev/null || { tail -80 "$output/$phase/server-launch.log"; exit 1; }
    kill -0 "$client_runner" 2>/dev/null || { tail -80 "$output/$phase/client-launch.log"; exit 1; }
    sleep 1
  done
  [[ $ready == true ]] || { echo "AE2 audit phase timeout: $phase"; exit 1; }
  wait "$server_runner"; server_runner=''
  cp "$runtime/server/logs/latest.log" "$output/$phase/server.log"
  cp "$runtime/client/logs/latest.log" "$output/$phase/client.log"
  stop_client
  cp "$runtime/client/screenshots/ae-compass-$phase-"*.png "$output/$phase/screenshots/"
  rg 'All dimensions are saved|AE_AUDIT_(SERVER_PASS|RESTART_PASS|FORCE_PASS)' "$output/$phase/server.log"
  rg 'AE_AUDIT_CLIENT_PASS' "$output/$phase/client.log"
  rg -q 'All dimensions are saved' "$output/$phase/server.log"
  mkdir -p "$output/$phase/saved-data"
  cp "$runtime/server/world/data/magnetization_meteorite_fields.dat" "$output/$phase/saved-data/"
  cp "$runtime/server/world/level.dat" "$output/$phase/saved-data/"
done
cp "$control/manifest.properties" "$output/manifest.properties"
python3 "$repo_dir/scripts/analyze-ae-meteorite-runtime-audit.py" "$output"
printf 'pass\n' > "$output/result"
cleanup
trap - EXIT
python3 - "$runtime" "$snapshot" <<'PY'
from pathlib import Path
import os,re,shutil,sys
for value,parent,prefix in [(sys.argv[1],Path('/dev/shm'),'magnetization-ae-meteorite.'),
                            (sys.argv[2],Path('/tmp'),'magnetization-ae-meteorite-validation.')]:
 path=Path(value)
 if path.parent!=parent or not re.fullmatch(re.escape(prefix)+r'[A-Za-z0-9]{6}',path.name):
  raise SystemExit('Refusing to clean unexpected audit directory: '+value)
 if path.is_symlink() or path.stat().st_uid!=os.getuid():
  raise SystemExit('Refusing to clean a symlink or another owner’s directory: '+value)
 shutil.rmtree(path)
PY
echo "AE2 runtime audit passed; evidence: $output"
