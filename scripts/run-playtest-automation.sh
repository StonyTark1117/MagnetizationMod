#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
manifest="$repo_dir/playtest/automation-matrix.json"
profile=${1:-lab}
mode=${2:-run}
backend=${PLAYTEST_BACKEND:-auto}
timestamp=$(date +%Y%m%d-%H%M%S)
result_dir="$repo_dir/playtest-results/$timestamp-$profile"
baseline_dir="$repo_dir/playtest/baselines/$profile"
mkdir -p "$result_dir/screenshots" "$result_dir/videos" "$baseline_dir"

if [[ $backend == auto ]]; then
  if [[ -n ${DISPLAY:-} && -z ${HYPRLAND_INSTANCE_SIGNATURE:-} ]]; then backend=x11; else backend=hyprland; fi
fi
[[ $backend == hyprland || $backend == x11 ]] || {
  printf 'Unknown PLAYTEST_BACKEND: %s (expected hyprland or x11)\n' "$backend" >&2; exit 2;
}
tools=(jq magick ffmpeg)
if [[ $backend == hyprland ]]; then tools+=(ydotool hyprctl grim); else tools+=(xdotool import); fi
for tool in "${tools[@]}"; do
  command -v "$tool" >/dev/null || { printf 'Missing required tool: %s\n' "$tool" >&2; exit 2; }
done
jq -e --arg p "$profile" '.profiles[$p]' "$manifest" >/dev/null || {
  printf 'Unknown profile: %s\n' "$profile" >&2; exit 2;
}

task=$(jq -r --arg p "$profile" '.profiles[$p].gradleTask' "$manifest")
game_dir=$(jq -r --arg p "$profile" '.profiles[$p].gameDirectory' "$manifest")
reset_command=$(jq -r --arg p "$profile" '.profiles[$p].resetCommand' "$manifest")
threshold=$(jq -r '.visualThreshold' "$manifest")
fatal_patterns=$(jq -r '.fatalLogPatterns' "$manifest")
report="$result_dir/report.md"
gradle_pid=

