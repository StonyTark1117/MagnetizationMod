#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -lt 3 || $# -gt 4 ]]; then
    echo "usage: $0 <gradle-run-task> <game-directory> <required-mod-ids-csv> [timeout-seconds]" >&2
    exit 2
fi

run_task=$1
game_directory=$2
required_mods=$3
timeout_seconds=${4:-240}
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
log_file="$repo_dir/$game_directory/logs/latest.log"
runner_output=$(mktemp)
xvfb_output=$(mktemp)
runner_pid=''
xvfb_pid=''

run_name=${run_task#run}
run_name=${run_name,}
client_signature="$repo_dir/build/moddev/${run_name}RunProgramArgs.txt"

client_pids() {
    ps -eo pid=,args= | awk -v signature="$client_signature" '
        index($0, signature) { print $1 }
    '
}

kill_client_children() {
    local pids
    pids=$(client_pids)
    [[ -z $pids ]] && return
    while read -r pid; do
        [[ -n $pid ]] && kill -TERM "$pid" 2>/dev/null || true
    done <<<"$pids"
    for _ in {1..20}; do
        pids=$(client_pids)
        [[ -z $pids ]] && return
        sleep 0.25
    done
    while read -r pid; do
        [[ -n $pid ]] && kill -KILL "$pid" 2>/dev/null || true
    done <<<"$pids"
}

cleanup() {
    if [[ -n $runner_pid ]] && kill -0 "$runner_pid" 2>/dev/null; then
        kill -TERM -- "-$runner_pid" 2>/dev/null || true
        for _ in {1..20}; do
            kill -0 "$runner_pid" 2>/dev/null || break
            sleep 0.25
        done
        kill -KILL -- "-$runner_pid" 2>/dev/null || true
        wait "$runner_pid" 2>/dev/null || true
    fi
    # A single-use Gradle daemon creates a new session. Match only this repo's
    # generated ModDev argument file so its client cannot survive the wrapper.
    kill_client_children
    if [[ -n $xvfb_pid ]] && kill -0 "$xvfb_pid" 2>/dev/null; then
        kill "$xvfb_pid" 2>/dev/null || true
        wait "$xvfb_pid" 2>/dev/null || true
    fi
    rm -f "$runner_output" "$xvfb_output"
}
trap cleanup EXIT INT TERM

display_number=$((90 + ($$ % 900)))
for _ in {1..100}; do
    [[ ! -e "/tmp/.X11-unix/X$display_number" ]] && break
    display_number=$((display_number + 1))
done
display=":$display_number"
Xvfb "$display" -screen 0 1280x720x24 -nolisten tcp >"$xvfb_output" 2>&1 &
xvfb_pid=$!
for _ in {1..40}; do
    [[ -e "/tmp/.X11-unix/X$display_number" ]] && break
    kill -0 "$xvfb_pid" 2>/dev/null || {
        echo "$run_task: Xvfb exited before its display became ready" >&2
        cat "$xvfb_output" >&2
        exit 1
    }
    sleep 0.25
done
[[ -e "/tmp/.X11-unix/X$display_number" ]] || {
    echo "$run_task: timed out waiting for Xvfb $display" >&2
    exit 1
}

mkdir -p "$repo_dir/$game_directory/logs"
rm -f "$log_file"
setsid env DISPLAY="$display" LIBGL_ALWAYS_SOFTWARE=1 \
    "$repo_dir/gradlew" "$run_task" --no-daemon --max-workers=1 --console=plain \
    >"$runner_output" 2>&1 &
runner_pid=$!
deadline=$((SECONDS + timeout_seconds))
ready=0

while (( SECONDS < deadline )); do
    if [[ -f $log_file ]] && grep -Eq 'Created: [0-9]+x[0-9]+x0 minecraft:textures/atlas/gui.png-atlas' "$log_file"; then
        ready=1
        break
    fi
    if ! kill -0 "$runner_pid" 2>/dev/null; then
        if wait "$runner_pid"; then status=0; else status=$?; fi
        runner_pid=''
        echo "$run_task: client exited before rendering the main menu (status $status)" >&2
        tail -n 200 "$runner_output" >&2
        [[ -f $log_file ]] && tail -n 200 "$log_file" >&2
        exit 1
    fi
    sleep 1
done

if (( ready != 1 )); then
    echo "$run_task: timed out after ${timeout_seconds}s before rendering the main menu" >&2
    tail -n 100 "$runner_output" >&2
    [[ -f $log_file ]] && tail -n 200 "$log_file" >&2
    exit 1
fi

IFS=',' read -ra mod_ids <<<"$required_mods"
for mod_id in "${mod_ids[@]}"; do
    if ! grep -Fq "($mod_id)" "$log_file"; then
        echo "$run_task: required mod '$mod_id' was absent from the loaded-mod list" >&2
        sed -n '1,100p' "$log_file" >&2
        exit 1
    fi
done

unexpected_errors=$(grep -En '/(ERROR|FATAL)\]' "$log_file" \
    | grep -Ev 'Error while loading the narrator$|Error starting SoundSystem\. Turning off sounds & music$|Invalid path in pack: byg:textures/block/track/TODO\.txt, ignoring$' \
    || true)
if [[ -n $unexpected_errors ]]; then
    printf '%s\n' "$unexpected_errors" >&2
    echo "$run_task: client reached the menu with ERROR/FATAL log entries" >&2
    exit 1
fi

if [[ $required_mods == 'ponder,railways,copycats' ]] \
        && ! grep -Fq 'Registered 16 core and 2 optional Magnetization Ponder scenes' "$log_file"; then
    echo "$run_task: Steam Rails and Copycats loaded, but both optional Ponder scenes did not register" >&2
    exit 1
fi

printf '%s: rendered main menu with required mods [%s] on isolated display %s\n' \
    "$run_task" "$required_mods" "$display"
