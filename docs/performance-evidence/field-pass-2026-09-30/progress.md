# Field and fluid performance pass — 2026-09-30

Scope: implement all 14 items in the user goal, retain gameplay, commit/push sequentially, run a short Discopanel comparison after items 1–4 and a full comparison after 1–14. Worktree branch: `codex/field-performance`, starting at `c3f2bd8`. Concurrent, uncommitted compatibility work in the main checkout is not part of this build.

| Item | Implementation / validation |
|---|---|
| 1. Disabled relay early exit | Both entrypoints check the live enable flag before copying the registry. Ender Transmission runtime suite: 3/3 required tests passed, including disabled/enabled one-hop projection. |
| 2. Three-dimensional candidate rejection | Conservative inclusive 3D recipient bounds before field evaluation; unknown adapter ranges remain eligible. Includes ship hulls, fluid sections, portal apertures and enabled train carriages. Preserves candidate attempts/cursor and relay query. Unit suite passed; CNA runtime 13/13, including vertical target movement and budget rotation. Large-hull/aperture boundary unit cases passed; Immersive Aeronautics/Portals runtime 5/5 passed, including real cross-portal ship force; final optional-runtime regressions also passed. |
| 3. Lazy ship field setup | Configuration and biome setup occurs once, after the first intersecting, registered, valid-mass, accepted, nonzero-polarity ship. Force integration and caps unchanged. Core gameplay suite: 173/173 required tests passed. Added opt-in ship_field_preparations counter for workload verification. |
| 4. Direct candidate list query | Direct bounded list from unique chunk keys; same bucket and caller order, cap, cursor behavior and defensive snapshot. Reference comparison covers limits, negative chunks, deduplication and unload. Full unit suite: 255 tests, 0 failures/errors; release build and artifact checks passed. |
| First Discopanel checkpoint | Build 8e2b99d deployed to original world; SHA256 be33761c7095dda5db6837e84a262daaf1bebf66c6471f51fc2190fae3031b83. Both captures 60 seconds / 1,200 ticks, 846 chunks, no players, 1,746 emitters and 5 ships. Baseline 49 entities, candidate 50. Sampled outermost mod time 0.347 → 0.087 ms/tick; median tick 8.005 → 7.136 ms. One capture each is directional evidence, not a stable percentage guarantee. Repeat: 0.060 ms/tick sampled mod time, 7.139 ms median, 50 entities / 846 chunks; supports the direction of improvement in this idle scene. |
| 5. Lazy redstone fallback | Both isRSPowered and Tesla canRun fallbacks are lazy. Live true/false results skip fallback; missing, invalid and throwing methods retain fallback behavior. Unit suite passed (256 tests). |
| 6. Early Tesla pulse gate | Original two-in-ten pulse condition now precedes block entity, energy and redstone access. IE runtime 5/5 passed, including charged pulses and explicit zero energy-read counts off-pulse. |
| 7. Cached adapter reflection metadata | ClassValue caches ordered field/method accessors and negative lookups, never instances or live results. Dynamic energy/storage replacement and inherited fallback unit tests passed (258 total). IE runtime 5/5 and Create Addition 3/3 passed. Addition watcher was given a misspelled run directory; authoritative runtime log confirms all assertions passed, and its lingering JVM was terminated separately. Correct directory is run-createaddition-gametest. |
| 8. Empty recession gate | Recession returns before building originals or copying sources when the creep registry is empty. Added recession_setups counter. Compile passed; CNA growth/recession integration 13/13 passed after item 9. |
| 9. Occupied emitter queries for ferrofluid | Ordered occupied X/Z rows replace empty chunk enumeration, retaining exact inclusive 32-/512-block expansion and candidate order. Unit suite 259/259; randomized 1,000-operation lifecycle checks cover row/category coherence. CNA integration 13/13, including attraction, repulsion and orphan recession. Chunk-attempt counters now count selected occupied bucket lookups; emitter_buckets_inspected records spatial traversal work. |
| 10. MR stored power first | Stored positive power returns before field lookup; unpowered field/neighbor fallbacks are unchanged. CNA suite 14/14, including real hardening with zero field searches and restoration after power removal. |
| 11. Gallium occupancy first | Powered empty cells skip field search and force setup. An O(1) source-presence gate avoids added entity queries in field-free levels. CNA 15/15: both plain/mixed gallium retain exact current speed and both polarity directions for a nonmagnetic item. Full-pack occupancy/no-field timing tradeoff is included in the final comparison below. |
| 12. Local magnetized-fluid groups | Recipient queries are cached per occupied 16³ section; application still follows the original source iteration order. CNA 16/16: a target between distant pools triggers zero applications, moving into one pool applies both nearby sources with exactly the reference combined velocity. |
| 13. Nearby train carriage queries | Uses live Minecraft spatial queries above 16 tracked cars once multiple fields query the same tick, then restores original iteration order. Discovery and a lone field do not build ordering metadata. Small populations retain a short scan; unusual sizes/subclasses and not-yet-visible joins retain conservative fallbacks, invalidated on size/lifecycle changes. Steam Rails runtime 2/2 (including the final lazy-setup refinement): 40-car fixture verifies subset/order, exact coupled force, same-tick movement and removal. |
| 14. Spatial magnetized-fluid field lookup | Chunk-local lookup preserves the existing WEAK-radius membership query. Mutable registry views update the index; palette-gated chunk reload restores source polarity, while unload/removal removes membership. Unit/build checks passed (261 tests); CNA 17/17 includes exact boundary, flowing-cell exclusion, unload/reload and removal. |
| Full Discopanel validation and completion audit | 25 matched scenarios completed on both builds (75 checkpoint / 84 final assertions). Three timing profiles per build completed. Original world/settings restored, final JAR verified, both production captures complete. |

