#!/usr/bin/env bash
set -uo pipefail

if [[ $# -gt 2 ]]; then
    echo "usage: $0 [dwell-seconds] [timeout-seconds]" >&2
    exit 2
fi

dwell_seconds=${1:-210}
timeout_seconds=${2:-$((dwell_seconds + 180))}
log_file='run-smoke-server/logs/latest.log'
runner_output=$(mktemp)
runner_pid=''
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
smoke_signature="$repo_dir/build/moddev/serverSmokeRunProgramArgs[.]txt"

kill_smoke_children() {
    local smoke_pids
    smoke_pids=$(pgrep -f "$smoke_signature" || true)
    if [[ -n "$smoke_pids" ]]; then
        while read -r smoke_pid; do
            [[ -n "$smoke_pid" ]] && kill -TERM "$smoke_pid" 2>/dev/null || true
        done <<<"$smoke_pids"
        for _ in {1..20}; do
            smoke_pids=$(pgrep -f "$smoke_signature" || true)
            [[ -z "$smoke_pids" ]] && break
            sleep 0.25
        done
        while read -r smoke_pid; do
            [[ -n "$smoke_pid" ]] && kill -KILL "$smoke_pid" 2>/dev/null || true
        done <<<"$smoke_pids"
    fi
}

cleanup() {
    if [[ -n "$runner_pid" ]] && kill -0 "$runner_pid" 2>/dev/null; then
        kill -TERM -- "-$runner_pid" 2>/dev/null || true
        for _ in {1..20}; do
            kill -0 "$runner_pid" 2>/dev/null || break
            sleep 0.25
        done
        if kill -0 "$runner_pid" 2>/dev/null; then
            kill -KILL -- "-$runner_pid" 2>/dev/null || true
        fi
        wait "$runner_pid" 2>/dev/null || true
    fi
    # Gradle's single-use daemon starts a new session, so its ModDev child can
    # escape the wrapper's process group. Match the generated run-argument file
    # that is unique to this smoke profile and clean only that JVM.
    kill_smoke_children
    rm -f "$runner_output"
}
trap cleanup EXIT INT TERM

# Isolate the nested Gradle process and its Minecraft child so a Sable thread
# that outlives Minecraft's clean stop cannot hang the release gate or make us
# target unrelated development clients/servers.
setsid ./gradlew runServerSmoke --no-daemon --max-workers=1 \
    "-PmagSmokeSeconds=$dwell_seconds" >"$runner_output" 2>&1 &
runner_pid=$!
deadline=$((SECONDS + timeout_seconds))
booted=0
stopping_since=-1

while (( SECONDS < deadline )); do
    if [[ -f "$log_file" ]]; then
        if grep -Eq 'Done \([^)]*\)!|For help, type "help"' "$log_file"; then
            booted=1
        fi
        if (( booted == 1 && stopping_since < 0 )) && grep -q 'Stopping server' "$log_file"; then
            stopping_since=$SECONDS
        fi
        # A disposable smoke world does not need to wait forever for Sable's
        # native physics threads after Minecraft accepted the clean stop. Give
        # normal chunk saving a grace period, then let cleanup bound the run.
        if (( stopping_since >= 0 && SECONDS - stopping_since >= 15 )); then
            break
        fi
    fi

    if ! kill -0 "$runner_pid" 2>/dev/null; then
        wait "$runner_pid"
        runner_status=$?
        runner_pid=''
        if (( runner_status != 0 )); then
            echo "smokeServerMinimal: server runner exited with status $runner_status" >&2
            tail -n 200 "$runner_output" >&2
            exit 1
        fi
        break
    fi
    sleep 1
done

if (( booted != 1 )); then
    echo "smokeServerMinimal: timed out after ${timeout_seconds}s before the server reported Done" >&2
    [[ -f "$log_file" ]] && tail -n 200 "$log_file" >&2
    tail -n 100 "$runner_output" >&2
    exit 1
fi
if (( stopping_since < 0 )); then
    echo "smokeServerMinimal: server reported Done but never began clean shutdown" >&2
    [[ -f "$log_file" ]] && tail -n 200 "$log_file" >&2
    tail -n 100 "$runner_output" >&2
    exit 1
fi

echo 'smokeServerMinimal: server reached Done and accepted a clean stop; terminating only its lingering process group'
cleanup
trap - EXIT INT TERM

verify_args=(verifyServerSmokeLog --no-daemon --max-workers=1)
if [[ ${MAG_SMOKE_ALLOW_ERRORS:-0} == 1 ]]; then
    verify_args+=(-PmagSmokeAllowErrors)
fi
./gradlew "${verify_args[@]}"
