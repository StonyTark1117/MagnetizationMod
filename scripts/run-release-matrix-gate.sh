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
run_profile 'Create: Coasters Simulated compatibility profile' smokeSimulatedCoastersGameTest
run_profile 'Create: Big Cannons compatibility profile' smokeCreateBigCannonsGameTest
run_profile 'Create: New Age compatibility profile' smokeCreateNewAgeGameTest
run_profile 'Create Crafts & Additions compatibility profile' smokeCreateAdditionGameTest
run_profile 'Create: Tracks compatibility profile' smokeCreateTracksGameTest
run_profile "Steam 'n' Rails compatibility profile" smokeSteamRailsGameTest

echo 'releaseMatrixGate: all isolated profiles passed'
