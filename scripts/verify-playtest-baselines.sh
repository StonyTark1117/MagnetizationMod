#!/usr/bin/env bash
set -Eeuo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
manifest="$repo_dir/playtest/automation-matrix.json"
checksums="$repo_dir/playtest/baselines/SHA256SUMS"
missing=0

while IFS=$'\t' read -r profile station; do
  baseline="$repo_dir/playtest/baselines/$profile/$station.png"
  if [[ ! -f $baseline ]]; then
    printf 'Missing playtest baseline: playtest/baselines/%s/%s.png\n' "$profile" "$station" >&2
    missing=1
  fi
done < <(jq -r '.profiles | to_entries[] | .key as $profile | .value.stations[] | [$profile, .] | @tsv' "$manifest")

# Lab GUI captures are a second immutable surface derived from a declared
# station, so checking only the station still would let a missing GUI baseline
# pass the release gate.
while IFS= read -r station; do
  baseline="$repo_dir/playtest/baselines/lab/$station-gui.png"
  if [[ ! -f $baseline ]]; then
    printf 'Missing playtest baseline: playtest/baselines/lab/%s-gui.png\n' "$station" >&2
    missing=1
  fi
done < <(jq -r '.guiStations[]' "$manifest")

while IFS= read -r station; do
  baseline="$repo_dir/playtest/baselines/lab/$station-transition.png"
  if [[ ! -f $baseline ]]; then
    printf 'Missing playtest baseline: playtest/baselines/lab/%s-transition.png\n' "$station" >&2
    missing=1
  fi
done < <(jq -r '.videoBaselineStations[]' "$manifest")

(( missing == 0 )) || exit 1

if [[ ! -f $checksums ]]; then
  printf 'Missing immutable playtest checksum manifest: playtest/baselines/SHA256SUMS\n' >&2
  exit 1
fi

expected_count=$(find "$repo_dir/playtest/baselines" -type f -name '*.png' | wc -l)
manifest_count=$(wc -l < "$checksums")
if [[ $expected_count -ne $manifest_count ]]; then
  printf 'Playtest checksum manifest has %s entries for %s PNG files.\n' \
    "$manifest_count" "$expected_count" >&2
  exit 1
fi

(cd "$repo_dir/playtest/baselines" && sha256sum --check --strict SHA256SUMS)
printf 'All immutable playtest baselines are declared, present, and checksum-verified.\n'
