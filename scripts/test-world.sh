#!/usr/bin/env bash
#
# test-world.sh — snapshot & restore Minecraft dev worlds so you build your
# test contraptions ONCE instead of every run.
#
# Sable persists ships inside the world save (the `sublevels/` dir +
# `sable_sub_level_occupancy.dat`), so a whole-world copy captures every
# assembled craft. Build your bearing/spring test rig once, `save` it, and
# `restore` it before each launch — a freeze-test force-quit can never cost
# you the build again.
#
# Snapshots live in test-worlds/ (project root, OUTSIDE the gitignored run/),
# so they survive `run/` cleanups. They are NOT committed by default (worlds
# are large binaries); force-add a specific one if you want to share it.
#
# Usage:
#   scripts/test-world.sh list
#   scripts/test-world.sh save    <live-world-name> [snapshot-name]
#   scripts/test-world.sh restore <snapshot-name>   [live-world-name]
#
# Examples:
#   scripts/test-world.sh save "New World (10)" freeze-rig   # snapshot it once
#   scripts/test-world.sh restore freeze-rig "New World"     # restore before a run
#
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SAVES_DIR="$PROJECT_DIR/run/saves"
SNAP_DIR="$PROJECT_DIR/test-worlds"

die() { echo "error: $*" >&2; exit 1; }

usage() {
    sed -n '2,33p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

cmd_list() {
    echo "Live worlds (run/saves/):"
    if [ -d "$SAVES_DIR" ] && [ -n "$(ls -A "$SAVES_DIR" 2>/dev/null)" ]; then
        ( cd "$SAVES_DIR" && ls -1d */ 2>/dev/null | sed 's#/$##; s/^/  /' )
    else
        echo "  (none)"
    fi
    echo "Snapshots (test-worlds/):"
    if [ -d "$SNAP_DIR" ] && [ -n "$(ls -A "$SNAP_DIR" 2>/dev/null)" ]; then
        ( cd "$SNAP_DIR" && ls -1d */ 2>/dev/null | sed 's#/$##; s/^/  /' )
    else
        echo "  (none)"
    fi
}

cmd_save() {
    [ $# -ge 1 ] || die "save needs a live-world name (see: $0 list)"
    local src="$SAVES_DIR/$1" name="${2:-$1}" dst
    dst="$SNAP_DIR/$name"
    [ -d "$src" ] || die "no such live world: '$1' (see: $0 list)"
    [ ! -e "$src/session.lock" ] || true   # lock file is copied harmlessly; MC re-creates it
    mkdir -p "$SNAP_DIR"
    rm -rf "$dst"
    cp -a "$src" "$dst"
    echo "Snapshot saved: '$1' -> test-worlds/$name"
}

cmd_restore() {
    [ $# -ge 1 ] || die "restore needs a snapshot name (see: $0 list)"
    local src="$SNAP_DIR/$1" name="${2:-$1}" dst
    dst="$SAVES_DIR/$name"
    [ -d "$src" ] || die "no such snapshot: '$1' (see: $0 list)"
    mkdir -p "$SAVES_DIR"
    rm -rf "$dst"
    cp -a "$src" "$dst"
    rm -f "$dst/session.lock"   # stale lock would block the game from opening it
    echo "Snapshot restored: test-worlds/$1 -> run/saves/$name"
    echo "(open '$name' in-game; ships in its sublevels/ load with it)"
}

[ $# -ge 1 ] || usage 1
case "$1" in
    list)    cmd_list ;;
    save)    shift; cmd_save "$@" ;;
    restore) shift; cmd_restore "$@" ;;
    -h|--help|help) usage 0 ;;
    *) die "unknown command '$1' (try: list | save | restore | help)" ;;
esac
