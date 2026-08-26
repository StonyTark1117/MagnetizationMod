#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -lt 6 || $# -gt 7 ]]; then
    echo "usage: $0 <game-directory> <neoforge-version> <required-mod-ids-csv> <evidence-png> <expected-version> <expected-sha256> [timeout-seconds]" >&2
    exit 2
fi

game_directory=$(realpath "$1")
neoforge_version=$2
required_mods=$3
evidence_png=$(realpath -m "$4")
expected_version=$5
expected_sha256=$6
timeout_seconds=${7:-600}
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
launcher_dir="$repo_dir/compat-fixtures/full-pack-launcher"
log_file="$game_directory/logs/latest.log"
runner_output=$(mktemp)
xvfb_output=$(mktemp)
frame_probe=$(mktemp --suffix=.png)
runner_pid=''
xvfb_pid=''

mapfile -t candidate_jars < <(find "$game_directory/mods" -maxdepth 1 -type f -name 'magnetization-*.jar' -print)
if [[ ${#candidate_jars[@]} -ne 1 ]]; then
    printf 'Expected exactly one Magnetization candidate in %s/mods, found %s\n' \
        "$game_directory" "${#candidate_jars[@]}" >&2
    exit 1
fi
candidate_version=$(unzip -p "${candidate_jars[0]}" META-INF/neoforge.mods.toml \
    | sed -n 's/^[[:space:]]*version[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
[[ $candidate_version == "$expected_version" ]] || {
    printf 'Expected candidate version %s but found version %s in %s\n' \
        "$expected_version" "$candidate_version" "${candidate_jars[0]}" >&2
    exit 1
}
candidate_sha=$(sha256sum "${candidate_jars[0]}" | cut -d' ' -f1)
[[ $candidate_sha == "$expected_sha256" ]] || {
    printf 'Expected candidate SHA-256 %s but found %s in %s\n' \
        "$expected_sha256" "$candidate_sha" "${candidate_jars[0]}" >&2
    exit 1
}

client_signature="$launcher_dir/build/moddev/clientRunProgramArgs.txt"
client_pids() {
    ps -eo pid=,args= | awk -v signature="$client_signature" 'index($0, signature) { print $1 }'
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
    kill_client_children
    if [[ -n $xvfb_pid ]] && kill -0 "$xvfb_pid" 2>/dev/null; then
        kill "$xvfb_pid" 2>/dev/null || true
        wait "$xvfb_pid" 2>/dev/null || true
    fi
    rm -f "$runner_output" "$xvfb_output" "$frame_probe"
}
trap cleanup EXIT INT TERM

display_number=$((1000 + ($$ % 3000)))
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
        echo "Full-pack smoke: Xvfb exited before $display became ready" >&2
        sed -n '1,120p' "$xvfb_output" >&2
        exit 1
    }
    sleep 0.25
done
[[ -e "/tmp/.X11-unix/X$display_number" ]] || {
    echo "Full-pack smoke: timed out waiting for Xvfb $display" >&2
    exit 1
}

mkdir -p "$game_directory/logs" "$(dirname -- "$evidence_png")"
rm -f "$log_file"
setsid env -u WAYLAND_DISPLAY DISPLAY="$display" XDG_SESSION_TYPE=x11 \
    QT_QPA_PLATFORM=xcb GDK_BACKEND=x11 LIBGL_ALWAYS_SOFTWARE=1 \
    "$repo_dir/gradlew" -p "$launcher_dir" runClient \
    "-PpackGameDirectory=$game_directory" "-PneoForgeVersion=$neoforge_version" \
    --no-daemon --max-workers=1 --console=plain >"$runner_output" 2>&1 &
runner_pid=$!
deadline=$((SECONDS + timeout_seconds))
ready=0
reload_complete_at=0
full_resource_reload=0
non_splash_since=0

while (( SECONDS < deadline )); do
    if [[ -f $log_file ]] && grep -Eq \
        'Error during pre-loading phase|Loading errors encountered|Mod loading errors found' "$log_file"; then
        echo "Full-pack smoke: NeoForge reported a mod-loading error before startup completed" >&2
        rg -n 'Error during pre-loading phase|Loading errors encountered|Mod loading errors found|Requested by:|Expected range:|Actual version:' \
            "$log_file" | tail -n 100 >&2 || true
        exit 1
    fi
    if [[ -f $log_file ]] && grep -Eq \
        'Reloading ResourceManager:.*mod/magnetization' "$log_file"; then
        full_resource_reload=1
    fi
    if [[ -f $log_file ]] \
        && (( full_resource_reload == 1 )) \
        && grep -Eq 'Created: [0-9]+x[0-9]+x0 minecraft:textures/atlas/gui.png-atlas' "$log_file"; then
        if (( reload_complete_at == 0 )); then
            reload_complete_at=$SECONDS
        elif (( SECONDS - reload_complete_at >= 30 )); then
            DISPLAY="$display" import -window root "$frame_probe"
            mojang_red_fraction=$(magick "$frame_probe" -fuzz 8% \
                -fill black +opaque '#f8323a' -fill white -opaque '#f8323a' \
                -format '%[fx:mean]' info:)
            frame_luminance=$(magick "$frame_probe" -colorspace Gray \
                -resize 1x1! -format '%[fx:mean]' info:)
            if awk -v red="$mojang_red_fraction" -v luminance="$frame_luminance" \
                    'BEGIN { exit ! (red <= 0.70 && luminance >= 0.02) }'; then
                if (( non_splash_since == 0 )); then
                    non_splash_since=$SECONDS
                elif (( SECONDS - non_splash_since >= 10 )); then
                    ready=1
                    break
                fi
            else
                non_splash_since=0
            fi
        fi
    fi
    if ! kill -0 "$runner_pid" 2>/dev/null; then
        if wait "$runner_pid"; then status=0; else status=$?; fi
        runner_pid=''
        echo "Full-pack smoke: client exited before reaching a stable post-reload frame (status $status)" >&2
        tail -n 240 "$runner_output" >&2
        [[ -f $log_file ]] && tail -n 240 "$log_file" >&2
        exit 1
    fi
    sleep 1
done

if (( ready != 1 )); then
    echo "Full-pack smoke: timed out after ${timeout_seconds}s before reaching a stable post-reload frame" >&2
    tail -n 160 "$runner_output" >&2
    [[ -f $log_file ]] && tail -n 240 "$log_file" >&2
    exit 1
fi

IFS=',' read -ra mod_ids <<<"$required_mods"
for mod_id in "${mod_ids[@]}"; do
    if ! grep -Fq "($mod_id)" "$log_file"; then
        echo "Full-pack smoke: required mod '$mod_id' was absent from the loaded-mod list" >&2
        exit 1
    fi
done

candidate_sha_after=$(sha256sum "${candidate_jars[0]}" | cut -d' ' -f1)
[[ $candidate_sha_after == "$expected_sha256" ]] || {
    echo "Full-pack smoke: candidate digest changed before launch" >&2
    exit 1
}

if grep -En '/(ERROR|FATAL)\].*(magnetization|Magnetization)' "$log_file"; then
    echo "Full-pack smoke: Magnetization emitted an ERROR/FATAL entry during client boot" >&2
    exit 1
fi

DISPLAY="$display" import -window root "$evidence_png"
printf 'Full-pack smoke: captured a stable client frame after the full mod-resource reload with candidate %s (%s) and required mods [%s] on private X server %s\n' \
    "$candidate_version" "$candidate_sha" "$required_mods" "$display"
