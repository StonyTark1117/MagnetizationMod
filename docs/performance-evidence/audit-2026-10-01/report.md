# Magnetization performance audit October 1 2026

The current source retains the earlier low-cost behavior for ordinary players and external magnets with no eligible targets. The clearest remaining optimization target is allocation and repeated configuration work in active field paths, amplified by optional Slugterra checks on ordinary entities. Gas-network recomputation also has measurable cost under continuous topology changes. Shaft-network scaling and native point-query searches deserve targeted follow-up, but this audit does not establish them as current live-server bottlenecks.

This is an audit of version **1.4.6 at source commit `1d479047812b`**, after the field-performance, shaft/docking, native-integration, Slugterra, and stress-test merges. No gameplay or optimization code was changed for this audit. The separate crash investigation remains outside scope.

## Test scope and identity

- Current artifact SHA256: `65d1960e632ee7e246ef2bf926b43aead326060b4a29b2ec3839d4fc6d33d6c1`.
- Discopanel initially ran the older field-performance artifact, SHA256 `63acc527a8dc674b3674279350f8f66e1e88046d227e0fd6a05c22d65424873f`. Both identify as 1.4.6; the version string alone cannot distinguish them.
- The Gradle gate verified current, up-to-date passing results for **286 unit tests**, and the build and release-JAR checks passed before deployment. The unit-test task did not rerun unchanged tests. This audit did not rerun the previous full optional-mod GameTest matrix.
- Local timing: 21 scenarios, 64 instances per normal grid, five independent 1,200-tick samples per scenario, 2,000 global warmup ticks and 400 scenario warmup ticks; no JFR or performance diagnostics during timing. The dense fixture has 1,024 external magnets; fluid fixtures have 2,048 sources.
- Separate JFR attribution: eight scenarios, 16 instances per normal grid, three 600-tick samples, 1,000 global and 300 scenario warmup ticks, with diagnostics. Dense magnets remain 1,024; fluid sources are 512. These timings must not be compared directly with the unprofiled standard run.
- Both local runs used NeoForge 21.1.252 and Java **21.0.12.1+1**. The harness metadata incorrectly reports the `PATH` Java 27 and marks itself dirty because its disposable runtime directory exists before the status check. Tracked source was clean; both source hashes are `cd8f85de26ed0e69a3d68c8843595f21905f0fe33f4a07c2d89de3d4e5d6db1f`. See `validation.json`.

## Live full-pack measurements

Discopanel remained on the original world and panel settings; no manual configuration changes were made. Every capture had zero players, 49 entities, 846 loaded chunks, 1,746 indexed block emitters and five Sable ships. All used the same 4-ms execution-sampling interval. The newly built JAR was installed after the build checks, verified by readback, and given two minutes of startup warmup before the first capture. The older reference had been running much longer; warmup age is therefore a comparison limitation.

