#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
manifest="$repo_dir/playtest/automation-matrix.json"
profile=${1:-lab}
mode=${2:-run}
timestamp=$(date +%Y%m%d-%H%M%S)
result_dir="$repo_dir/playtest-results/$timestamp-$profile"
baseline_dir="$repo_dir/playtest/baselines/$profile"
mkdir -p "$result_dir/screenshots" "$result_dir/videos" "$baseline_dir"

for tool in jq ydotool hyprctl grim magick ffmpeg; do
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
  local deadline=$((SECONDS + 180))
  while (( SECONDS < deadline )); do
    if hyprctl clients -j | jq -e '.[] | select(.class | startswith("Minecraft"))' >/dev/null; then return 0; fi
    sleep 2
  done
  printf 'Minecraft window did not appear.\n' >&2
  return 1
}

focus_minecraft() {
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
    [[ -f $log_file ]] && rg -q 'Created: 1024x1024x0 minecraft:textures/atlas/gui.png-atlas' "$log_file" && {
      sleep 4
      return 0
    }
    sleep 2
  done
  printf 'Minecraft main menu did not become ready.\n' >&2
  return 1
}

window_geometry() {
  hyprctl clients -j | jq -r '.[] | select(.class | startswith("Minecraft")) | "\(.at[0]),\(.at[1]) \(.size[0])x\(.size[1])"' | head -1
}

click_relative() {
  local fx=$1 fy=$2 x y
  read -r x y < <(hyprctl clients -j | jq -r --argjson fx "$fx" --argjson fy "$fy" '
    .[] | select(.class | startswith("Minecraft")) |
    [((.at[0] + (.size[0] * $fx)) / 2 | floor), ((.at[1] + (.size[1] * $fy)) / 2 | floor)] | @tsv' | head -1)
  ydotool mousemove --absolute "$x" "$y"
  ydotool click 0xC0 >/dev/null
}

key() { ydotool key "$1":1 "$1":0; }
type_text() { ydotool type --key-delay 2 -- "$1"; }
send_command() {
  # Slash opens the command box itself; typing a literal slash through ydotool
  # is keyboard-layout dependent and can silently turn a command into chat.
  key 53; sleep 0.3; type_text "${1#/}"; key 28; sleep 2
}
capture() { grim -g "$(window_geometry)" "$result_dir/screenshots/$1.png"; }

open_or_create_world() {
  # Mouse coordinates are relative to the verified Minecraft window and avoid
  # depending on focus order, which other installed mods can change.
  click_relative 0.5 0.4; sleep 4
  if find "$repo_dir/$game_dir/saves" -mindepth 1 -maxdepth 1 -type d -print -quit 2>/dev/null | grep -q .; then
    click_relative 0.40 0.25; sleep 0.5; click_relative 0.35 0.85
  else
    click_relative 0.65 0.85; sleep 3
    click_relative 0.50 0.54; sleep 0.5
    click_relative 0.35 0.94
  fi
  local deadline=$((SECONDS + 180))
  while (( SECONDS < deadline )); do
    [[ -f "$repo_dir/$game_dir/logs/latest.log" ]] &&
      if rg -q 'Magnetization 1\.3\.0 .* staged at|joined the game' "$repo_dir/$game_dir/logs/latest.log"; then
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
  send_command "/save-all"
  key 1; sleep 1
  click_relative 0.50 0.75
  deadline=$((SECONDS + 120))
  while (( SECONDS < deadline )); do
    tail -n "+$((before + 1))" "$log_file" | rg -q 'Stopping singleplayer server|Stopping server' && break
    sleep 2
  done
  sleep 5
  deadline=$((SECONDS + 180))
  while (( SECONDS < deadline )); do
    focus_minecraft
    click_relative 0.50 0.40; sleep 4
    click_relative 0.40 0.25; sleep 0.5; click_relative 0.35 0.85
    sleep 8
    if tail -n "+$((before + 1))" "$log_file" | rg -q 'joined the game'; then
      # Recipe-viewer and resource reload callbacks temporarily consume input
      # immediately after the join marker in compatibility-heavy profiles.
      sleep 8
      return 0
    fi
  done
  printf 'World did not reload before timeout.\n' >&2
  return 1
}

compare_or_seed() {
  local shot=$1 name base metric status
  name=$(basename "$shot")
  base="$baseline_dir/$name"
  if [[ ! -f $base ]]; then
    cp -- "$shot" "$base"
    printf '| %s | BASELINE_CREATED | first accepted capture |\n' "$name" >> "$report"
    return
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
if [[ $profile == lab ]]; then
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
while IFS= read -r station; do
  send_command "/magnetization playtest goto $station"
  while IFS= read -r station_action; do
    [[ -z $station_action ]] || send_command "$station_action"
  done < <(jq -r --arg s "$station" '.stationActions[$s][]? // empty' "$manifest")
  capture "$station"
  compare_or_seed "$result_dir/screenshots/$station.png"
  if jq -e --arg p "$profile" --arg s "$station" '
      $p == "lab" and (.guiStations | index($s) != null)' "$manifest" >/dev/null; then
    # Right-click the station fixture, retain the GUI (or interaction result),
    # then close it before issuing the next exact-state command.
    ydotool click 0xC1 >/dev/null
    sleep 2
    capture "$station-gui"
    compare_or_seed "$result_dir/screenshots/$station-gui.png"
    key 1
    sleep 1
  fi
  if jq -e --arg s "$station" '.videoStations | index($s) != null' "$manifest" >/dev/null; then
    video_action=$(jq -r --arg s "$station" '.videoActions[$s] // empty' "$manifest")
    ffmpeg -nostdin -hide_banner -loglevel error -y -f x11grab -framerate 30 \
      -video_size "$(window_geometry | sed 's/.* //')" \
      -i ":0.0+$(window_geometry | sed 's/ .*//' | tr ',' ',')" -t 8 \
      -c:v libx264 -preset ultrafast -crf 24 "$result_dir/videos/$station.mp4" &
    video_pid=$!
    sleep 1
    [[ -z $video_action ]] || send_command "$video_action"
    wait "$video_pid" || true
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
