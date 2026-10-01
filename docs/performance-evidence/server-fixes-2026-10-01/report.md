# Full-pack stress optimizations — 2026-10-01

Status: final updated-pack measurements and restoration in progress. This file
must not be read as a completed performance validation until the result tables
and restoration evidence are recorded below.

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

## Gameplay validation

Build, release-JAR verification and all 293 unit tests pass. The new solver has
400 differential graph cases covering cycles, multiple inputs and cap edges.
There are 264 passing GameTest cases across core, engineering, MR regressions,
coasters, CNA, IE, Alexs Caves, CreateAddition, TFMG and Slugterra configurations.
[Validation evidence](gameplay-validation.json) identifies which source revision
and dependency each gate exercised; unchanged paths were not all rerun after
every later edit.

The final fluid refinement passed all 176 core tests. The focused Slugterra test
checks both projectile movement protocols, turning without speed gain, a live
flight-state change within the same tick and ordinary iron-item attraction.
The new port passes all nine broad Slugterra cases. An initial direct core run
failed an unrelated separator visual check in a terrain world; the repository's
fresh-flat supervisor subsequently passed the complete core suite.

A separate full-pack function checks 37 immediate fluid states without yielding
between source edits and assertions: direct sources, removal, split/rejoin,
multiple sources and indirect lever power. Its result remains pending.
