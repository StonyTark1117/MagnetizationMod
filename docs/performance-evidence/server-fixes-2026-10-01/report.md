# Full-pack stress optimizations — 2026-10-01

**Gas churn improves by 21.4% in matched sprint medians, and the bulk-fluid peak
falls 38.8%, from 15.26 to 9.33 seconds. The bulk hitch remains severe.** Coaster
classification is bypassed when reactions are enabled; the final measurements
do not demonstrate overall dense-field, Lenz-scan or shaft speedups.

Completed on the updated full pack: 30 final-candidate stress scenarios / 150
samples, matched baseline measurements, normal-speed profiles, and 37 immediate
fluid assertions. All 293 unit tests and 264 GameTest cases passed in their
recorded profiles. The original world and server settings are restored with the
tested Magnetization build and Slugterra 1.0 installed.

## Scope and build identity

This work addresses all six findings in the [preceding stress audit](../server-stress-2026-10-01/report.md).
It concerns added workload and gameplay correctness; it makes no claim about the
cause of the historical crash.

The isolated branch is `codex/fullpack-stress-fixes-2026-10-01`. Production source
is `8217baf4586d4e594967283cf483373381af63d4`. Magnetization still reports version
1.4.6, so use the JAR hash to distinguish this build from previous 1.4.6 artifacts:

| Build | SHA-256 |
|---|---|
| Original baseline | `978c9673c6c775d5d41a33c462eb65f7ebad82b4387910b97b674afc3b3ad9e1` |
| First candidate, retained for evidence | `77e574774c78c047353c2a181e797d1e2dcae45f64aafaacb921a7c76b4633e6` |
| Final candidate | `b81e113f59ca87afd0f0517ef7c2bcd49a036c4acd03b377de601bca52f0c60a` |

## Implemented changes

1. **Conductive fluid redstone:** indexed primitive graph traversal, combined
   neighbor/input discovery, queued attenuation solver, and reusable traversal
   buffers reduce repeated reads and allocation. Each call starts with fresh
   topology, block states and power. Recomputations remain synchronous with the
   original 4,096-cell cap, breadth-first cell order, writes and notifications.
   No updates are deferred to another tick.
2. **Gas recomputation:** read native block state first, avoid block-entity access
   for states without one, and reuse gas identity reads within one traversal.
   Reentrant work has separate scratch storage; cached contents clear afterward.
   Topology invalidation, power checks, energy use, grace and caps remain intact.
3. **Dense fields:** classify projectile type once in the existing per-tick
   target snapshot. Ordinary targets avoid repeated Slugterra registry lookups
   for overlapping fields; eligible projectiles retain live configuration and
   flight-state checks. Field budgets, target order and force calculations remain
   intact. The first candidate's neighbor-state cache showed no gain and was
   removed before the final build.
4. **Coaster compatibility:** when magnetic field reactions are enabled, return
   before classifying ship structure. Disabled reactions retain classification.
5. **Lenz braking:** classify each immutable block state once per conductor scan.
   Every position, loaded check, scan order and the original 2,048-position cap
   remain intact; classification refreshes on the next scan.
6. **Shaft networks:** collect each registered shaft with one loaded lookup,
   preserving sorted order, unloaded entries, replacement removal, live network
   discovery and the four-tick cadence. Other mods' kinetic hooks still run.

## Slugterra dependency correction

The server originally had `slugterra-1.3.4+1.21.1-unofficial.1.jar`, SHA
`b5e6093124b450d6b9aa281aae7d5842a86f748b84c365770ffe3271f7b5dc94`.
Seven broad integration tests failed with that port, and all seven also failed
when the new projectile shortcut was removed. See [control evidence](slugterra-control.json).

At the user's request, the new private [v1.0 release](https://github.com/StonyTark1117/legends-of-slugterra-neoforge/releases/tag/v1.0)
was checked. Its source is `590f1a0257b2f10aa03af40a1ddd86c1b106d498` and verified
JAR SHA is `b2388d688b2902a5931a3c8be44a997fdc87789a192c31a64599b2eda591c736`.
All nine broad integration tests and the focused cached-type/live-flight-state
test pass against this release. It includes the previously missing Dark content.
Clients need the same Slugterra build. Its metadata also requires JEI
19.57.0.450 or newer within 19.x when JEI is installed on the client. The server
has JEI 19.57.0.447; this dependency is client-only and does not block the server,
but a client using that same older JEI must update it. No original Forge addon
JARs were added.

The dependency changes the pack, so old-port `baseline`/`candidate` results are
retained as a separate experiment. Final comparisons pair `baseline-updated`
(original Magnetization) with `candidate-v2` (optimized Magnetization), both
using Slugterra 1.0. The prior seven failures are not attributed to the optimization.

## Method and interpretation

The actual Discopanel full-pack server is used: Minecraft 1.21.1, NeoForge
21.1.252, Java 21, 16 GiB heap, Intel i5-10500 with six visible CPU threads.
A disposable flat world retains the original global and world-specific mod
configuration. The original world is backed up and unloaded during tests.

