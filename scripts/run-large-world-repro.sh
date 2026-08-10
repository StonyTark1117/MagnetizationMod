#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
run_dir="$repo_dir/run-large-world-repro"
reports_root="$repo_dir/build/reports/large-world-repro"
radius=${MAG_REPRO_RADIUS:-800}
target_chunks=${MAG_REPRO_TARGET_CHUNKS:-10000}
timeout_seconds=${MAG_REPRO_TIMEOUT_SECONDS:-3600}
observe_seconds=${MAG_REPRO_OBSERVE_SECONDS:-180}
shutdown_timeout_seconds=${MAG_REPRO_SHUTDOWN_TIMEOUT_SECONDS:-7200}
neoforge_version=${MAG_REPRO_NEOFORGE_VERSION:-21.1.241}
keep_world=${MAG_REPRO_KEEP_WORLD:-0}
reload_fields_enabled=${MAG_REPRO_CREATE_NEW_AGE_FIELDS:-true}
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
report_dir="$reports_root/$timestamp"
summary_file="$report_dir/summary.txt"
server_pid=
console_fd=

expected_mod_ids=(
    magnetization create sable aeronautics simulated terrablender
    aeroportals immersive_portals_core simulatedcoasters createbigcannons
    create_new_age createaddition immersiveengineering alexscaves tracks
    railways createdieselgenerators copycats theoneprobe tfmg curios patchouli
    create_enchantment_industry createendertransmission chunky mr_chunky_offline
    lithium modernfix
)

fail() {
    echo "large-world-repro: ERROR: $*" >&2
    return 1
}

