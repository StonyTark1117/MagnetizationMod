#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_dir"
mkdir -p build/compat-audit/storage-restart
exec 9>build/compat-audit/storage-restart.lock
flock -n 9 || { echo 'Storage restart audit is already running' >&2; exit 2; }
if [[ -d run-storage-restart-audit || -L run-storage-restart-audit ]]; then
  archive=$(mktemp -d /tmp/magnetization-storage-restart.XXXXXX)
  mv run-storage-restart-audit "$archive/runtime"
fi
runtime=$(mktemp -d /tmp/magnetization-storage-restart-runtime.XXXXXX)
ln -s "$runtime" run-storage-restart-audit
printf 'eula=true\n' > run-storage-restart-audit/eula.txt
port=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')
cat > run-storage-restart-audit/server.properties <<PROPS
online-mode=false
server-ip=127.0.0.1
server-port=$port
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
generate-structures=false
view-distance=3
simulation-distance=3
spawn-protection=0
PROPS
for phase in create verify; do
  timeout --kill-after=15s 240s ./gradlew runStorageRestartAuditServer "-PstorageAuditPhase=$phase" --console=plain > "build/compat-audit/storage-restart/$phase-launch.log" 2>&1
  cp run-storage-restart-audit/logs/latest.log "build/compat-audit/storage-restart/$phase.log"
  if rg -q 'STORAGE_RESTART_FAILED' "build/compat-audit/storage-restart/$phase.log"; then
    rg -A15 'STORAGE_RESTART_FAILED' "build/compat-audit/storage-restart/$phase.log"; exit 1
  fi
  rg 'All dimensions are saved' "build/compat-audit/storage-restart/$phase.log"
  if [[ $phase == create ]]; then
    rg 'STORAGE_RESTART_CREATED' "build/compat-audit/storage-restart/$phase.log"
  else
    rg 'STORAGE_RESTART_(RESTORED|PASS)' "build/compat-audit/storage-restart/$phase.log"
  fi
done
