#!/usr/bin/env python3
"""Compare two performance-stress summary.json files."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def load(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--threshold-pct", type=float, default=10.0)
    parser.add_argument("--absolute-threshold-mspt", type=float)
    parser.add_argument("--fail-on-regression", action="store_true")
    args = parser.parse_args()
    if args.threshold_pct < 0:
        parser.error("--threshold-pct cannot be negative")

    baseline = load(args.baseline)
    candidate = load(args.candidate)
    comparable_keys = ("profile", "grid_size", "sample_ticks", "samples_per_scenario", "scenarios")
    mismatches = [
        key for key in comparable_keys
        if baseline["metadata"].get(key) != candidate["metadata"].get(key)
    ]
    if mismatches:
        raise SystemExit(f"reports are not comparable; metadata differs: {', '.join(mismatches)}")
    absolute_threshold = args.absolute_threshold_mspt
    if absolute_threshold is None:
        absolute_threshold = max(
            float(baseline["metadata"].get("absolute_noise_floor_mspt", 0.1)),
            float(candidate["metadata"].get("absolute_noise_floor_mspt", 0.1)),
        )
    if absolute_threshold < 0:
        parser.error("--absolute-threshold-mspt cannot be negative")
    before = {row["scenario"]: row for row in baseline["scenarios"]}
    after = {row["scenario"]: row for row in candidate["scenarios"]}
    shared = [row["scenario"] for row in baseline["scenarios"] if row["scenario"] in after]
    if not shared:
        raise SystemExit("the reports have no shared scenarios")

    print("scenario\tbaseline_mspt\tcandidate_mspt\tdelta_mspt\tdelta_pct\tstatus")
    regressions = []
    for name in shared:
        old = float(before[name]["median_mspt"])
        new = float(after[name]["median_mspt"])
        delta_mspt = new - old
        delta = 100.0 * (new / old - 1.0) if old else float("inf")
        status = "REGRESSION" if delta > args.threshold_pct and delta_mspt > absolute_threshold else "ok"
        if status == "REGRESSION":
            regressions.append(name)
        print(f"{name}\t{old:.6f}\t{new:.6f}\t{delta_mspt:+.6f}\t{delta:+.2f}%\t{status}")

    print(
        f"relative_threshold={args.threshold_pct:.2f}% "
        f"absolute_threshold={absolute_threshold:.3f}_mspt regressions={len(regressions)}"
    )
    if regressions and args.fail_on_regression:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
