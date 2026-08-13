#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
run_dir="$repo_dir/run-performance-stress"
reports_root="$repo_dir/build/reports/performance-stress"
profile=${MAG_STRESS_PROFILE:-standard}
timeout_seconds=${MAG_STRESS_TIMEOUT_SECONDS:-600}
shutdown_timeout_seconds=${MAG_STRESS_SHUTDOWN_TIMEOUT_SECONDS:-90}
keep_world=${MAG_STRESS_KEEP_WORLD:-0}
neoforge_version=${MAG_STRESS_NEOFORGE_VERSION:-21.1.241}
stability_cv_threshold_pct=${MAG_STRESS_CV_THRESHOLD_PCT:-10}
empty_drift_threshold_pct=${MAG_STRESS_DRIFT_THRESHOLD_PCT:-10}
absolute_noise_floor_mspt=${MAG_STRESS_ABSOLUTE_NOISE_FLOOR_MSPT:-0.1}
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
git_revision=$(git -C "$repo_dir" rev-parse --short=12 HEAD)
report_dir="$reports_root/${timestamp}-${git_revision}-${profile}"
server_pid=
console_fd=
completed=false

case "$profile" in
    quick)
        default_grid=4
        default_global_warmup=1000
        default_warmup=300
        default_sample_ticks=600
        default_samples=3
        ;;
    standard)
        default_grid=8
        default_global_warmup=2000
        default_warmup=400
        default_sample_ticks=1200
        default_samples=5
        ;;
    full)
        default_grid=16
        default_global_warmup=4000
        default_warmup=800
        default_sample_ticks=2400
        default_samples=7
        ;;
    *)
        echo "performance-stress: ERROR: MAG_STRESS_PROFILE must be quick, standard, or full" >&2
        exit 2
        ;;
esac

grid_size=${MAG_STRESS_GRID:-$default_grid}
global_warmup_ticks=${MAG_STRESS_GLOBAL_WARMUP_TICKS:-$default_global_warmup}
warmup_ticks=${MAG_STRESS_WARMUP_TICKS:-$default_warmup}
sample_ticks=${MAG_STRESS_SAMPLE_TICKS:-$default_sample_ticks}
samples_per_scenario=${MAG_STRESS_SAMPLES:-$default_samples}
default_scenarios='empty_start,block_item_control,idle_emitters,active_emitters,external_fields,railgun_emitters,air_separators,gas_volume,mixed_pack,empty_end'
scenario_csv=${MAG_STRESS_SCENARIOS:-$default_scenarios}
IFS=',' read -r -a scenarios <<<"$scenario_csv"

expected_mod_ids=(
    magnetization create sable aeronautics simulated terrablender
    create_new_age tfmg oreexcavation createoreexcavation
)

fail() {
    echo "performance-stress: ERROR: $*" >&2
    return 1
}

send_command() {
    printf '%s\n' "$1" >&"$console_fd"
}

stop_server() {
    if [[ -n ${server_pid:-} ]] && kill -0 "$server_pid" 2>/dev/null; then
        if [[ -n ${console_fd:-} ]]; then
            printf 'stop\n' >&"$console_fd" 2>/dev/null || true
        fi
        local deadline=$((SECONDS + shutdown_timeout_seconds))
        while kill -0 "$server_pid" 2>/dev/null && (( SECONDS < deadline )); do
            sleep 1
        done
        if kill -0 "$server_pid" 2>/dev/null; then
            kill -TERM -- "-$server_pid" 2>/dev/null || true
            sleep 2
        fi
    fi
    local game_pids
    game_pids=$(pgrep -f 'stressServerRunVmArgs[.]txt' || true)
    if [[ -n "$game_pids" ]]; then
        kill -TERM $game_pids 2>/dev/null || true
        sleep 2
        local game_pid
        for game_pid in $game_pids; do
            kill -0 "$game_pid" 2>/dev/null && kill -KILL "$game_pid" 2>/dev/null || true
        done
    fi
    if [[ -n ${console_fd:-} ]]; then
        eval "exec ${console_fd}>&-" || true
        console_fd=
    fi
    server_pid=
}

archive_runtime() {
    [[ -f "$run_dir/logs/latest.log" ]] && cp "$run_dir/logs/latest.log" "$report_dir/latest.log"
    [[ -f "$run_dir/world/datapacks/magnetization_stress/stress-manifest.json" ]] \
        && cp "$run_dir/world/datapacks/magnetization_stress/stress-manifest.json" "$report_dir/stress-manifest.json"
    if [[ -d "$run_dir/crash-reports" ]]; then
        mkdir -p "$report_dir/crash-reports"
        cp -a "$run_dir/crash-reports/." "$report_dir/crash-reports/"
    fi
}