The matched sequence has ten scenarios, each with five 1,200-tick sprint samples,
a 2,000-tick initial warmup and 400-tick scenario warmup. Sprint medians are
medians of sample-average tick costs, not individual tick latency percentiles.
The final candidate additionally exercises the remaining twenty scenarios from
the original thirty-scenario stress matrix.

Four separate 180-second Spark captures run at normal 20-TPS pacing after
60-second warmups: 1,024 dense external magnets with 64 item targets, a 2,048-cell
changing gas volume, 256 shaft networks, and 16 moving Sable ships. A separate
120-second capture includes bulk setup of 2,048 conductive fluid blocks.
Spark sampling uses a 4 ms interval. Attribution excludes performance fixture
classes. Inclusive child methods overlap and must not be added to their parents.
Bulk capture averages must not be presented as steady-state costs.

These are stress tests with active entities, topology changes, networks and
moving ships, despite zero human players. Fake-player fixtures are not networked
clients. Moving-ship measurements include fixture teleport overhead. Natural
spawns, random ticks, weather/daylight changes, drops and autosaves are disabled
for controlled measurement. This does not establish survival, terrain-generation,
client/network, long-soak or all-mod compatibility performance.

## Updated-pack performance results

Both columns use Slugterra 1.0 and the same full pack. Percentages describe this
measured pair, not a guaranteed speedup on other hardware or saves. Method costs
are inclusive sampled execution time divided by ticks; rows overlap and must
not be summed. One normal-speed capture per workload/build was collected, so
small differences and individual worst ticks require caution.

| Finding / measured quantity | Baseline | Final candidate | Change |
|---|---:|---:|---:|
| Bulk setup, worst tick (ms) | 15257.956 | 9332.594 | -38.8% |
| Gas recomputation (sampled ms/tick) | 3.796 | 1.919 | -49.4% |
| External tracker (sampled ms/tick) | 1.549 | 1.956 | +26.3% |
| Coaster structure classification (sampled ms/tick) | 0.468 | Not observed in samples | Classification bypassed when enabled |
| Lenz conductor scan (sampled ms/tick) | 0.451 | 0.523 | +16.0% |
| Shaft manager (sampled ms/tick) | 0.597 | 0.607 | +1.7% |

Sprint values below are medians of five 1,200-tick sample averages. Brackets
show the minimum–maximum sample averages, not individual tick percentiles.

| Matched scenario | Baseline ms/tick [range] | Final ms/tick [range] | Change |
|---|---:|---:|---:|
| `empty_start` | 1.78 [1.77–1.86] | 1.90 [1.82–2.18] | +6.7% |
| `block_item_control` | 1.78 [1.76–1.87] | 1.82 [1.74–1.91] | +2.2% |
| `active_emitters` | 2.51 [2.46–2.78] | 2.44 [2.32–2.61] | -2.8% |
| `dense_external` | 3.05 [2.90–3.15] | 3.05 [2.97–3.24] | +0.0% |
| `gas_volume` | 5.23 [5.13–5.32] | 4.11 [4.07–4.24] | -21.4% |
| `gas_stable` | 1.69 [1.60–1.71] | 1.69 [1.66–1.79] | +0.0% |
| `ferrofluid_external` | 2.08 [2.00–2.24] | 2.14 [2.07–2.34] | +2.9% |
| `shafts_256` | 2.76 [2.69–3.07] | 2.90 [2.73–3.10] | +5.1% |
| `ships_16` | 7.10 [6.96–7.19] | 7.03 [6.92–7.19] | -1.0% |
| `empty_end` | 1.70 [1.66–1.88] | 1.74 [1.72–1.98] | +2.4% |

Normal-speed capture details:

