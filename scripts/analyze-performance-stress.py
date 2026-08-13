#!/usr/bin/env python3
"""Turn raw tick-sprint samples into stable CSV, JSON, and Markdown reports."""

from __future__ import annotations

import argparse
import csv
import json
import math
import statistics
from pathlib import Path


def round_number(value: float) -> float:
    return round(value, 6)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--samples", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    args.output.mkdir(parents=True, exist_ok=True)
    with args.metadata.open(encoding="utf-8") as handle:
        metadata = json.load(handle)

    rows: list[dict[str, object]] = []
    by_scenario: dict[str, list[float]] = {}
    with args.samples.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle, delimiter="\t"):
            parsed = {
                "scenario": row["scenario"],
                "sample": int(row["sample"]),
                "ticks": int(row["ticks"]),
                "tps": float(row["tps"].replace(",", "")),
                "mspt": float(row["mspt"].replace(",", "")),
            }
            rows.append(parsed)
            by_scenario.setdefault(str(parsed["scenario"]), []).append(float(parsed["mspt"]))

    if not rows:
        raise SystemExit("no measurement samples found")
    scenario_order = metadata["scenarios"]
    missing = [name for name in scenario_order if name not in by_scenario]
    if missing:
        raise SystemExit(f"missing samples for scenarios: {', '.join(missing)}")

    baseline = statistics.median(by_scenario["empty_start"])
    summaries: list[dict[str, object]] = []
    warnings: list[str] = []
    for name in scenario_order:
        values = by_scenario[name]
        mean = statistics.fmean(values)
        median = statistics.median(values)
        stdev = statistics.stdev(values) if len(values) > 1 else 0.0
        cv_pct = 100.0 * stdev / mean if mean else math.inf
        mad = statistics.median(abs(value - median) for value in values)
        scaled_mad_mspt = 1.4826 * mad
        robust_cv_pct = 100.0 * scaled_mad_mspt / median if median else math.inf
        spread_mspt = max(values) - min(values)
        summary = {
            "scenario": name,
            "samples": len(values),
            "median_mspt": round_number(median),
            "mean_mspt": round_number(mean),
            "min_mspt": round_number(min(values)),
            "max_mspt": round_number(max(values)),
            "stdev_mspt": round_number(stdev),
            "cv_pct": round_number(cv_pct),
            "robust_cv_pct": round_number(robust_cv_pct),
            "scaled_mad_mspt": round_number(scaled_mad_mspt),
            "spread_mspt": round_number(spread_mspt),
            "delta_vs_empty_mspt": round_number(median - baseline),
            "relative_vs_empty_pct": round_number(100.0 * (median / baseline - 1.0)) if baseline else None,
        }
        summaries.append(summary)
        noise_floor = metadata.get("absolute_noise_floor_mspt", 0.1)
        if robust_cv_pct > metadata["stability_cv_threshold_pct"] and scaled_mad_mspt > noise_floor:
            warnings.append(
                f"{name}: robust sample CV {robust_cv_pct:.2f}% exceeds "
                f"{metadata['stability_cv_threshold_pct']:.2f}% with {scaled_mad_mspt:.3f} MSPT scaled MAD"
            )

    end_median = statistics.median(by_scenario["empty_end"])
    drift_pct = 100.0 * (end_median / baseline - 1.0) if baseline else math.inf
    drift_mspt = end_median - baseline
    if (abs(drift_pct) > metadata["empty_drift_threshold_pct"]
            and abs(drift_mspt) > metadata.get("absolute_noise_floor_mspt", 0.1)):
        warnings.append(
            f"empty baseline drift {drift_pct:+.2f}% ({drift_mspt:+.3f} MSPT) exceeds thresholds"
        )

    report = {
        "schema_version": 1,
        "metadata": metadata,
        "empty_baseline_drift_pct": round_number(drift_pct),
        "empty_baseline_drift_mspt": round_number(drift_mspt),
        "stable": not warnings,
        "warnings": warnings,
        "scenarios": summaries,
    }
    (args.output / "summary.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    with (args.output / "results.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=("scenario", "sample", "ticks", "tps", "mspt"))
        writer.writeheader()
        writer.writerows(rows)

    lines = [
        "# Magnetization performance stress report",
        "",
        f"Profile: `{metadata['profile']}`; grid: {metadata['grid_size']}x{metadata['grid_size']}; "
        f"samples: {metadata['samples_per_scenario']} x {metadata['sample_ticks']} ticks.",
        "",
        "| Scenario | Median MSPT | Delta vs empty | Relative | CV | Robust CV |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    for row in summaries:
        relative = row["relative_vs_empty_pct"]
        lines.append(
            f"| `{row['scenario']}` | {row['median_mspt']:.3f} | {row['delta_vs_empty_mspt']:+.3f} | "
            f"{relative:+.2f}% | {row['cv_pct']:.2f}% | {row['robust_cv_pct']:.2f}% |"
        )
    lines.extend(("", f"Empty baseline drift: {drift_pct:+.2f}% ({drift_mspt:+.3f} MSPT).", ""))
    if warnings:
        lines.extend(("Warnings:", ""))
        lines.extend(f"- {warning}" for warning in warnings)
        lines.append("")
    (args.output / "summary.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"performance-stress: analyzed {len(rows)} samples; stable={not warnings}")


if __name__ == "__main__":
    main()