cleanup() {
  if [[ -n ${gradle_pid:-} ]] && kill -0 "$gradle_pid" 2>/dev/null; then
    kill "$gradle_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

mkdir -p "$repo_dir/$game_dir"
if [[ ! -f "$repo_dir/$game_dir/options.txt" ]]; then
  cp -- "$repo_dir/playtest/options.txt" "$repo_dir/$game_dir/options.txt"
fi

wait_for_window() {
  # The full optional-compatibility lab can spend more than three minutes in
  # resource reload on a cold Gradle cache before GLFW exposes the window.
  local deadline=$((SECONDS + 300))
  while (( SECONDS < deadline )); do
    if [[ $backend == x11 ]]; then
      xdotool search --onlyvisible --class 'Minecraft' >/dev/null 2>&1 && return 0
    elif hyprctl clients -j | jq -e '.[] | select(.class | startswith("Minecraft"))' >/dev/null; then
      return 0
    fi
    sleep 2
  done
  printf 'Minecraft window did not appear.\n' >&2
  return 1
}

focus_minecraft() {
  if [[ $backend == x11 ]]; then
    local window
    window=$(xdotool search --onlyvisible --class 'Minecraft' | head -1)
    [[ -n $window ]] || return 1
    xdotool windowfocus --sync "$window"
    return
  fi
  local workspace
  workspace=$(hyprctl clients -j | jq -r '.[] | select(.class | startswith("Minecraft")) | .workspace.id' | head -1)
  [[ -n $workspace ]] || return 1
  # Hyprland keybinds remain the most reliable focus boundary on this test host.
  if (( workspace >= 1 && workspace <= 9 )); then
    ydotool key 125:1 "$((workspace + 1))":1 "$((workspace + 1))":0 125:0
  fi
  sleep 1
  hyprctl activewindow -j | jq -e '.class | startswith("Minecraft")' >/dev/null
}

wait_for_main_menu() {
  local log_file="$repo_dir/$game_dir/logs/latest.log" deadline=$((SECONDS + 180))
  while (( SECONDS < deadline )); do
    [[ -f $log_file ]] && rg -q 'Created: [^ ]+ minecraft:textures/atlas/gui.png-atlas' "$log_file" && {
      sleep 4
      return 0
    }
    sleep 2
  done
  printf 'Minecraft main menu did not become ready.\n' >&2
  return 1
}

window_geometry() {
  if [[ $backend == x11 ]]; then
    local window geometry x y width height
    window=$(xdotool search --onlyvisible --class 'Minecraft' | head -1)
    geometry=$(xdotool getwindowgeometry --shell "$window")
    x=$(sed -n 's/^X=//p' <<<"$geometry")
    y=$(sed -n 's/^Y=//p' <<<"$geometry")
    width=$(sed -n 's/^WIDTH=//p' <<<"$geometry")
    height=$(sed -n 's/^HEIGHT=//p' <<<"$geometry")
    printf '%s,%s %sx%s\n' "$x" "$y" "$width" "$height"
    return
  fi
  hyprctl clients -j | jq -r '.[] | select(.class | startswith("Minecraft")) | "\(.at[0]),\(.at[1]) \(.size[0])x\(.size[1])"' | head -1
}

click_relative() {
  local fx=$1 fy=$2 x y
  if [[ $backend == x11 ]]; then
    local geometry gx gy size width height
    geometry=$(window_geometry)
    read -r gx gy size < <(sed 's/,/ /; s/ / /' <<<"$geometry")
    width=${size%x*}; height=${size#*x}
    x=$(awk -v p="$gx" -v w="$width" -v f="$fx" 'BEGIN { printf "%d", p + w * f }')
    y=$(awk -v p="$gy" -v h="$height" -v f="$fy" 'BEGIN { printf "%d", p + h * f }')
    xdotool mousemove "$x" "$y" click 1
    return
  fi
  read -r x y < <(hyprctl clients -j | jq -r --argjson fx "$fx" --argjson fy "$fy" '
    .[] | select(.class | startswith("Minecraft")) |
    [((.at[0] + (.size[0] * $fx)) / 2 | floor), ((.at[1] + (.size[1] * $fy)) / 2 | floor)] | @tsv' | head -1)
  ydotool mousemove --absolute "$x" "$y"
  ydotool click 0xC0 >/dev/null
}

key() {
  if [[ $backend == x11 ]]; then
    case $1 in 1) xdotool key Escape ;; 28) xdotool key Return ;; 53) xdotool key slash ;;
      *) printf 'Unsupported X11 key code: %s\n' "$1" >&2; return 2 ;; esac
  else
    ydotool key "$1":1 "$1":0
  fi
}
type_text() {
  if [[ $backend == x11 ]]; then xdotool type --delay 2 -- "$1"; else ydotool type --key-delay 2 -- "$1"; fi
}
click_right() {
  if [[ $backend == x11 ]]; then xdotool click 3; else ydotool click 0xC1 >/dev/null; fi
}
send_command() {
  # Slash opens the command box itself; typing a literal slash through ydotool
  # is keyboard-layout dependent and can silently turn a command into chat.
  focus_minecraft
  key 53; sleep 0.3; type_text "${1#/}"; key 28; sleep 2
}
goto_station() {
  local station=$1 log_file="$repo_dir/$game_dir/logs/latest.log" before attempt
  before=$(wc -l < "$log_file")
  for attempt in 1 2 3; do
    send_command "/magnetization playtest goto $station"
    if tail -n "+$((before + 1))" "$log_file" | rg -q "\[CHAT\] Station $station at"; then return 0; fi
    if (( attempt == 2 )); then
      # Normalize chat/pause/world without guessing screen state. If Escape
      # opened the pause menu, the integrated server logs that transition and
      # a second Escape returns to the world; otherwise the first Escape closed
      # the stale screen and already returned to the world.
      local escape_before
      escape_before=$(wc -l < "$log_file")
      key 1; sleep 1
      if tail -n "+$((escape_before + 1))" "$log_file" | rg -q 'Saving and pausing game'; then
        key 1; sleep 1
      fi
    fi
  done
  printf 'Station command did not execute in-world: %s\n' "$station" >&2
  return 1
}
capture() {
  if [[ $backend == x11 ]]; then
    import -window "$(xdotool search --onlyvisible --class 'Minecraft' | head -1)" "$result_dir/screenshots/$1.png"
  else
    grim -g "$(window_geometry)" "$result_dir/screenshots/$1.png"
  fi
}

