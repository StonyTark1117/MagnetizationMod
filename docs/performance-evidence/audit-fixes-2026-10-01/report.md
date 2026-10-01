# Performance audit fixes — October 1, 2026

All seven audit items have implementation or scaling coverage. The matched 30-scenario comparison completed 150 samples on each build and found no regression exceeding both 10% and 0.1 ms/tick. The largest measured gains are native point queries and larger shaft populations. Active native and dense external field workloads also improved. Gas and fluid timing changes are small, but JFR confirms substantially less allocation in both paths.

This continues the [original audit](../audit-2026-10-01/report.md). Crash causality is outside scope. Version remains unreleased 1.4.6; source revisions and artifact hashes distinguish builds.

## Matched timing

Baseline production code is `1d479047812b`. Candidate is `70a71579d208`, with implementation commits `59c4f926` and `3e9ca114`. The baseline checkout has only the same opt-in fixture/harness extensions, so both variants execute identical workloads. Their fixture file hashes are recorded in `validation.json`.

Both runs used NeoForge 21.1.252, Java 21.0.12.1+1, the same configuration hash, 64 instances per normal grid, 2,000 global warmup ticks, 400 warmup ticks per scenario, and five 1,200-tick samples per scenario. JFR and performance diagnostics were disabled. Each run used a new disposable flat world. These are whole-server sprint timings in the local compatibility pack, not mod-only CPU times or production multiplayer measurements.

| Workload | Baseline median ms/tick | Optimized median ms/tick | Difference |
|---|---:|---:|---:|
| Item/block control | 3.22 | 3.21 | −0.01 |
| 64 active native electromagnets and items | 6.46 | 5.60 | −0.86 (13.3%) |
| 64 CNA magnets and items | 3.70 | 3.41 | −0.29 (7.8%) |
| 1,024 dense external magnets and items | 5.25 | 4.54 | −0.71 (13.5%) |
| Native emitter, zero synthetic queries | 0.13 | 0.13 | 0.00 |
| Native emitter, 64 synthetic queries/tick | 1.71 | 0.16 | −1.55 (90.6%) |
| Native emitter, 256 synthetic queries/tick | 6.29 | 0.20 | −6.09 (96.8%) |
| Gas volume with one-cell churn | 1.53 | 1.48 | −0.05 |
| Same gas volume, stable topology | 0.13 | 0.13 | 0.00 |
| Ferrofluid pool near external magnets | 0.42 | 0.33 | −0.09 |
| 16 isolated shaft networks | 0.16 | 0.17 | +0.01 |
| 64 isolated shaft networks | 0.23 | 0.21 | −0.02 |
| 256 isolated shaft networks | 0.88 | 0.39 | −0.49 (55.7%) |
| 4 moving ships with shaft/docking fixtures | 0.85 | 0.71 | −0.14 (16.5%) |
| 16 moving ships with shaft/docking fixtures | 2.63 | 2.11 | −0.52 (19.8%) |
| Empty start / end | 0.15 / 0.14 | 0.15 / 0.14 | Same medians |

Both runs passed the harness's robust stability check. The shared interactive desktop had other pre-existing CPU activity; isolated high samples are retained in the CSVs. These results support median workload comparisons, not tail-latency claims. Differences below the 0.1-ms absolute noise floor—including gas, fluid timing, ordinary players and the smaller shaft cases—should not be promoted into percentage improvement claims.

Native recipients call the real shared `MagneticFields.isInField` path but are synthetic queries, not 256 complete player/golem ticks. Shaft counts are networks, each containing a motor and two magnetic shafts. Activation checks require every static source/receiver to be active and every expected ship/shaft to exist. Moving fixtures repeatedly translate and rotate receiver ships through range boundaries with real bound anchors and linked switches; timings include harness teleport overhead. They do not model large ships or arbitrary multiplayer builds.

Full results: `standard-comparison.tsv`, `baseline-standard-summary.json`, `candidate-standard-summary.json`, and both `*-standard-samples.csv` files.

