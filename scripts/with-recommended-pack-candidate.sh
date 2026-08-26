#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -lt 5 ]]; then
    echo "usage: $0 <game-directory> <candidate-jar> <manifest-json> <expected-manifest-sha256> -- <smoke-command...>" >&2
    exit 2
fi

game_directory=$(realpath "$1")
candidate_jar=$(realpath "$2")
manifest_json=$(realpath "$3")
expected_manifest_sha=$4
shift 4
[[ ${1:-} == -- ]] || { echo "missing -- before smoke command" >&2; exit 2; }
shift
[[ $# -gt 0 ]] || { echo "missing smoke command" >&2; exit 2; }

mods_dir="$game_directory/mods"
[[ -d $mods_dir && -f $candidate_jar && -f $manifest_json ]] || {
    echo "candidate wrapper: missing mods directory, candidate, or manifest" >&2
    exit 1
}
manifest_sha=$(sha256sum "$manifest_json" | cut -d' ' -f1)
[[ $manifest_sha == "$expected_manifest_sha" ]] || {
    echo "candidate wrapper: manifest digest mismatch: $manifest_sha" >&2
    exit 1
}

mapfile -t originals < <(find "$mods_dir" -maxdepth 1 -type f -name 'magnetization-*.jar' -print)
[[ ${#originals[@]} -eq 1 ]] || {
    echo "candidate wrapper: expected exactly one original Magnetization JAR, found ${#originals[@]}" >&2
    exit 1
}
original=${originals[0]}
original_name=$(basename "$original")
original_sha=$(sha256sum "$original" | cut -d' ' -f1)
candidate_name=$(basename "$candidate_jar")
candidate_sha=$(sha256sum "$candidate_jar" | cut -d' ' -f1)
backup_dir=$(mktemp -d)
backup="$backup_dir/$original_name"
restored=0

restore() {
    find "$mods_dir" -maxdepth 1 -type f -name 'magnetization-*.jar' -delete
    cp -- "$backup" "$mods_dir/$original_name"
    restored_sha=$(sha256sum "$mods_dir/$original_name" | cut -d' ' -f1)
    current_manifest_sha=$(sha256sum "$manifest_json" | cut -d' ' -f1)
    [[ $restored_sha == "$original_sha" && $current_manifest_sha == "$expected_manifest_sha" ]]
    [[ ! -e "$mods_dir/$candidate_name" || $candidate_name == "$original_name" ]]
    restored=1
}
cleanup() {
    if [[ $restored == 0 && -f $backup ]]; then restore || true; fi
    rm -rf -- "$backup_dir"
}
trap cleanup EXIT INT TERM

cp -- "$original" "$backup"
find "$mods_dir" -maxdepth 1 -type f -name 'magnetization-*.jar' -delete
cp -- "$candidate_jar" "$mods_dir/$candidate_name"
installed_sha=$(sha256sum "$mods_dir/$candidate_name" | cut -d' ' -f1)
[[ $installed_sha == "$candidate_sha" ]] || {
    echo "candidate wrapper: installed candidate digest mismatch" >&2
    exit 1
}

"$@"
restore
printf 'candidate wrapper: restored %s (%s); manifest %s unchanged; candidate absent\n' \
    "$original_name" "$original_sha" "$expected_manifest_sha"