open_or_create_world() {
  # Mouse coordinates are relative to the verified Minecraft window and avoid
  # depending on focus order, which other installed mods can change.
  if ! find "$repo_dir/$game_dir/saves" -mindepth 1 -maxdepth 1 -type d -print -quit 2>/dev/null | grep -q .; then
    focus_minecraft; sleep 4; click_relative 0.50 0.42; sleep 4
    click_relative 0.65 0.85; sleep 3
    click_relative 0.50 0.54; sleep 0.5
    click_relative 0.35 0.94
  else
    local attempt joined_deadline
    for attempt in 1 2 3; do
      focus_minecraft
      # Resource reload can leave the title visible before it accepts clicks.
      # Escape safely dismisses Options/world-selection if a prior attempt only
      # partially navigated, then the exact title/world buttons are retried.
      key 1; sleep 2
      click_relative 0.50 0.42; sleep 4
      click_relative 0.40 0.25; sleep 0.5
      click_relative 0.31 0.82
      joined_deadline=$((SECONDS + 60))
      while (( SECONDS < joined_deadline )); do
        if rg -q 'Magnetization [0-9]+\.[0-9]+\.[0-9]+ .* staged at|joined the game' \
                "$repo_dir/$game_dir/logs/latest.log"; then
          sleep 8
          return 0
        fi
        sleep 2
      done
    done
  fi
  local deadline=$((SECONDS + 180))
  while (( SECONDS < deadline )); do
    [[ -f "$repo_dir/$game_dir/logs/latest.log" ]] &&
      if rg -q 'Magnetization [0-9]+\.[0-9]+\.[0-9]+ .* staged at|joined the game' "$repo_dir/$game_dir/logs/latest.log"; then
        sleep 8
        return 0
      fi
    sleep 2
  done
  printf 'World did not stage before timeout.\n' >&2
  return 1
}

reload_world() {
  local log_file="$repo_dir/$game_dir/logs/latest.log" before deadline
  before=$(wc -l < "$log_file")
  key 1; sleep 1
  # At Minecraft's enforced 854x480 test resolution the bottom quit button is
  # centred at 80% height. The old 75% click landed in the gap above it and
  # made the persistence gate wait on a world that never closed.
  click_relative 0.50 0.80
  deadline=$((SECONDS + 120))
  while (( SECONDS < deadline )); do
    tail -n "+$((before + 1))" "$log_file" | rg -q 'Stopping singleplayer server|Stopping server' && break
    sleep 2
  done
  # The title screen is not interactive when the server stop marker first
  # appears: compatibility-heavy clients may still be tearing down render
  # workers. Wait for that client-side boundary before clicking any menu.
  deadline=$((SECONDS + 120))
  while (( SECONDS < deadline )); do
    tail -n "+$((before + 1))" "$log_file" | rg -q 'ChunkBuilder.*Stopping worker threads' && break
    sleep 2
  done
  sleep 3
  # Escape is harmless on the title screen and recovers from an Options screen
  # if a slow transition retained a previous click.
  key 1
  sleep 1
  focus_minecraft
  click_relative 0.50 0.42
  sleep 4
  click_relative 0.40 0.25
  sleep 0.5
  click_relative 0.31 0.82
  deadline=$((SECONDS + 180))
  while (( SECONDS < deadline )); do
    if tail -n "+$((before + 1))" "$log_file" | rg -q 'joined the game'; then
      # Recipe-viewer and resource reload callbacks temporarily consume input
      # immediately after the join marker in compatibility-heavy profiles.
      sleep 8
      return 0
    fi
    sleep 2
  done
  printf 'World did not reload before timeout.\n' >&2
  return 1
}

compare_or_seed() {
  local shot=$1 name base metric status
  name=$(basename "$shot")
  base="$baseline_dir/$name"
  if [[ $mode == record ]]; then
    cp -- "$shot" "$base"
    printf '| %s | BASELINE_RECORDED | explicit record mode |\n' "$name" >> "$report"
    return
  fi
  if [[ ! -f $base ]]; then
    printf '| %s | FAIL | immutable baseline missing; rerun explicitly with record mode |\n' "$name" >> "$report"
    printf 'Missing immutable baseline: %s\n' "$base" >&2
    return 3
  fi
  metric=$(magick compare -metric RMSE "$base" "$shot" null: 2>&1 | sed -n 's/.*(\([^)]*\)).*/\1/p' || true)
  status=PASS
  awk -v m="${metric:-1}" -v t="$threshold" 'BEGIN { exit !(m > t) }' && status=FAIL || true
  printf '| %s | %s | RMSE %s (limit %s) |\n' "$name" "$status" "${metric:-unavailable}" "$threshold" >> "$report"
}