## Allocation and workload evidence

A separate matched quick-profile run used 16 instances per normal grid, 1,000 global warmup ticks, 300 scenario warmup ticks and three 600-tick measured samples per scenario, with JFR and diagnostics enabled. Dense magnets remain 1,024; the fluid pool contains 512 sources. Attribution includes only server-thread events during the measured sprint intervals. Setup, warmup and startup are excluded. These profiled timings are not mixed with the standard timing table above.

| Mod-associated allocation-sample weight over 1,800 measured ticks | Baseline | Optimized |
|---|---:|---:|
| Active native field scenario | 404 MB | 97 MB |
| Dense external fields | 4,034 MB | 1,107 MB |
| Gas churn, all mod frames | 304 MB | 84 MB |
| External-field fluid pool | 235 MB | 80 MB |
| 256 native point queries/tick | 45,414 MB | 306 MB |
| 256 shaft networks | 436 MB | 461 MB |
| 16 moving ships/docks | 3,196 MB | 403 MB |

These are weighted statistical allocation estimates, not exact allocated bytes, retained memory, or evidence of a heap leak. Inclusive method totals overlap and must not be summed. The short fluid scenario has only 16/10 server-thread execution samples before/after, so it cannot support precise CPU attribution.

- In dense fields, `commonClientValue` accounted for about 2,906 MB of the baseline allocation weight and had no attributed allocation samples in the optimized run. Total mod-associated weight fell about 73%.
- `GasExcitation.recompute` weight fell from 292 MB to 84 MB (about 71%). This supports buffer reuse even though its standard timing improvement is below the noise floor. Stable gas stayed near the empty baseline.
- Fluid target gathering fell from about 54.5 MB to 4.2 MB; `anchorsNear` had about 50.3 MB before and no attributed allocation samples afterward. Lazy iteration still creates small iterator objects; this is not a zero-allocation claim.
- Shaft allocation did not improve in this recording, despite the clear timing gain. The per-pass spatial index adds temporary structures and intentionally avoids persistent topology/pose caching. Further reduction would require separate evidence and invalidation coverage.

Activation counters match across both builds: the dense fixture discovers/resolves 16 targets and applies 256 external fields per tick from 1,024 candidates. The fluid fixture still counts all 512 sources, four target regions and 16 field applications per tick. The new native-query counter records 256 occupied buckets for 256 queries per tick (one bucket/query), replacing the old source-confirmed 4,225 coordinate probes per query. The baseline did not have that new counter; its zero/missing field is not a zero-work claim.

Evidence is in both `*-jfr-attribution.json`, `*-profiled-summary.json` and `*-profiled-work-counts.tsv` files. Raw recordings and logs remain in ignored build reports; the analyzer is `scripts/analyze-performance-jfr.py`.

## Changes and gameplay constraints

1. **Config access:** dedicated-server/no-snapshot reads avoid the client override lock and key construction. Connected-client snapshots retain synchronization and cache immutable keys only. Snapshot replacement, empty snapshots, local reload under a remote override, and disconnect restoration are covered.
2. **Optional entities:** reject unrelated Slugterra types and absent integration before feature config work. Relevant projectiles/mounts still read live movement, transformation and configuration.
3. **External adapters:** cache block-to-adapter identity and share one live enable check within a scheduler candidate. Public entry points still validate enablement. Power, energy, CNA pulses, block replacement, attempt budgets and rotating cursors stay live.
4. **Fluid discovery:** cache immutable source count/chunk/section metadata until membership changes; lazily iterate anchor buckets. Original discovery/anchor order, negative boundaries, chunk coverage and writable-map invalidation are tested. No force or creep frequency changes.
5. **Point queries:** visit occupied rows/buckets instead of probing a 65×65 square of potentially empty chunk positions. Randomized reference tests compare exact membership, iteration order, inclusive bounds, removal and external caps against the old expanded scan.
6. **Gas:** reuse traversal capacity with a separate buffer for reentrant calls and cleanup in `finally`. No cross-tick topology, power or excitation cache was added. Existing cap, loaded-boundary, same-tick invalidation, grace, ownership and energy logic is retained.
7. **Shafts/docking:** rebuild a spatial source index each manager pass, preserve original sorted source order and retain the exact distance test. Physical topology and ship poses are not cached across passes. The immediate local-motor check, source conflicts, reversals, four-tick cadence and ticket behavior remain intact. Scaling now covers increasing isolated networks and multiple moving ships.

