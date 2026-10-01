# Full-pack server stress test — 2026-10-01

**The full-pack server handled the sustained test workloads at approximately 20
TPS, but bulk placement reproduced a 16.0-second tick dominated by Magnetization's
ferrofluid redstone-network updates.** This is a substantial remaining workload,
separate from the earlier crash investigation. Steady-state gas recomputation and
external-field processing also remain measurable costs.

Completed: 30 scenarios × five 1,200-tick samples (180,000 measured sprint ticks),
five 180-second steady-state Spark captures (18,000 normal-speed ticks), and one
120-second bulk-placement capture (2,081 ticks). No human players joined.

This audit runs the installed optimized Magnetization 1.4.6 JAR on the actual
Discopanel `Create Magnetized` server with its full 387-JAR modpack. It addresses
the gap in the previous audit: its stress measurements were local, while its
Discopanel measurements were idle only.

## Scope and method

- Minecraft 1.21.1, NeoForge 21.1.252, Temurin Java 21.0.12.1; 16 GiB configured heap.
- Intel Core i5-10500 at 3.10 GHz; Spark reports six visible CPU threads.
- JAR SHA-256: `978c9673c6c775d5d41a33c462eb65f7ebad82b4387910b97b674afc3b3ad9e1`.
- Production code revision: `70a71579d208`; repository at start: `a03e6efc`.
- Disposable flat world, seed 8675309; original world archived before testing.
- Original global config, scripts, mods and world-specific server config retained.
- Original view and simulation distances retained (10 each).
- Only the existing opt-in fixture flag is enabled. Diagnostic counters and JFR
  are disabled during throughput measurements.
- Thirty scenarios, 2,000-tick global warmup, 400-tick scenario warmup, five
  1,200-tick samples per scenario. Tick sprint removes the 20-TPS pacing limit.
- Separate 180-second Spark captures at normal 20-TPS pacing, following a
  60-second workload warmup, measure sustained workload behavior.

Scenario setup uses peaceful difficulty, disables natural mob spawning, random
block ticks, weather/daylight changes and entity/block drops, and force-loads the
fixture area. Autosave is disabled during measurements. These are controlled
workloads, not a recreation of ordinary survival activity.

The fake-player cases invoke selected Magnetization paths; they do not simulate
networked players. Native point-query fixtures call the real field API directly.
Moving ships are real Sable sublevels, repeatedly teleported through shaft range
with rotation and linked docking components; their timing includes fixture
teleport overhead. Shaft and ship activation is checked against live counts.

This run measures the current version against control workloads. It is not a
matched old-JAR/new-JAR comparison. Earlier local speedup percentages cannot be
claimed as measured full-pack server improvements.

## Sprint results

All 150 samples completed on the full-pack server. All scaling activation checks
passed. Every scenario's coefficient of variation was below 10%; empty-control
median drift was −6.7% (1.80 to 1.68 ms/tick). One RCON setup-response failure
interrupted the runner before fluid measurements; the server stayed running.
The runner resumed after an additional 2,000-tick warmup, preserving completed
samples. Large setups then used scheduled functions with completion markers.

Values below are the **median of five sprint-average tick costs**, not per-tick
latency percentiles. Range is the minimum–maximum of those five sample averages.
Each sample measures 1,200 ticks without the 20-TPS pacing delay.

| Scenario | Median ms/tick | Sample-average range |
|---|---:|---:|
| `empty_start` | 1.80 | 1.75–1.96 |
| `block_item_control` | 1.75 | 1.71–1.80 |
| `idle_emitters` | 1.70 | 1.67–2.06 |
| `active_emitters` | 2.47 | 2.43–2.67 |
| `external_fields` | 1.94 | 1.86–2.24 |
| `railgun_emitters` | 1.68 | 1.63–1.82 |
| `air_separators` | 1.66 | 1.59–1.85 |
| `gas_volume` | 5.44 | 5.17–5.54 |
| `mixed_pack` | 2.01 | 1.90–2.05 |
| `dense_external` | 3.03 | 2.95–3.21 |
| `equipment_changes` | 1.67 | 1.59–1.74 |
| `ordinary_player` | 1.60 | 1.57–1.85 |
| `ordinary_mobs` | 1.69 | 1.69–1.74 |
| `external_no_targets` | 1.61 | 1.60–1.94 |
| `ferrofluid_pool` | 1.65 | 1.62–1.69 |
| `ferrofluid_external` | 2.09 | 1.97–2.26 |
| `ferrofluid_native` | 1.77 | 1.74–1.80 |
| `mr_armor` | 1.67 | 1.60–1.80 |
| `mr_mainhand` | 1.60 | 1.59–1.62 |
| `mr_offhand` | 1.63 | 1.58–1.75 |
| `native_queries_control` | 1.60 | 1.56–1.65 |
| `native_queries_64` | 1.67 | 1.61–1.75 |
| `native_queries_256` | 1.79 | 1.76–1.89 |
| `gas_stable` | 1.59 | 1.55–1.64 |
| `shafts_16` | 1.71 | 1.68–1.98 |
| `shafts_64` | 1.92 | 1.87–2.23 |
| `shafts_256` | 2.79 | 2.72–2.99 |
| `ships_4` | 3.38 | 3.20–3.50 |
| `ships_16` | 7.48 | 7.42–7.54 |
| `empty_end` | 1.68 | 1.66–1.73 |