if [[ $mode == attach ]]; then
  wait_for_window
else
  (cd "$repo_dir" && ./gradlew "$task" --no-daemon >"$result_dir/gradle.log" 2>&1) &
  gradle_pid=$!
  wait_for_window
fi
focus_minecraft
if [[ $mode != attach ]]; then wait_for_main_menu; open_or_create_world; fi

{
  printf '# Magnetization automated playtest: %s\n\n' "$profile"
  printf -- '- Timestamp: `%s`\n- Profile: `%s`\n- Gradle task: `%s`\n\n' "$timestamp" "$profile" "$task"
  printf '| Capture | Result | Detail |\n|---|---|---|\n'
} > "$report"

send_command "$reset_command"
for golem_type in gallium_golem mr_fluid_golem magnetite_golem pyrrhotite_golem hematite_golem titanomagnetite_golem; do
  send_command "/kill @e[type=magnetization:$golem_type]"
done
if [[ $profile == lab && ${PLAYTEST_SKIP_PERSISTENCE:-0} != 1 ]]; then
  send_command "/magnetization playtest scenario persistence seed"
  capture "persistence-seeded"
  reload_world
  send_command "/magnetization playtest scenario persistence verify"
  capture "persistence-reloaded"
  assertion_deadline=$((SECONDS + 15))
  while (( SECONDS < assertion_deadline )); do
    rg -q 'PLAYTEST_ASSERT (PASS|FAIL) persistence' "$repo_dir/$game_dir/logs/latest.log" && break
    sleep 1
  done
  if rg -q 'PLAYTEST_ASSERT PASS persistence' "$repo_dir/$game_dir/logs/latest.log"; then
    printf '| persistence-reloaded.png | PASS | actual save, title exit, reopen, and server-side state assertion |\n' >> "$report"
  else
    printf '| persistence-reloaded.png | FAIL | persistence assertion marker missing |\n' >> "$report"
    printf 'Persistence reload assertion failed.\n' >&2
    exit 1
  fi
  send_command "$reset_command"
  if [[ $mode == persistence ]]; then
    printf '\n## Diagnostics\n\n- Persistence-only run completed successfully.\n' >> "$report"
    printf 'Report: %s\n' "$report"
    exit 0
  fi
fi
# Every lab traversal should inspect running machines, including focused runs
# that deliberately skip the save/reopen proof. The persistence fixture is also
# the authoritative active-state seed for Electrolyzer, Tokamak, Fusion, and
# Railgun rather than leaving their accepted captures at empty defaults.
if [[ $profile == lab ]]; then
  send_command "/magnetization playtest scenario persistence seed"
  sleep 12