## Validation commands

`bash scripts/run-gametest-gate.sh runEnderTransmissionGameTestServer run-ender-transmission-gametest 300 -PmagSkipGameTestCleanup=true`

The new optional Gradle flag disables the legacy global GameTest-process cleanup in isolated worktrees. The gate supervises and terminates only its own process group, protecting concurrent tests in other workspaces. Default existing behavior is unchanged.

## First checkpoint captures

- [Baseline, previous 1.4.6 build](https://spark.lucko.me/L1qX2wzq1L)
- [First four changes](https://spark.lucko.me/jbycQG5qCH)
- [First four changes, repeat](https://spark.lucko.me/2O999PWEwE)

Raw captures, decoded trees, original/candidate JARs and private server logs are retained in `build/reports/field-pass-2026-09-30/` (not committed). Baseline SHA256: `58d7a5cba43b582ed7d5312c1c739411a0c8135f174d35942cf61f004e307bd4`. Both artifact manifests still identify 1.4.6; use the commit and checksum to distinguish the checkpoint. The first checkpoint ran in the original world with unchanged panel settings. The baseline was already warm; the candidate received two minutes of warmup after restart. These are sampling estimates, not direct handler timers.

## Final local regression gate

After all 14 implementations: 261 unit tests, release artifact checks, and 208 required GameTests passed (173 core, 17 CNA/optimization, 3 Ender Transmission, 2 Steam Rails, 5 Immersive Aeronautics/Portals, 5 Immersive Engineering, 3 Create Addition). Optional suites ran separately with their actual runtimes.

The full comparison uses `scripts/generate-field-pass-fixtures.py` to extend the existing disposable stress pack with seven contained scenarios. It uses a separate flat lab dimension inside a copy of the server world, with diagnostic flags enabled equally for checkpoint-four and final builds. This includes the gallium occupied/no-field tradeoff, separated magnetized pools, distant-source queries, stored MR power and vertical external fields. Both builds completed all 25 scenarios and the three matched timing captures. Checkpoint-four passed 75 workload assertions; the final build passed 84, including the new counters. Raw nonzero counters are recorded in `workload-counts.json`.


## Full-pack controlled results

The comparison uses checkpoint-four (`8e2b99d`) versus all fourteen changes (`bd8baf8`) in the same disposable lab. Each scenario settles for five seconds, reindexes, then records approximately 200 ticks. Both builds use the same performance-diagnostics and fixture flags. These results measure incremental changes after the first four; the earlier original-world captures measure the first checkpoint against the preceding binary.

| Workload | Checkpoint-four | All fourteen |
|---|---:|---:|
| Native-emitter discovery, 512 fluid sources in four chunks | 16,900 attempted chunk keys / creep pass | 1 occupied key / pass; 4 bucket inspections / pass |
| External-emitter discovery, same fluid layout | 100 attempted chunk keys / creep pass | 4 occupied keys / pass; 16 bucket inspections / pass |
| External fluid-field applications | 16 / tick | 16 / tick |
| Dense external emitters | 256 applications / tick | 256 applications / tick |
| Vertically distant external emitters | Zero evaluations | Zero evaluations; 256 bounds rejections / tick |
| Stable enclosed fluid pools | Recession setup was unconditional | Zero recession setups |
| Positively powered MR cells | Field lookup preceded stored-power check | Zero field searches |
| Empty powered gallium, emitter present | Field lookup preceded occupancy check | 6,464 occupancy queries; zero field searches / 201 ticks |
| Occupied powered gallium, emitter out of range | Field lookup first | 6,400 occupancy queries and live field searches / 200 ticks |
| Occupied gallium with no source anywhere in lab | Field lookup first | Zero occupancy or field queries |
| Target midway between distant magnetized pools | Dimension-wide recipient box allowed source processing | Zero field applications; 536 local target queries / 201 ticks |
| MR-equipped target far from 64 magnetized sources | Full source scan | 40 MR searches, zero fluid candidates / 200 ticks |

Chunk-key counts have deliberately changed meaning from expanded positions attempted to occupied positions selected. The separate bucket-inspection counter records index traversal; this is not a claim of a 16,900-fold runtime improvement. Newly added counters were unavailable in the checkpoint-four binary, so its uninstrumented paths above are described from source rather than reported as measured counts. Nearby fluid forces, source ordering, train ordering, live movement, polarity, pulse timing, and lifecycle behavior are covered by the local GameTests/unit tests; the live-pack count fixtures alone do not prove every force invariant.

### Matched timing captures

Each profile lasts approximately 60 seconds / 1,200 ticks after a 20-second scenario settle, with 2,095 loaded chunks and zero players. Entity counts match per pair: 45 empty gallium, 109 occupied gallium, 46 separated pools. Values below are sampled handler milliseconds divided by observed ticks, not direct timers.

| Scenario / handler | Checkpoint-four ms/tick | All-fourteen ms/tick | Profiles |
|---|---:|---:|---|
| Empty powered gallium / gallium handler | 0.060 | 0.050 | [Checkpoint](https://spark.lucko.me/uSQvCtngLj), [final](https://spark.lucko.me/F4QIuROkhf) |
| Occupied gallium outside field / gallium handler | 0.107 | 0.023 | [Checkpoint](https://spark.lucko.me/ZDyMJrUUqL), [final](https://spark.lucko.me/UzPAQeMzyc) |
| Separated pools / magnetized-fluid handler | 0.040 | 0.013 | [Checkpoint](https://spark.lucko.me/4gt9ZDiIfa), [final](https://spark.lucko.me/ncrPW6WxzB) |

The gallium occupancy tradeoff showed no measured handler regression in these captures, but one pair per case is insufficient for a stable percentage claim. Total sampled mod time was noisy: the external-emitter handler contributed 1.120 ms/tick in the occupied checkpoint capture and 1.209 ms/tick in the empty final capture, versus much lower values in the other captures. Consequently, total-mod percentages cannot be attributed to the gallium change. Median whole-server tick times were 8.223 → 7.408 ms (empty), 8.218 → 8.151 ms (occupied), and 7.411 → 7.517 ms (separated). The evidence supports eliminated work and lower sampled specific-handler time, not a universal whole-server speedup. This optimization pass does not establish the cause of the separate crash.


## Final deployment

Final code: `bd8baf8`, version 1.4.6, SHA256 `63acc527a8dc674b3674279350f8f66e1e88046d227e0fd6a05c22d65424873f`. Local artifact: `build/reports/field-pass-2026-09-30/final-magnetization-1.4.6.jar`. Version 1.4.6 was already unreleased on this branch; checksums and source commits distinguish the test binaries.

Discopanel is restored to the original `world`, original JVM options, and original Docker overrides. The final JAR remains installed and its bytes were verified by readback. Startup reports 1,746 block emitters and five Sable ships, with zero players. Diagnostic/fixture commands are disabled again. Comparing the last 3,000 startup lines against checkpoint-four found no new normalized error messages (timestamps/worker IDs removed); existing pack errors remain, so this does not claim an error-free pack. The disposable world copy is retained separately for reproduction.

Production captures use the original world, diagnostics disabled, 846 chunks, 49 entities, zero players, and a two-minute startup warmup. Both contain 1,200 ticks. [Final](https://spark.lucko.me/rUX04SuWV1): sampled outermost mod time **0.053 ms/tick**, median server tick **7.347 ms**. [Repeat](https://spark.lucko.me/PoNjMfwjVO): **0.070 ms/tick**, median **7.355 ms**. The pre-pass capture was 0.347 / 8.005 ms; checkpoint-four captures were 0.087 and 0.060 / 7.136 and 7.139 ms. The final mod samples are close to checkpoint-four, while whole-server medians are slightly higher than that checkpoint. This idle world therefore demonstrates the main gain from the first four changes; it does not establish an additional idle-server speedup from the remaining ten. The targeted workload reductions above are the stronger evidence for those changes.

Compact profile measurements are retained in `profile-summary.json`; full Spark trees and server logs stay in the private build reports. All implementation commits and this evidence are on `codex/field-performance`; concurrent main-checkout compatibility edits were excluded. The complete local gates, first-four checkpoint, full comparison, restored deployment, and final artifact checks are complete.