The 50 ms/tick budget corresponds to 20 TPS. The highest steady sprint average,
16 moving ships, was 7.48 ms/tick; this does not rule out individual long ticks.
Changing gas cost 5.44 versus 1.59 ms/tick for stable gas. Dense external magnets
cost 3.03 versus 1.75 ms/tick for the matching block/item control; powered native
magnets cost 2.47 ms/tick. These differences include all modpack interactions.
They are not timings isolated to Magnetization.

The 256-query fixture measured 1.79 versus 1.60 ms/tick for its zero-query control.
Equipment and no-target cases stayed near the controls. Differences below about
0.1 ms/tick should not be treated as meaningful with this experiment.

## Normal-speed profiles

Each steady-state capture lasted 180 seconds / 3,600 ticks after a 60-second
normal-speed workload warmup. All five sustained approximately 20 TPS. Spark used
its asynchronous execution sampler at 4 ms intervals. One-minute median ranges
and worst recorded ticks cover the entire capture; they are not sprint averages.

| Workload | One-minute median range, ms/tick | Worst tick, ms | Sampled Magnetization production ms/tick | Capture |
|---|---:|---:|---:|---|
| `empty_start` | 2.75–2.82 | 12.46 | 0.046 | [Spark](https://spark.lucko.me/ccMErqYWdT) |
| `dense_external` | 4.15–4.17 | 29.31 | 1.422 | [Spark](https://spark.lucko.me/Jy7ybRBntO) |
| `gas_volume` | 6.51–6.54 | 27.39 | 3.784 | [Spark](https://spark.lucko.me/Va1IlNJsE4) |
| `shafts_256` | 3.18–3.25 | 87.49 | 1.007 | [local saved capture](../../../build/reports/server-stress-2026-10-01/profile-shafts_256.spark) |
| `ships_16` | 8.16–8.28 | 44.34 | 1.888 | [Spark](https://spark.lucko.me/nRWkt80AeR) |

The shaft capture contained an 87.49 ms tick despite maintaining 20 TPS over its
windows. These results establish sustained headroom for these fixtures, not an
absence of hitches. The shaft profile upload returned HTTP 400; Spark saved the
capture locally and it was recovered without rerunning the measurement. Its
server path is `/config/spark/profile-2026-10-01_19.22.17.sparkprofile`.

The control, dense-magnet and gas captures each had 1,444 loaded chunks. Dense
magnets had 64 item entities; the empty control had zero entities. Actual counts
for every capture are retained in `profiles.json`.

Largest measured production paths:

- Changing gas: `GasExcitation.recompute`, about **3.73 ms/tick**.
- Dense magnets: `ExternalEmitterTracker.onLevelTick`, about **1.38 ms/tick**;
  includes `applyBudgeted` (~0.95), field application (~0.63), and target
  scheduling (~0.42). These inclusive figures overlap and must not be summed.
- Moving ships: anchor emitter ticking ~1.25 ms/tick, including Simulated Coasters
  classification ~0.75; Lenz braking ~0.56, predominantly conductor scanning.
- 256 shaft networks: `MagneticShaftNetwork.tick`, about **0.98 ms/tick**.


## Bulk setup behavior — significant remaining issue

[Dedicated bulk-placement capture](https://spark.lucko.me/FpdJdb4daM): start from
the empty fixture, begin profiling, wait ten seconds, then schedule the standard
`ferrofluid_external` setup. This creates a confined 32 × 32 × 2 pool (2,048 fluid
blocks) plus 64 external magnets. The capture includes the setup and subsequent
steady state; it must not be compared as a steady-state average.

- Worst tick: **16,006.32 ms**.
- First approximately 60-second window: **881 ticks**; the next window: **1,200**.
- About **14.012 seconds** sampled under `FluidRedstone.recomputeNetwork` via
  neighbor notifications, plus **1.436 seconds** via placement: **15.448 seconds**
  combined across the capture. These are disjoint call branches, not a sum of
  overlapping parent and child frames.
- `FluidRedstone.externalSignal` alone accounts for about 8.36 seconds inclusive
  across those branches, within the recomputation total.
- The server recovered. This was a bulk-edit stress case, not ordinary single
  bucket placement, and no historical crash-causality conclusion follows.

Earlier scheduled setups took approximately 20–22 seconds from dispatch to the
completion observation, including up to two seconds of polling. The first direct
RCON setup was repeated by the panel after a response failure, logging completion
at 17 and 20 seconds for the two invocations. Its interrupted setup produced no
measurement sample. Scheduling subsequent setups eliminated that command retry.
The dedicated capture above supplies the direct attribution missing from those
initial observations.

## Six follow-up optimization opportunities

These are **unimplemented candidates**, ranked for follow-up rather than claimed
speedups. Costs come from different workloads and are not additive.

| Priority | Candidate | Evidence and constraint |
|---|---|---|
| 1 | Avoid repeated ferrofluid redstone-network traversals during dense edits | ~15.45 seconds in the bulk capture. Investigate component invalidation and duplicate work; preserve signal propagation, neighbor ordering and same-tick redstone behavior. |
| 2 | Reduce repeated gas component recomputation | ~3.73 ms/tick under single-cell churn. Investigate duplicate component visits and repeated fluid lookups; preserve topology, live power, energy and grace semantics. |
| 3 | Reduce dense external-field application/scheduling work | ~1.38 ms/tick in the external tracker. Investigate shared calculations within a pass and redundant entity/neighbor reads; retain budgets, target order and live signals. |
| 4 | Check the coaster field-reaction option before classifying a ship | ~0.75 ms/tick in coaster classification. `receivesMagneticFields` currently evaluates structure membership first; an enabled reaction option may allow an immediate true result. Verify configuration and integration behavior before changing it. |
| 5 | Reduce Lenz conductor lookup work | ~0.56 ms/tick with 16 moving magnetic ships. Investigate loaded-section iteration or an invalidated conductor index; preserve sample order/cap, bounds, motion and block/tag changes. |
| 6 | Further reduce shaft-network update work at high network counts | ~0.98 ms/tick at 256 networks. Review remaining per-pass work using this full-pack profile; preserve cadence, direction, conflicts and moving-ship range behavior. |

The first candidate is the most urgent demonstrated transient-load issue. The
coaster option check is a smaller, concrete candidate whose benefit depends on
the live configuration. The server’s `simulatedCoastersFieldReaction` option was
verified enabled; the source currently performs classification before reading it.
No production code was changed during this audit.

## Coverage limits

- No human or networked bot clients joined. The fake-player fixture directly
  invokes the MR-equipment handler; it does not simulate the complete player
  tick, combat, inventory traffic, chunk streaming or Slugterra mount/projectile
  activity. The cow fixture has AI disabled.
- The separator grid measures placed separators without an operating feed.
  Powered railgun blocks are not a sustained projectile-firing test.
- Gas tests exercise a confined unpowered argon volume and its topology changes;
  they do not cover every powered-exciter/energy/grace combination.
- No survival exploration, terrain generation, autosave, or long-duration
  multiplayer soak was measured. These tests do not establish a player capacity.
- Spark CPU attribution is statistical, inclusive of callees, restricted to the
  Server thread and recognized `com.stonytark.magnetization` classes. It is not
  an allocation measurement or exact accounting of all injected/mixin work.
  Fixture/diagnostic classes are excluded from the production estimate. Nested
  production methods are counted once; individual hotspot rows overlap.
- The earlier original-world idle variability remains unresolved. Fresh-world
  stress results neither explain it nor prove it fixed. Crash causality is outside
  this optimization report.

## Restoration

Verified after restart:

- Original `world` loaded; server running with zero players.
- 1,746 indexed emitters and five Sable ships, matching the pre-test counts.
- Every saved `server.properties` value restored; temporary panel settings,
  JVM arguments, whitelist and operator files restored.
- Fixture command disabled. Installed JAR hash unchanged.
- Original `world/level.dat` remained byte-identical throughout testing, checked
  before restarting that world. A full pre-test world archive was also retained.
- Production source unchanged. The disposable test world is retained separately
  for inspection; it is not selected for normal startup.

Original-world archive:
`/codex-before-fullpack-stress-20261001.zip` on the server (423 files).

Raw local evidence: `build/reports/server-stress-2026-10-01/`.
