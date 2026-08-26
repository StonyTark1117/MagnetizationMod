#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
gradle="$repo_dir/gradlew"

run_profile() {
    local task=$1
    echo "optionalClientProfiles: starting $task"
    "$gradle" "$task" --no-daemon --max-workers=1 --console=plain
    echo "optionalClientProfiles: passed $task"
}

run_profile smokeClientJade
run_profile smokeClientTop
run_profile smokeClientRei
run_profile smokeClientEmi
run_profile smokeClientJer
run_profile smokeClientPonderCompat
run_profile smokeClientSimulatedCoastersTrackStyles
run_profile smokeClientCoastersMagnetized
run_profile smokeClientCoastersEngineered
run_profile smokeClientCoastersExtras
run_profile smokeClientSimulatedMissiles

echo 'optionalClientProfiles: all isolated clients passed'
