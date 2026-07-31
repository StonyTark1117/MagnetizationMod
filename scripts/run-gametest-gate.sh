#!/usr/bin/env bash
set -uo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
    echo "usage: $0 <gradle-run-task> <game-directory> [timeout-seconds]" >&2
    exit 2
fi

run_task=$1
game_directory=$2
timeout_seconds=${3:-300}
log_file="$game_directory/logs/latest.log"
runner_output=$(mktemp)
runner_pid=''

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
    rm -f "$runner_output"
}
trap cleanup EXIT INT TERM

# Sable sublevels from an interrupted/older test world can interfere with later
# globally-scanned physics tests. Preserve the generated directory in /tmp for
# diagnosis, but always exercise the gate against a genuinely fresh world.
if [[ -d "$game_directory" ]]; then
    archive="/tmp/magnetization-$(basename "$game_directory")-$(date +%s)-$$"
    mv "$game_directory" "$archive"
    echo "$run_task: moved previous generated run directory to $archive"
fi

# A separate process group lets the supervisor terminate the nested Gradle JVM
# and its lingering Minecraft/Sable child without touching any other Gradle run.
setsid ./gradlew "$run_task" --no-daemon >"$runner_output" 2>&1 &
runner_pid=$!
deadline=$((SECONDS + timeout_seconds))
passed=0

while (( SECONDS < deadline )); do
    if [[ -f "$log_file" ]]; then
        if grep -Eq 'required tests failed| failed at ' "$log_file"; then
            echo "$run_task: GameTest reported a failure" >&2
            tail -n 200 "$log_file" >&2
            exit 1
        fi
        if grep -Eq 'All [1-9][0-9]* required tests passed' "$log_file"; then
            passed=1
            # Give Minecraft a brief chance to finish its own shutdown. Sable's
            # physics thread is known to keep the JVM alive after this point.
            if grep -q 'Stopping server' "$log_file"; then
                sleep 3
                break
            fi
        fi
    fi

    if ! kill -0 "$runner_pid" 2>/dev/null; then
        wait "$runner_pid"
        runner_status=$?
        runner_pid=''
        if (( passed == 1 && runner_status == 0 )); then
            break
        fi
        echo "$run_task: process exited before a passing GameTest summary (status $runner_status)" >&2
        tail -n 200 "$runner_output" >&2
        exit 1
    fi
    sleep 1
done

if (( passed != 1 )); then
    echo "$run_task: timed out after ${timeout_seconds}s without a passing GameTest summary" >&2
    [[ -f "$log_file" ]] && tail -n 200 "$log_file" >&2
    tail -n 100 "$runner_output" >&2
    exit 1
fi

summary=$(grep -E 'GAME TESTS COMPLETE|All [1-9][0-9]* required tests passed' "$log_file" | tail -n 2)
printf '%s\n' "$summary"
echo "$run_task: assertions passed; terminating only its lingering process group"

# cleanup performs the bounded TERM/KILL and wait. Clear the trap afterward so
# a successful explicit cleanup cannot be mistaken for an interrupted gate.
cleanup
trap - EXIT INT TERM
exit 0