| Workload | Baseline minute-median range / worst tick (ms) | Final minute-median range / worst tick (ms) | Baseline / final Spark |
|---|---:|---:|---|
| `dense_external` | 4.10–4.17 / 36.51 | 4.07–4.09 / 33.96 | [baseline](https://spark.lucko.me/74GJs7Toc6) / [final](https://spark.lucko.me/fCzgWxmFXE) |
| `gas_volume` | 5.96–5.97 / 73.84 | 5.46–5.51 / 41.33 | [baseline](https://spark.lucko.me/kSFbBF1prE) / [final](https://spark.lucko.me/aTqJcEfjKl) |
| `shafts_256` | 3.20–3.26 / 44.77 | 3.22–3.26 / 37.55 | [baseline](https://spark.lucko.me/6lVuaRwUmH) / [final](https://spark.lucko.me/stvLu87sgr) |
| `ships_16` | 7.75–7.85 / 90.09 | 7.65–7.70 / 149.24 | [baseline](https://spark.lucko.me/SlHxKXDpjE) / [final](https://spark.lucko.me/ujOSZ8neaF) |

Bulk capture: [baseline](https://spark.lucko.me/fs0vE33gaX) / [final](https://spark.lucko.me/5CrVCl9D5V).

All thirty final-candidate scenarios and their 150 samples are retained in
[sprint-summary.json](sprint-summary.json). The twenty extra scenarios have no
matched updated-pack baseline here and are throughput/activation checks, not
evidence of a before/after speedup. Captures and hashes are indexed in
[profile-manifest.json](profile-manifest.json); per-method costs are in
[hotspots.json](hotspots.json). [analyze_profiles.py](analyze_profiles.py)
recreates summaries from the locally retained decoded captures.

## Remaining bulk-edit limit

The 2,048-cell bulk-placement peak falls from 15,257.96 to 9,332.59 ms (38.8%).
This is still a severe synchronous hitch. Reducing work within each recomputation
does not remove the repeated traversals caused by dense edits. A future design
would need precise invalidation or a defined bulk-update boundary; silently
postponing updates would change the immediate redstone behavior tested here.

Spark's final bulk capture reports 2,215 ticks in metadata and 2,216 when its
window counters are summed. Both values and the one-tick discrepancy are retained
in the manifest; normalization uses metadata (difference below 0.05%). The
120-second duration, start timestamp, file hash and zero-player windows validate.
This count discrepancy does not change the recorded 9,332.59 ms worst tick.

## Interpretation of the targeted paths

The final dense-field sprint median is unchanged at 3.05 ms/tick. Its normal-speed
minute medians are slightly lower, but its sampled external-tracker attribution
is higher. Repeated Slugterra handling checks nearly disappear from samples;
this does not demonstrate an overall dense-field speedup. Scheduling and signal
queries remain substantial costs.

The shaft sprint median is 2.76→2.90 ms/tick with overlapping sample ranges. Normal
minute medians overlap around 3.2–3.3 ms/tick and sampled shaft-manager cost is
0.597→0.607 ms/tick. The earlier candidate on the old port showed a larger sampled
reduction, which the updated-pack run does not reproduce. Retain that evidence,
but do not use it to claim a final shaft speedup. Native kinetic discovery and
other mods' hooks remain active; changing their cadence or bypassing them would
need a separate behavior-preserving design.

Lenz conductor scanning is 0.451→0.523 sampled ms/tick in the final pair; its
older-port gain did not reproduce either. Within that scan, sampled
`FerromagneticCompat.is` cost falls 0.188→0.010 ms/tick; reducing classification
work did not translate to a demonstrated full-scan speedup. Coaster classification,
0.468 ms/tick in the updated baseline, is absent from final samples when reactions
are enabled. Total sampled Magnetization work in the moving-ship fixture falls
1.399→0.882 ms/tick, while whole-tick minute medians improve only modestly. The
candidate also records a 149.24 ms tick versus 90.09 ms in baseline. A single worst
tick does not identify its cause, and this run is not evidence of hitch-free
operation.

## Gameplay validation

Build, release-JAR verification and all 293 unit tests pass. The new solver has
400 differential graph cases covering cycles, multiple inputs and cap edges.
There are 264 passing GameTest cases across core, engineering, MR regressions,
coasters, CNA, IE, Alexs Caves, CreateAddition, TFMG and Slugterra configurations.
[Validation evidence](gameplay-validation.json) identifies which source revision
and dependency each gate exercised; unchanged paths were not all rerun after
every later edit. Those GameTest gates use isolated dependency profiles; the
30-scenario stress matrix and 37 fluid assertions run on the full 387-JAR server.

The final fluid refinement passed all 176 core tests. The focused Slugterra test
checks both projectile movement protocols, turning without speed gain, a live
flight-state change within the same tick and ordinary iron-item attraction.
The new port passes all nine broad Slugterra cases. An initial direct core run
failed an unrelated separator visual check in a terrain world; the repository's
fresh-flat supervisor subsequently passed the complete core suite.

A separate full-pack function checks 37 immediate fluid states without yielding
between source edits and assertions: direct sources, removal, split/rejoin,
multiple sources and indirect lever power. All 37 assertions passed with zero
failures; [server evidence](fullpack-gameplay.json) pins the artifact and function
hashes. No gameplay regression was found in these checked cases; this does not
certify every interaction in the full pack.

## Server restoration and retained artifacts

The original `world` is running again, reporting the same 1,746 block emitters
and five Sable ships as before testing. Before starting it, `world/level.dat`
matched the pre-test backup byte for byte. All original server-property values,
JVM arguments, whitelist and operator entries were restored and checked. The
performance fixture command is disabled. Both installed JARs were read back and
matched their recorded SHA-256 values; the old Slugterra JAR is retained with a
`.disabled` suffix. The original world archive remains
`/codex-before-fullpack-fixes-20261001.zip`.

Evidence: [restoration checks](restoration-validation.json),
[world preservation](world-preservation.json),
[restored server state](restored.json), and
[dependency readback](restored-dependency.json).

The exact tested Magnetization artifact is retained locally as
`build/reports/server-fixes-2026-10-01/candidate-v2.jar`; the Slugterra release is
in that directory's `integration/slugterra-1.0.jar`. The private raw evidence also
retains old JARs, original configuration backups and all decoded Spark captures.
Configuration backups and credentials are not committed. The branch contains
source changes, test additions, the changelog and sanitized audit evidence;
concurrent Ponder/client work in the original checkout is separate.