fi
while IFS= read -r station; do
  if [[ -n ${PLAYTEST_STATIONS:-} && ",$PLAYTEST_STATIONS," != *",$station,"* ]]; then
    continue
  fi
  goto_station "$station"
  while IFS= read -r station_action; do
    [[ -z $station_action ]] || send_command "$station_action"
  done < <(jq -r --arg s "$station" '.stationActions[$s][]? // empty' "$manifest")
  if jq -e --arg p "$profile" --arg s "$station" '
      (.stationActions[$s] | length > 0)
      or ($p == "lab" and (.guiStations | index($s) != null))' "$manifest" >/dev/null; then
    # Command feedback would otherwise be mistaken for part of the accepted HUD.
    # Leave the HUD visible and wait only for the transient chat lines to fade.
    sleep 12
  fi
  if jq -e --arg s "$station" '.itemUseStations | index($s) != null' "$manifest" >/dev/null; then
    click_right
    sleep 2
  fi
  capture "$station"
  compare_or_seed "$result_dir/screenshots/$station.png"
  if jq -e --arg s "$station" '.closeAfterCaptureStations | index($s) != null' "$manifest" >/dev/null; then
    key 1
    sleep 1
  fi
  if jq -e --arg p "$profile" --arg s "$station" '
      $p == "lab" and (.guiStations | index($s) != null)' "$manifest" >/dev/null; then
    # A screenshot is not GUI evidence unless a real Magnetization screen opened.
    screen_before=$(wc -l < "$repo_dir/$game_dir/logs/latest.log")
    click_right
    screen_deadline=$((SECONDS + 10))
    while (( SECONDS < screen_deadline )); do
      if tail -n "+$((screen_before + 1))" "$repo_dir/$game_dir/logs/latest.log" |
          rg -q 'PLAYTEST_SCREEN_OPEN com\.stonytark\.magnetization\.client\.screen\.'; then
        break
      fi
      sleep 1
    done
    if ! tail -n "+$((screen_before + 1))" "$repo_dir/$game_dir/logs/latest.log" |
        rg -q 'PLAYTEST_SCREEN_OPEN com\.stonytark\.magnetization\.client\.screen\.'; then
      printf '| %s-gui.png | FAIL | no Magnetization screen-open marker after interaction |\n' "$station" >> "$report"
      printf 'Expected Magnetization GUI did not open at station: %s\n' "$station" >&2
      exit 1
    fi
    sleep 2
    capture "$station-gui"
    compare_or_seed "$result_dir/screenshots/$station-gui.png"
    key 1
    sleep 1
  fi
  if [[ $profile == lab ]] && jq -e --arg s "$station" '.videoStations | index($s) != null' "$manifest" >/dev/null; then
    video_action=$(jq -r --arg s "$station" '.videoActions[$s] // empty' "$manifest")
    ffmpeg -nostdin -hide_banner -loglevel error -y -f x11grab -framerate 30 \
      -video_size "$(window_geometry | sed 's/.* //')" \
      -i "${DISPLAY:-:0.0}+$(window_geometry | sed 's/ .*//' | tr ',' ',')" -t 8 \
      -c:v libx264 -preset ultrafast -crf 24 "$result_dir/videos/$station.mp4" &
    video_pid=$!
    sleep 1
    [[ -z $video_action ]] || send_command "$video_action"
    video_cleanup=$(jq -r --arg s "$station" '.videoCleanupActions[$s] // empty' "$manifest")
    video_cleanup_delay=$(jq -r --arg s "$station" '.videoCleanupDelays[$s] // empty' "$manifest")
    if [[ -n $video_cleanup && -n $video_cleanup_delay ]]; then
      sleep "$video_cleanup_delay"
      send_command "$video_cleanup"
      video_cleanup=
    fi
    wait "$video_pid" || true
    [[ -z $video_cleanup ]] || send_command "$video_cleanup"
    if jq -e --arg s "$station" '.videoBaselineStations | index($s) != null' "$manifest" >/dev/null; then
      ffmpeg -nostdin -hide_banner -loglevel error -y -i "$result_dir/videos/$station.mp4" \
        -vf 'fps=2,scale=213:120,tile=4x4:padding=0:margin=0' -frames:v 1 \
        "$result_dir/screenshots/$station-transition.png"
      compare_or_seed "$result_dir/screenshots/$station-transition.png"
    fi
  fi
done < <(jq -r --arg p "$profile" '.profiles[$p].stations[]' "$manifest")

log_file="$repo_dir/$game_dir/logs/latest.log"
if rg -n -i "$fatal_patterns" "$log_file" > "$result_dir/log-findings.txt"; then
  if rg -i 'magnetization|magnetization:' "$result_dir/log-findings.txt" > "$result_dir/mod-log-findings.txt"; then
    log_status=FAIL
  else
    log_status=WARN
    : > "$result_dir/mod-log-findings.txt"
  fi
else
  log_status=PASS
  : > "$result_dir/log-findings.txt"
  : > "$result_dir/mod-log-findings.txt"
fi
{
  printf '\n## Diagnostics\n\n'
  printf -- '- Log scan: **%s** (`log-findings.txt`; owned findings in `mod-log-findings.txt`)\n' "$log_status"
  printf -- '- Screenshots: `%s`\n- Videos: `%s`\n' "screenshots/" "videos/"
} >> "$report"

printf 'Report: %s\n' "$report"