## Full-pack deployment and live comparison

The verified optimized JAR is installed on Discopanel in the original `world`. Its SHA256 is `978c9673c6c775d5d41a33c462eb65f7ebad82b4387910b97b674afc3b3ad9e1`; it remains version 1.4.6 at code revision `70a71579d208`. Upload was verified by downloading and hashing the installed file. The prior audited JAR (`65d1960e632ee7e246ef2bf926b43aead326060b4a29b2ec3839d4fc6d33d6c1`) is preserved locally for rollback.

All captures used the same 4-ms async execution sampling interval, original world and settings, zero players, 49 entities, 846 chunks, 1,746 indexed emitters and five ships. The candidate was given at least two minutes of startup warmup. The baseline had been running longer, so startup age is not matched.

| Capture | Duration / ticks | Sampled mod ms/tick | Server median ms/tick across windows | Spark |
|---|---|---:|---:|---|
| Audited baseline | 180 s / 3,600 | 0.080 | 6.738–6.965 | [Baseline](https://spark.lucko.me/QLgYsIj07E) |
| Optimized, first | 180 s / 3,600 | 0.376 | 7.167–7.215 | [First](https://spark.lucko.me/AyLUPKlHQ0) |
| Optimized, later | 180 s / 3,600 | 0.360 | 7.126–7.150 | [Later](https://spark.lucko.me/eH2av1D3ap) |

The server maintained approximately 20 TPS in every capture. **This is not a verified live-idle improvement.** Each optimized capture had two low-cost mod windows (0.023–0.070 ms/tick) and one elevated window (0.977–1.043 ms/tick), mostly under external-emitter scheduling; the baseline's windows were 0.067–0.093. The later capture did not eliminate this variation. Startup age and sampling variation prevent assigning a reliable regression factor, but the higher optimized averages are retained as an unresolved live-idle finding. A matched-restart A/B experiment would be needed to separate it from initialization/JIT or intermittent full-pack work. The original audit also observed wide variation on unchanged baseline code.

Single-tick maxima were 138 ms in the baseline, 184 ms in the first candidate capture and 371 ms in the later one. These isolated events do not establish a tail-latency improvement or crash causality. No error lines mentioning Magnetization appeared in the captured optimized startup log; existing unrelated pack recipe errors remain outside this work.

Profiling has completed. No fixture datapack, diagnostic JVM flags or manual configuration changes were installed on the live server. This verifies idle full-pack operation, not peak multiplayer activity or a full-pack load test. Raw profiles, startup logs, validation logs, the rollback artifact and archived baseline benchmark runs are retained under `build/reports/performance-fixes-2026-10-01/`; compact measurements are in `live-summary.json`.

## Validation status

Passed: 292 unit tests; 173 core GameTests; nine Slugterra runtime tests; 20 Create: New Age tests; seven engineering GameTests, including a new moving-shaft range exit/reentry test; and four engineering regression tests. The engineering suite also covers local drive priority, reversals, conflicts, shared load, live range settings, chunk unload/reload and docking motion. These are regression evidence, not a claim that every possible modpack interaction is exhaustively covered.

The additional adapter matrix passed: Create Addition 7/7, Immersive Engineering 10/10, Alex’s Caves 7/7, Ender Transmission 3/3 and TFMG 20/20. `./gradlew build verifyReleaseJar` passed, including release contents and repository checks. Core and Slugterra gates were also rerun successfully against the final code. The matched timings, allocation evidence, installed artifact and live captures are recorded above. The live-idle finding remains a limitation of the result, not an omitted measurement.
