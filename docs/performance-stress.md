# Reproducible performance stress tests

The performance harness measures dedicated-server tick cost without adding benchmark-only code to the shipped mod. It starts a fresh flat world with a broad server-safe Create compatibility pack, installs a generated datapack in that disposable world, and measures each scenario with Minecraft's built-in `tick sprint` command. The profile deliberately omits world-pregeneration tools and the Aeroportals/Immersive Aeronautics test stack: those perform unrelated background work or bundle Immersive Portals code that logs a third-party dedicated-server error every tick and would contaminate the result.

## Run a benchmark

An accepted development-run EULA is required at `run/eula.txt`. The harness copies it but never accepts the EULA itself.

```bash
./gradlew performanceStress
```

The default `standard` profile places 64 instances per grid, performs a 2,000-tick JVM/server warmup, warms each scenario for 400 ticks, and records five independent 1,200-tick samples. Reports are written to `build/reports/performance-stress/<timestamp>-<revision>-<profile>/`:

- `summary.md`: readable scenario table and stability warnings
- `summary.json`: machine-readable metadata and aggregate statistics
- `results.csv`: individual measurements
- `raw-samples.tsv`: acquisition record
- `latest.log`, `gradle.log`, and `stress-manifest.json`: diagnostic evidence

The disposable world is removed after the run. Set `MAG_STRESS_KEEP_WORLD=1` to retain it for inspection.

## Profiles and overrides

```bash
MAG_STRESS_PROFILE=quick ./gradlew performanceStress
MAG_STRESS_PROFILE=full ./gradlew performanceStress
MAG_STRESS_GRID=12 MAG_STRESS_SAMPLES=7 ./gradlew performanceStress
```

| Profile | Grid | Instances | Global warmup | Scenario warmup | Samples | Ticks/sample |
|---|---:|---:|---:|---:|---:|---:|
| `quick` | 4x4 | 16 | 1,000 | 300 | 3 | 600 |
| `standard` | 8x8 | 64 | 2,000 | 400 | 5 | 1,200 |
| `full` | 16x16 | 256 | 4,000 | 800 | 7 | 2,400 |

Available overrides are `MAG_STRESS_GRID`, `MAG_STRESS_GLOBAL_WARMUP_TICKS`, `MAG_STRESS_WARMUP_TICKS`, `MAG_STRESS_SAMPLE_TICKS`, `MAG_STRESS_SAMPLES`, `MAG_STRESS_TIMEOUT_SECONDS`, `MAG_STRESS_SHUTDOWN_TIMEOUT_SECONDS`, `MAG_STRESS_CV_THRESHOLD_PCT`, `MAG_STRESS_DRIFT_THRESHOLD_PCT`, and `MAG_STRESS_ABSOLUTE_NOISE_FLOOR_MSPT`.

`MAG_STRESS_SCENARIOS` accepts a comma-separated subset, but it must include `empty_start` and `empty_end`. The fixed default order is intentional: it detects run-long JIT, thermal, or host-load drift.

## Scenarios

| Scenario | Load isolated |
|---|---|
| `empty_start` | Initial forced-chunk baseline |
| `block_item_control` | Inert blocks plus the same glass-confined item-entity count used by field scenarios |
| `idle_emitters` | Magnetization electromagnet block entities and confined magnetic items, without redstone |
| `active_emitters` | Redstone-powered electromagnets and confined magnetic items |
| `external_fields` | Create: New Age magnetite emitters and confined magnetic items |
| `railgun_emitters` | Powered Railgun emitter block entities and confined magnetic items |
| `air_separators` | Idle Air Separator block entities |
| `gas_volume` | A sealed noble-gas volume with deterministic one-cell churn each tick, exercising bounded network recomputation |
| `mixed_pack` | A deterministic blend of representative blocks and item targets |
| `empty_end` | Final empty baseline used to quantify drift |

Every scenario clears the same volume, removes tagged test entities, reuses the same forced chunks, disables random ticks and autosaves during measurement, and runs in the same server process. These choices minimize world generation, disk I/O, and JVM-startup variance while keeping the measured game code realistic.

## Compare revisions

Run the same profile and overrides on both revisions, then compare their JSON summaries:

```bash
python3 scripts/compare-performance-stress.py \
  baseline/summary.json candidate/summary.json
```

The default regression thresholds are 10 percent and the report's absolute noise floor (normally 0.1 MSPT); both must be exceeded. Use `--threshold-pct 5` or `--absolute-threshold-mspt 0.2` to change them and `--fail-on-regression` when a nonzero exit is useful in automation. The comparer refuses reports whose profile, grid, sample length, sample count, or scenario list differs.

Treat a result as actionable only when the summary is stable. The report retains ordinary coefficient of variation (CV), but stability warnings use robust CV derived from median absolute deviation so one transient sample does not hide agreement among the others. By default, robust CV above 10 percent or empty-baseline drift beyond plus or minus 10 percent produces a warning when the scaled median absolute deviation also exceeds the 0.1 MSPT noise floor. Repeat noisy runs after removing competing CPU, memory, and I/O load. Compare on the same machine, Java version, profile, and scenario parameters; the report embeds those values so mismatches are visible.