stop_server() {
    if [[ -n ${server_pid:-} ]] && kill -0 "$server_pid" 2>/dev/null; then
        if [[ -n ${console_fd:-} ]]; then
            printf 'stop\n' >&"$console_fd" 2>/dev/null || true
        fi
        local deadline=$((SECONDS + 60))
        while kill -0 "$server_pid" 2>/dev/null && (( SECONDS < deadline )); do
            sleep 1
        done
        if kill -0 "$server_pid" 2>/dev/null; then
            kill -TERM -- "-$server_pid" 2>/dev/null || true
            sleep 2
        fi
    fi
    # Gradle's single-use daemon starts the game JVM in its own process group.
    # If the console path itself failed, terminate only this run profile's exact
    # VM-args process so cleanup cannot leave a background server behind.
    local game_pids
    game_pids=$(pgrep -f 'largeWorld(Bootstrap|Fixture|Repro)ServerRunVmArgs[.]txt' || true)
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

archive_runtime_files() {
    local phase=${1:-final}
    [[ -f "$run_dir/logs/latest.log" ]] && cp "$run_dir/logs/latest.log" "$report_dir/$phase-latest.log"
    if [[ -d "$run_dir/crash-reports" ]]; then
        mkdir -p "$report_dir/$phase-crash-reports"
        cp -a "$run_dir/crash-reports/." "$report_dir/$phase-crash-reports/"
    fi
}

cleanup() {
    local exit_code=$?
    stop_server
    archive_runtime_files interrupted
    if [[ "$keep_world" == 1 ]]; then
        echo "large-world-repro: retained disposable world at $run_dir" >&2
    else
        rm -rf -- "$run_dir"
    fi
    exit "$exit_code"
}
trap cleanup EXIT INT TERM

if [[ "$run_dir" != "$repo_dir/run-large-world-repro" || -z "$repo_dir" ]]; then
    fail "refusing to clean unresolved run directory: $run_dir"
fi
if [[ ! "$radius" =~ ^[1-9][0-9]*$ || ! "$target_chunks" =~ ^[1-9][0-9]*$ \
        || ! "$timeout_seconds" =~ ^[1-9][0-9]*$ || ! "$observe_seconds" =~ ^[1-9][0-9]*$ \
        || ! "$shutdown_timeout_seconds" =~ ^[1-9][0-9]*$ ]]; then
    fail 'radius, target chunk count, and timeout values must be positive integers'
fi
if [[ "$reload_fields_enabled" != true && "$reload_fields_enabled" != false ]]; then
    fail 'MAG_REPRO_CREATE_NEW_AGE_FIELDS must be true or false'
fi
if [[ ! -f "$repo_dir/run/eula.txt" ]] || ! grep -Eq '^eula=true[[:space:]]*$' "$repo_dir/run/eula.txt"; then
    fail 'an accepted run/eula.txt is required; the harness will not accept the EULA for you'
fi

rm -rf -- "$run_dir"
mkdir -p "$run_dir" "$report_dir"
cp "$repo_dir/run/eula.txt" "$run_dir/eula.txt"
cat >"$run_dir/server.properties" <<'PROPERTIES'
allow-flight=true
enable-command-block=true
level-name=world
max-tick-time=-1
motd=Magnetization large-world issue reproducer
online-mode=false
simulation-distance=5
sync-chunk-writes=true
view-distance=5
PROPERTIES

cat >"$summary_file" <<SUMMARY
Magnetization GitHub issue #7 large-world reproduction
started_utc=$timestamp
neoforge=$neoforge_version
chunky_radius_blocks=$radius
target_chunks=$target_chunks
shutdown_timeout_seconds=$shutdown_timeout_seconds
generation_createNewAgeFieldsEnabled=false
reload_createNewAgeFieldsEnabled=$reload_fields_enabled
generated_world_cleanup=$([[ "$keep_world" == 1 ]] && echo retained || echo automatic)
SUMMARY

start_server() {
    local phase=$1
    local gradle_task=$2
    rm -f -- "$run_dir/server-input.fifo"
    # Every readiness and watchdog decision must come from this process. Logs
    # and crash reports have already been archived by the preceding phase.
    rm -f -- "$run_dir/logs/latest.log"
    rm -rf -- "$run_dir/crash-reports"
    mkfifo "$run_dir/server-input.fifo"
    exec {console_fd}<>"$run_dir/server-input.fifo"
    setsid "$repo_dir/gradlew" --no-daemon \
        "-Pneoforge_version=$neoforge_version" "$gradle_task" \
        <"$run_dir/server-input.fifo" >"$report_dir/$phase-gradle.log" 2>&1 &
    server_pid=$!
    echo "large-world-repro: started $phase server (pid $server_pid)"
}

send_command() {
    printf '%s\n' "$1" >&"$console_fd"
}

finish_server() {
    local shutdown_timeout=${1:-45}
    send_command stop
    local deadline=$((SECONDS + shutdown_timeout))
    while kill -0 "$server_pid" 2>/dev/null && (( SECONDS < deadline )); do
        sleep 1
    done
    if kill -0 "$server_pid" 2>/dev/null; then
        echo "large-world-repro: server shutdown exceeded ${shutdown_timeout}s; terminating this profile after its save pass" >&2
        local game_pids
        game_pids=$(pgrep -f 'largeWorld(Bootstrap|Fixture|Repro)ServerRunVmArgs[.]txt' || true)
        [[ -z "$game_pids" ]] || kill -TERM $game_pids 2>/dev/null || true
        sleep 2
        [[ -z "$game_pids" ]] || kill -KILL $game_pids 2>/dev/null || true
        kill -TERM "$server_pid" 2>/dev/null || true
    fi
    wait "$server_pid" 2>/dev/null || true
    server_pid=
    eval "exec ${console_fd}>&-"
    console_fd=
}

wait_for_log() {
    local pattern=$1
    local timeout=$2
    local deadline=$((SECONDS + timeout))
    while (( SECONDS < deadline )); do
        if [[ -f "$run_dir/logs/latest.log" ]] && grep -Eq "$pattern" "$run_dir/logs/latest.log"; then
            return 0
        fi
        if [[ -n ${server_pid:-} ]] && ! kill -0 "$server_pid" 2>/dev/null; then
            return 1
        fi
        sleep 2
    done
    return 1
}

set_compat_booleans() {
    local cna_fields=$1
    local all_other_booleans=${2:-true}
    local config="$run_dir/config/magnetization-common.toml"
    [[ -f "$config" ]] || fail "missing generated config: $config"
    local temp="$config.tmp"
    awk '
        /^\[compat\][[:space:]]*$/ { in_compat=1; print; next }
        /^\[/ { in_compat=0 }
        in_compat && /^[[:space:]]*[A-Za-z0-9_]+[[:space:]]*=[[:space:]]*(true|false)[[:space:]]*$/ {
            sub(/=[[:space:]]*(true|false)[[:space:]]*$/, "= " replacement)
        }
        { print }
    ' replacement="$all_other_booleans" "$config" >"$temp"
    mv "$temp" "$config"
    sed -E -i "s/^([[:space:]]*createNewAgeFieldsEnabled[[:space:]]*=[[:space:]]*).*/\\1$cna_fields/" "$config"
}

verify_compat_booleans() {
    local phase=$1
    local expected_cna=$2
    local expected_other=$3
    local config="$run_dir/config/magnetization-common.toml"
    local entries=()
    local mismatches=()
    local key value expected
    while IFS='=' read -r key value; do
        [[ -n "$key" ]] || continue
        entries+=("$key=$value")
        expected=$expected_other
        [[ "$key" != createNewAgeFieldsEnabled ]] || expected=$expected_cna
        [[ "$value" == "$expected" ]] || mismatches+=("$key=$value (expected $expected)")
    done < <(awk '
        /^\[compat\][[:space:]]*$/ { in_compat=1; next }
        in_compat && /^\[/ { exit }
        in_compat {
            line=$0
            gsub(/[[:space:]]/, "", line)
            split(line, pair, "=")
            if (pair[1] ~ /^[A-Za-z0-9_]+$/ && (pair[2] == "true" || pair[2] == "false")) {
                print pair[1] "=" pair[2]
            }
        }
    ' "$config")
    (( ${#entries[@]} > 0 )) || fail "no [compat] booleans found for $phase verification"
    printf '%s_compat_boolean_count=%s\n' "$phase" "${#entries[@]}" >>"$summary_file"
    printf '%s_compat_booleans=%s\n' "$phase" "${entries[*]}" >>"$summary_file"
    if (( ${#mismatches[@]} > 0 )); then
        fail "$phase compatibility options were not set as requested: ${mismatches[*]}"
    fi
}

count_chunks() {
    local region_dir="$run_dir/world/region"
    if [[ ! -d "$region_dir" ]]; then
        echo 0
        return
    fi
    find "$region_dir" -type f -name '*.mca' -print0 | while IFS= read -r -d '' region; do
        dd if="$region" bs=4096 count=1 status=none
    done | od -An -tu4 | awk '{ for (i=1; i<=NF; i++) if ($i != 0) count++ } END { print count+0 }'
}

has_watchdog_signature() {
    local log="$run_dir/logs/latest.log"
    [[ -f "$log" ]] && grep -Eq 'ServerHangWatchdog|A single server tick took|ExternalEmitterTracker|FieldApplicator.*SubLevelInclusive' "$log"
}

verify_mods() {
    local phase=${1:-reload}
    local log="$run_dir/logs/latest.log"
    local missing=()
    local mod_id
    for mod_id in "${expected_mod_ids[@]}"; do
        grep -Fq "($mod_id)" "$log" || missing+=("$mod_id")
    done
    if (( ${#missing[@]} > 0 )); then
        printf '%s_missing_mod_ids=%s\n' "$phase" "${missing[*]}" >>"$summary_file"
        fail "dedicated server did not load expected mods: ${missing[*]}"
    fi
    printf '%s_verified_mod_ids=%s\n' "$phase" "${expected_mod_ids[*]}" >>"$summary_file"
}

# Bootstrap once so NeoForge writes the authoritative config schema. Its world
# is deliberately discarded before the measured generation begins.
start_server bootstrap runLargeWorldBootstrapServer
if ! wait_for_log 'Done \(' 300; then
    archive_runtime_files bootstrap-failed
    fail 'bootstrap server did not reach Done'
fi
finish_server
archive_runtime_files bootstrap
set_compat_booleans false false
verify_compat_booleans generation false false
rm -rf -- "$run_dir/world"

# Generate a genuinely fresh 10,000-chunk fixture with the complete mod stack
# present but compatibility work disabled. Keeping CNA installed here is
# essential because its high-density magnetite feature supplies the emitters
# that the all-enabled reload must index.
start_server generation runLargeWorldFixtureServer
if ! wait_for_log 'Done \(' 300; then
    archive_runtime_files generation-failed
    fail 'generation server did not reach Done'
fi
verify_mods generation
send_command "function chunky_offline:config/set {\"radius\":$radius,\"x\":0,\"z\":0}"
# Chunky Offline requests cancellation of its auto-started default task before
# applying the new radius. Confirm that cancellation, then start the selected
# task; otherwise Chunky retains and resumes the 10,000-block default task.
sleep 2
send_command 'chunky confirm'
sleep 2
send_command 'chunky start'

deadline=$((SECONDS + timeout_seconds))
last_progress=$SECONDS
generated=0
generation_reproduced=false
while (( SECONDS < deadline )); do
    if has_watchdog_signature; then
        generation_reproduced=true
        break
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
        has_watchdog_signature && generation_reproduced=true
        break
    fi
    if (( SECONDS - last_progress >= 15 )); then
        generated=$(count_chunks)
        echo "large-world-repro: generated $generated / $target_chunks chunks"
        send_command 'chunky progress'
        last_progress=$SECONDS
        if grep -Eq 'Task finished|Generation complete|generation completed' "$run_dir/logs/latest.log"; then
            break
        fi
    fi
    sleep 2
done
printf 'watchdog_during_generation=%s\n' "$generation_reproduced" >>"$summary_file"
archive_runtime_files generation

if [[ "$generation_reproduced" == true ]]; then
    printf 'result=CONTROL_GENERATION_WATCHDOG\n' >>"$summary_file"
    fail 'watchdog occurred while createNewAgeFieldsEnabled=false; the 10,000-chunk control fixture could not be completed'
fi
finish_server "$shutdown_timeout_seconds"
generated=$(count_chunks)
printf 'generated_chunks=%s\n' "$generated" >>"$summary_file"
if (( generated < target_chunks )); then
    fail "only $generated generated chunks were durable after shutdown; expected at least $target_chunks"
fi

# The measured server load has every compatibility boolean enabled by default.
# Setting MAG_REPRO_CREATE_NEW_AGE_FIELDS=false provides the matching control.
set_compat_booleans "$reload_fields_enabled" true
verify_compat_booleans reload "$reload_fields_enabled" true
sed -E -i 's/^max-tick-time=.*/max-tick-time=60000/' "$run_dir/server.properties"

# Load the complete fixture on a new dedicated-server process, then ask Chunky
# Offline to revisit the same area while the full compat bridge is enabled.
start_server reload runLargeWorldReproServer
if ! wait_for_log 'Done \(' 300; then
    archive_runtime_files reload-failed
    if has_watchdog_signature; then
        verify_mods reload
        printf 'watchdog_during_reload=true\nresult=REPRODUCED_DURING_RELOAD_STARTUP\n' >>"$summary_file"
        echo "large-world-repro: reproduced watchdog while loading the pregenerated world; evidence: $report_dir"
        exit 0
    fi
    fail 'reload server did not reach Done'
fi
verify_mods reload
send_command "function chunky_offline:config/set {\"radius\":$radius,\"x\":0,\"z\":0}"
sleep 2
send_command 'chunky confirm'
sleep 2
send_command 'chunky start'

deadline=$((SECONDS + observe_seconds))
reload_reproduced=false
while (( SECONDS < deadline )); do
    if has_watchdog_signature; then
        reload_reproduced=true
        break
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
        has_watchdog_signature && reload_reproduced=true
        break
    fi
    sleep 2
done
archive_runtime_files reload
printf 'watchdog_during_reload=%s\n' "$reload_reproduced" >>"$summary_file"
if [[ "$reload_reproduced" == true ]]; then
    printf 'result=REPRODUCED_DURING_RELOAD\n' >>"$summary_file"
    echo "large-world-repro: reproduced watchdog path while loading the pregenerated world; evidence: $report_dir"
    exit 0
fi

printf 'result=NOT_REPRODUCED\n' >>"$summary_file"
echo "large-world-repro: issue did not reproduce in this attempt; evidence: $report_dir" >&2
exit 2
