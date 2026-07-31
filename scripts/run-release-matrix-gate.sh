#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
gradle="$repo_dir/gradlew"
smoke_seconds=${MAG_SMOKE_SECONDS:-210}

run_profile() {
    local label=$1
    shift
    echo "releaseMatrixGate: starting $label"
    "$gradle" --no-daemon "$@"
    echo "releaseMatrixGate: passed $label"
}

run_profile 'minimal release profile' releaseGate "-PmagSmokeSeconds=$smoke_seconds"
run_profile 'AeroPortals compatibility profile' smokeAeroPortalsGameTest
run_profile 'Immersive Aeronautics compatibility profile' smokeImmersiveAeronauticsGameTest

echo 'releaseMatrixGate: all isolated profiles passed'