| Capture | Duration and ticks | Sampled mod ms per tick | Server median ms per tick | Spark |
|---|---|---:|---:|---|
| Older deployed field-performance build | 60 s / 1,200 | 0.263 | 7.117 | [Reference](https://spark.lucko.me/CX8q5oSLhQ) |
| Current build first capture | 60 s / 1,200 | 0.983 | 7.015 | [Current](https://spark.lucko.me/It4WLEh7EM) |
| Current build repeat | 60 s / 1,200 | 1.370 | 7.004 | [Repeat](https://spark.lucko.me/bIudAW2XWI) |
| Current build later longer capture | 194.8 s / 3,896 | 0.074 | 6.891–6.930 across reporting windows | [Longer capture](https://spark.lucko.me/kmHVKOTsOL) |

The server sustained approximately 20 TPS. Current-build captures had individual tick maxima of 50.9–62.1 ms; the older reference maximum was 178.0 ms. These small sets of maxima do not establish a tail-latency improvement. The long capture has multiple window medians; their range is shown instead of inventing an aggregate median.

The first two current captures attributed most mod time to `ExternalEmitterTracker` (0.830 / 1.203 ms per tick), but the later capture attributed only 0.025 ms per tick to it. Thus the initial apparent mod-cost increase was not sustained. Neither a stable regression factor nor an improvement percentage is justified by these samples. The controlled active-field allocation evidence is stronger for prioritizing additional work. Sampled mod totals count outermost mod frames once and exclude server sleep as mod work.

Collection note: the long profile required an explicit stop because this Spark version interpreted the help invocation as starting a profiler. The attempted timed start found that profiler already running. An initially retrieved duplicate of the previous report was rejected by checksum and timestamps; only the actual 194.8-second recording is included above. Profiling is stopped.

The server is left running the current artifact on the original world. No test datapack, fixture mode, or diagnostic JVM flags were installed on Discopanel during this audit. The old JAR is preserved locally for rollback. Compact measurements are in `live-summary.json`; raw Spark files, server logs and the rollback artifact remain under `build/reports/performance-audit-2026-10-01/`.

## Controlled timing results

These are whole-server tick-sprint measurements in a disposable local compatibility pack, not mod-only CPU timers or full-pack live-server timings. Matched item controls matter: confined item entities and surrounding blocks already cost 3.73 ms/tick in this fixture.

| Scenario | Median ms per tick | Interpretation |
|---|---:|---|
| Empty start / end | 0.14 / 0.11 | Absolute drift −0.03 ms |
| Inert blocks and confined item control | 3.73 | Control for item-bearing field grids |
| 64 unpowered electromagnets and items | 3.79 | +0.06 versus control, below 0.1-ms noise floor |
| 64 powered electromagnets and items | 6.79 | +3.06 versus control; largest measured grid cost |
| 64 CNA magnets and items | 3.94 | +0.21 versus control |
| 1,024 dense external magnets and items | 5.62 | +1.89 versus control; 256-application budget retained |
| 64 powered railgun emitters and items | 4.00 | +0.27 versus control; no firing-throughput claim |
| 64 idle air separators | 0.15 | +0.01 versus initial empty |
| Gas volume with one-cell churn every tick | 1.63 | +1.49 versus initial empty |
| Mixed block and item fixture | 3.94 | +0.21 versus control; different block mix |
| Ordinary unequipped synthetic player | 0.12 | No substantial measured inactive-equipment cost |
| 64 ordinary mobs near magnets | 0.27 | Includes the mobs themselves; no mob-only subtraction |
| External magnets without eligible targets | 0.11 | Near empty baseline |
| Enclosed plain ferrofluid pool | 0.15 | +0.01 versus initial empty |
| Pool near external magnets | 0.42 | +0.28 versus initial empty |
| Pool near a native electromagnet | 0.18 | +0.04 versus initial empty |
| MR armor / main hand / off hand | 0.12 / 0.13 / 0.12 | Synthetic handler fixture, not connected-player load |
| Equipment cycling | 0.14 | Near empty baseline |

All 105 samples completed; the harness reports stable results using its robust-variation test and 0.1-ms absolute noise floor. Ordinary CV is high for several very cheap cases due to isolated spikes. The relative empty drift is −21.43%, but its absolute size is only −0.03 ms; avoid percentage claims for these tiny baselines. Full samples are in `standard-samples.csv` and all scenario statistics in `standard-summary.json`.

## Prioritized opportunities

### 1 Remove dedicated-server client-override lookup overhead

**High confidence, highest priority.** `MagConfig.commonClientValue` locks `CLIENT_SYNC_LOCK`, obtains a path list, joins it into a string, and looks in a client-override map on every tolerant config read. Dedicated servers have no connected-client override state to consult. This work is multiplied across field, entity, and adapter checks.

In the dense-field JFR sample intervals, this method appeared in **58 of 293 server-thread execution samples** and in approximately **3.074 GB of allocation-sample weight**, about **74.6% of the 4.122 GB associated with any Magnetization frame**. These are weighted statistical allocation estimates over 1,800 accelerated ticks, not exact allocated bytes, retained memory, or a heap leak. Active electromagnets likewise attributed about 81% of mod-associated allocation weight to this method. Whole-recording allocation classes include byte arrays, String arrays, and ArrayLists.

Proposed change: bypass override-key construction when overrides cannot apply, and cache immutable config keys where a lookup is needed. Keep client/server separation, remote override synchronization, reload behavior, and early-load fallback semantics. [Source](../../../src/main/java/com/stonytark/magnetization/config/MagConfig.java#L2941).

### 2 Reject unrelated optional-mod entities before repeated config reads

**High confidence under active fields.** Slugterra is absent from the local test runtime, yet its config getters and compatibility methods appear in dense-field CPU and allocation samples. `SlugterraMountCompat.susceptibility` checks configuration before checking whether Slugterra is loaded; `SlugterraProjectileCompat.handles` checks configuration before the entity type. Force application invokes these for ordinary targets as well as relevant entities.

Dense-field inclusive samples include 21 with `slugterraCompatEnabled`, 15 with `slugterraDeflectionEnabled`, and 13 with `slugterraMountsEnabled`. These overlap with opportunity 1; savings must not be added together. Proposed change: cheap mod-presence/entity-type rejection first, and reuse per-pass feature eligibility without caching live projectile movement or transformation state. [Mount source](../../../src/main/java/com/stonytark/magnetization/compat/SlugterraMountCompat.java#L20), [projectile source](../../../src/main/java/com/stonytark/magnetization/compat/SlugterraProjectileCompat.java#L28), [force path](../../../src/main/java/com/stonytark/magnetization/physics/FieldApplicator.java#L596).

### 3 Avoid repeated external-emitter classification and support checks

**Source-confirmed repetition, supported by dense-field samples.** `applyBudgeted` calls `isIndexableEmitter`, then `isSupportedEmitter`, then `currentField`, which checks support again. The first call already delegates to the same support check for ordinary external magnets. Registry lookups and config reads repeat before a single field is produced.

Proposed change: resolve immutable adapter identity once and share one live enable decision through the internal evaluation path. Preserve public API validation, runtime config changes, block replacement, and the rotating attempt budget. Do not cache live power/energy or change the CNA pulse/redstone semantics. Redstone helpers themselves also appeared in 27 dense-field samples, so adapter cost will remain after config cleanup. [Scheduler](../../../src/main/java/com/stonytark/magnetization/compat/ExternalEmitterTracker.java#L183), [adapter](../../../src/main/java/com/stonytark/magnetization/compat/ExternalFieldCompat.java#L62).

### 4 Reduce repeated fluid discovery and temporary anchor lists

**Measured allocation, moderate priority.** A 512-source pool still produces 512 discoveries per tick before collapsing to four target regions. In this fixture, JFR attributed about 54.5 MB of inclusive allocation weight to external target gathering and 67.1 MB to `FerrofluidCreepHandler.anchorsNear` during measured intervals. CPU attribution is sparse: only 19 server-thread execution samples in the fluid scenario, so precise timing claims would be unjustified.

Proposed change: maintain counts/bounds per occupied fluid region, and iterate nearby anchor buckets without copying a new list for every magnet. Preserve the exact current horizontal search bounds, full discovery coverage, lifecycle updates, and force/creep order. [Discovery](../../../src/main/java/com/stonytark/magnetization/compat/ExternalEmitterTracker.java#L132), [anchor lists](../../../src/main/java/com/stonytark/magnetization/content/fluid/FerrofluidCreepHandler.java#L115).

### 5 Extend occupied-bucket queries to point-field searches

**Source-confirmed scaling risk, not a measured bottleneck in these fixtures.** The earlier ferrofluid discovery fix does not cover `MagneticFields.candidatesNear`. When any native emitter exists in a dimension, `snapshotNativeNear(..., 512)` still probes a 65×65 square—**4,225 chunk positions per query**. It affects MR/golem/gallium queries and anchor peer searches. The MR benchmark here uses external magnets and does not exercise the native-present worst case.

Proposed change: reuse the occupied-row index with equivalent inclusive bounds and candidate semantics. Add a targeted fixture with native emitters and many simultaneous query recipients before claiming a gain. [Point query](../../../src/main/java/com/stonytark/magnetization/physics/MagneticFields.java#L157), [registry query](../../../src/main/java/com/stonytark/magnetization/physics/EmitterRegistry.java#L266).

### 6 Profile persistent gas-component caching under realistic mutations

**Measured workload, design requires care.** The gas churn fixture costs 1.63 ms/tick at standard size. JFR found `GasExcitation.recompute` in 17 of 35 server-thread execution samples in the smaller profiled case, with about 295 MB of associated allocation weight. Current caching deduplicates work within a tick, but still allocates traversal structures and revisits components across ticks.

Proposed follow-up: measure stable networks separately from continuous mutations, then consider reusable traversal buffers or component state invalidated by topology, power, and exciter changes. Preserve grace decay, same-tick merges/splits, ownership, loaded-chunk boundaries, and energy consumption. The churn fixture is deliberately adverse and does not represent every gas build. [Source](../../../src/main/java/com/stonytark/magnetization/content/fluid/GasExcitation.java#L53).

### 7 Add scaling coverage for magnetic shafts and docking

**Source-only concern, unmeasured here.** `MagneticShaftNetwork.update` rediscovers physical components every four ticks and pairs receiver shafts against all loaded shafts. `connectionRatio` can also discover the receiver component during kinetic propagation. Pairing can approach quadratic work as shaft populations grow, and component traversal can be large. None of the current timing scenarios places shafts or exercises docking motion.

Proposed follow-up: benchmark increasing isolated networks and multiple moving ships, then consider spatial shaft selection and topology-driven component reuse. Preserve immediate local-motor precedence, directional links, conflicts, RPM reversal handling, and chunk-ticket expiry. [Source](../../../src/main/java/com/stonytark/magnetization/content/shaft/MagneticShaftNetwork.java#L123).

## Preserved workload reductions

The profiled dense fixture still discovers 16 eligible entities once per tick, resolves 16 target-detail records, and applies exactly 256 external fields per tick from 1,024 candidates. The 512-source fluid fixture collapses discovery into four scheduled regions, and native/external ferrofluid discovery retains the occupied-bucket path. These counters support that the prior optimizations remain active; source discovery and repeated per-force config checks still leave additional work to remove.

## Measurement limits

JFR analysis uses only server-thread events inside the three measured sprint intervals per scenario; setup, warmup, and startup are excluded. Inclusive method counts overlap and must not be summed. Short scenarios have few execution samples. The profiled run is for attribution; the unprofiled standard run supplies timing. The live full-pack captures are idle scenes with no connected players, so this audit does not certify peak multiplayer load, large ship fleets, shaft factories, projectile battles, client FPS, or allocation retention. No current measurement establishes crash causality.

The first follow-up should combine the dedicated-server config fast path with early optional-mod rejection, then repeat the same unprofiled standard benchmark and dense-field allocation recording before proceeding to more complex caches.