cleanup() {
    local exit_code=$?
    stop_server
    archive_runtime
    if [[ "$keep_world" == 1 ]]; then
        echo "performance-stress: retained disposable world at $run_dir" >&2
    else
        rm -rf -- "$run_dir"
    fi
    if [[ "$completed" != true && -d "$report_dir" ]]; then
        printf 'exit_code=%s\n' "$exit_code" >"$report_dir/FAILED"
        echo "performance-stress: incomplete evidence retained at $report_dir" >&2
    fi
    exit "$exit_code"
}
trap cleanup EXIT INT TERM

if [[ "$run_dir" != "$repo_dir/run-performance-stress" || -z "$repo_dir" ]]; then
    fail "refusing to clean unresolved run directory: $run_dir"
fi
for value in "$grid_size" "$global_warmup_ticks" "$warmup_ticks" "$sample_ticks" "$samples_per_scenario" "$timeout_seconds" "$shutdown_timeout_seconds"; do
    [[ "$value" =~ ^[1-9][0-9]*$ ]] || fail "grid, tick counts, sample count, and timeouts must be positive integers"
done
(( grid_size <= 20 )) || fail 'MAG_STRESS_GRID cannot exceed 20'
[[ "$keep_world" == 0 || "$keep_world" == 1 ]] || fail 'MAG_STRESS_KEEP_WORLD must be 0 or 1'
(( ${#scenarios[@]} >= 2 )) || fail 'at least empty_start and empty_end scenarios are required'
[[ " ${scenarios[*]} " == *" empty_start "* && " ${scenarios[*]} " == *" empty_end "* ]] \
    || fail 'the scenario list must contain empty_start and empty_end'
for scenario in "${scenarios[@]}"; do
    [[ ",$default_scenarios," == *",$scenario,"* ]] || fail "unknown scenario: $scenario"
done
if [[ ! -f "$repo_dir/run/eula.txt" ]] || ! grep -Eq '^eula=true[[:space:]]*$' "$repo_dir/run/eula.txt"; then
    fail 'an accepted run/eula.txt is required; the harness will not accept the EULA for you'
fi

rm -rf -- "$run_dir"
mkdir -p "$run_dir/world/datapacks" "$report_dir"
cp "$repo_dir/run/eula.txt" "$run_dir/eula.txt"
cp /dev/null "$report_dir/gradle.log"
cat >"$run_dir/server.properties" <<'PROPERTIES'
allow-flight=true
enable-command-block=true
enable-rcon=false
enable-status=false
level-name=world
level-seed=8675309
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","features":false,"lakes":false}
max-tick-time=-1
motd=Magnetization disposable performance stress harness
online-mode=false
pause-when-empty-seconds=-1
simulation-distance=5
spawn-protection=0
sync-chunk-writes=false
view-distance=5
PROPERTIES

python3 "$repo_dir/scripts/generate-performance-stress-pack.py" \
    --output "$run_dir/world/datapacks/magnetization_stress" \
    --grid-size "$grid_size" >"$report_dir/generator-output.json"

python3 - "$report_dir/metadata.json" <<PY
import json
import platform
import subprocess
import sys
from pathlib import Path

destination = Path(sys.argv[1])
metadata = {
    "schema_version": 1,
    "started_utc": "$timestamp",
    "git_revision": "$git_revision",
    "git_dirty": bool(subprocess.run(["git", "-C", "$repo_dir", "status", "--porcelain"], capture_output=True, text=True, check=True).stdout),
    "profile": "$profile",
    "grid_size": $grid_size,
    "instances_per_grid": $((grid_size * grid_size)),
    "global_warmup_ticks": $global_warmup_ticks,
    "warmup_ticks": $warmup_ticks,
    "sample_ticks": $sample_ticks,
    "samples_per_scenario": $samples_per_scenario,
    "scenarios": "$scenario_csv".split(","),
    "neoforge_version": "$neoforge_version",
    "java_version": subprocess.run(["java", "-version"], capture_output=True, text=True).stderr.splitlines()[0],
    "os": platform.platform(),
    "machine": platform.machine(),
    "processor": platform.processor(),
    "cpu_count": __import__("os").cpu_count(),
    "stability_cv_threshold_pct": float("$stability_cv_threshold_pct"),
    "empty_drift_threshold_pct": float("$empty_drift_threshold_pct"),
    "absolute_noise_floor_mspt": float("$absolute_noise_floor_mspt"),
}
destination.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
PY

mkfifo "$run_dir/server-input.fifo"
exec {console_fd}<>"$run_dir/server-input.fifo"
setsid "$repo_dir/gradlew" --no-daemon "-Pneoforge_version=$neoforge_version" runStressServer \
    <"$run_dir/server-input.fifo" >"$report_dir/gradle.log" 2>&1 &
server_pid=$!
echo "performance-stress: starting $profile server (pid $server_pid); report $report_dir"

wait_for_log() {
    local pattern=$1
    local timeout=$2
    local deadline=$((SECONDS + timeout))
    while (( SECONDS < deadline )); do
        if [[ -f "$run_dir/logs/latest.log" ]] && grep -Eq "$pattern" "$run_dir/logs/latest.log"; then
            return 0
        fi
        if ! kill -0 "$server_pid" 2>/dev/null; then
            return 1
        fi
        sleep 1
    done
    return 1
}

wait_for_new_match() {
    local pattern=$1
    local previous=$2
    local timeout=$3
    local deadline=$((SECONDS + timeout))
    local count
    while (( SECONDS < deadline )); do
        count=$(grep -Ec "$pattern" "$run_dir/logs/latest.log" 2>/dev/null || true)
        if (( count > previous )); then
            return 0
        fi
        kill -0 "$server_pid" 2>/dev/null || return 1
        sleep 0.25
    done
    return 1
}

if ! wait_for_log 'Done \(' "$timeout_seconds"; then
    fail "server did not reach Done within ${timeout_seconds}s; see $report_dir/gradle.log"
fi
if grep -Eq 'Failed to load function magnetization_stress:' "$run_dir/logs/latest.log"; then
    fail "one or more generated stress functions were rejected; see $run_dir/logs/latest.log"
fi

missing_mods=()
for mod_id in "${expected_mod_ids[@]}"; do
    grep -Fq "($mod_id)" "$run_dir/logs/latest.log" || missing_mods+=("$mod_id")
done
(( ${#missing_mods[@]} == 0 )) || fail "dedicated server did not load expected benchmark mods: ${missing_mods[*]}"

send_command 'save-off'
send_command 'difficulty peaceful'
send_command 'tick rate 20'
echo "performance-stress: global JVM/server warmup ($global_warmup_ticks ticks)"
global_warmup_before=$(grep -c 'Sprint completed with' "$run_dir/logs/latest.log" 2>/dev/null || true)
send_command "tick sprint $global_warmup_ticks"
wait_for_new_match 'Sprint completed with' "$global_warmup_before" "$timeout_seconds" \
    || fail 'global JVM/server warmup did not finish'
printf 'scenario\tsample\tticks\ttps\tmspt\n' >"$report_dir/raw-samples.tsv"

run_sprint() {
    local scenario=$1
    local sample=$2
    local ticks=$3
    local before line parsed tps mspt
    before=$(grep -c 'Sprint completed with' "$run_dir/logs/latest.log" 2>/dev/null || true)
    send_command "tick sprint $ticks"
    if ! wait_for_new_match 'Sprint completed with' "$before" "$timeout_seconds"; then
        fail "$scenario sample $sample did not finish its $ticks-tick sprint"
    fi
    line=$(grep 'Sprint completed with' "$run_dir/logs/latest.log" | tail -n 1)
    parsed=$(sed -nE 's/.*Sprint completed with ([0-9.,]+) ticks per second, or ([0-9.,]+) ms per tick.*/\1\t\2/p' <<<"$line")
    [[ -n "$parsed" ]] || fail "could not parse tick sprint output: $line"
    IFS=$'\t' read -r tps mspt <<<"$parsed"
    printf '%s\t%s\t%s\t%s\t%s\n' "$scenario" "$sample" "$ticks" "$tps" "$mspt" >>"$report_dir/raw-samples.tsv"
    echo "performance-stress: $scenario sample $sample/$samples_per_scenario = ${mspt} MSPT"
}

for scenario in "${scenarios[@]}"; do
    marker="MAG_STRESS_READY_$scenario"
    before=$(grep -c "$marker" "$run_dir/logs/latest.log" 2>/dev/null || true)
    send_command "function magnetization_stress:$scenario"
    if ! wait_for_new_match "$marker" "$before" "$timeout_seconds"; then
        fail "$scenario setup did not finish"
    fi
    echo "performance-stress: $scenario warmup ($warmup_ticks ticks)"
    warmup_before=$(grep -c 'Sprint completed with' "$run_dir/logs/latest.log" 2>/dev/null || true)
    send_command "tick sprint $warmup_ticks"
    wait_for_new_match 'Sprint completed with' "$warmup_before" "$timeout_seconds" \
        || fail "$scenario warmup did not finish"
    for ((sample = 1; sample <= samples_per_scenario; sample++)); do
        run_sprint "$scenario" "$sample" "$sample_ticks"
    done
done

python3 "$repo_dir/scripts/analyze-performance-stress.py" \
    --samples "$report_dir/raw-samples.tsv" \
    --metadata "$report_dir/metadata.json" \
    --output "$report_dir"

completed=true
echo "performance-stress: completed; summary $report_dir/summary.md"
exit 0
