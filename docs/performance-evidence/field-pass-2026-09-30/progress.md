# Field and fluid performance pass — 2026-09-30

Scope: implement all 14 items in the user goal, retain gameplay, commit/push sequentially, run a short Discopanel comparison after items 1–4 and a full comparison after 1–14. Worktree branch: `codex/field-performance`, starting at `c3f2bd8`. Concurrent, uncommitted compatibility work in the main checkout is not part of this build.

| Item | Implementation / validation |
|---|---|
| 1. Disabled relay early exit | Both entrypoints check the live enable flag before copying the registry. Ender Transmission runtime suite: 3/3 required tests passed, including disabled/enabled one-hop projection. |
| 2. Three-dimensional candidate rejection | Conservative inclusive 3D recipient bounds before field evaluation; unknown adapter ranges remain eligible. Includes ship hulls, fluid sections, portal apertures and enabled train carriages. Preserves candidate attempts/cursor and relay query. Unit suite passed; CNA runtime 13/13, including vertical target movement and budget rotation. Large-hull/aperture boundary unit cases passed; Immersive Aeronautics/Portals runtime 5/5 passed, including real cross-portal ship force; broader final regressions remain. |
| 3. Lazy ship field setup | Configuration and biome setup occurs once, after the first intersecting, registered, valid-mass, accepted, nonzero-polarity ship. Force integration and caps unchanged. Core gameplay suite: 173/173 required tests passed. Added opt-in ship_field_preparations counter for workload verification. |
| 4. Direct candidate list query | Direct bounded list from unique chunk keys; same bucket and caller order, cap, cursor behavior and defensive snapshot. Reference comparison covers limits, negative chunks, deduplication and unload. Full unit suite: 255 tests, 0 failures/errors; release build and artifact checks passed. |
| First Discopanel checkpoint | Build 8e2b99d deployed to original world; SHA256 be33761c7095dda5db6837e84a262daaf1bebf66c6471f51fc2190fae3031b83. Both captures 60 seconds / 1,200 ticks, 846 chunks, no players, 1,746 emitters and 5 ships. Baseline 49 entities, candidate 50. Sampled outermost mod time 0.347 → 0.087 ms/tick; median tick 8.005 → 7.136 ms. One capture each is directional evidence, not a stable percentage guarantee. Repeat: 0.060 ms/tick sampled mod time, 7.139 ms median, 50 entities / 846 chunks; supports the direction of improvement in this idle scene. |
| 5. Lazy redstone fallback | Both isRSPowered and Tesla canRun fallbacks are lazy. Live true/false results skip fallback; missing, invalid and throwing methods retain fallback behavior. Unit suite passed (256 tests). |
| 6. Early Tesla pulse gate | Original two-in-ten pulse condition now precedes block entity, energy and redstone access. IE runtime 5/5 passed, including charged pulses and explicit zero energy-read counts off-pulse. |
| 7. Cached adapter reflection metadata | ClassValue caches ordered field/method accessors and negative lookups, never instances or live results. Dynamic energy/storage replacement and inherited fallback unit tests passed (258 total). IE runtime 5/5 and Create Addition 3/3 passed. Addition watcher was given a misspelled run directory; authoritative runtime log confirms all assertions passed, and its lingering JVM was terminated separately. Correct directory is run-createaddition-gametest. |
| 8. Empty recession gate | Recession returns before building originals or copying sources when the creep registry is empty. Added recession_setups counter. Compile passed; CNA growth/recession integration 13/13 passed after item 9. |
| 9. Occupied emitter queries for ferrofluid | Ordered occupied X/Z rows replace empty chunk enumeration, retaining exact inclusive 32-/512-block expansion and candidate order. Unit suite 259/259; randomized 1,000-operation lifecycle checks cover row/category coherence. CNA integration 13/13, including attraction, repulsion and orphan recession. Chunk-attempt counters now count selected occupied bucket lookups; emitter_buckets_inspected records spatial traversal work. |
| 10. MR stored power first | Stored positive power returns before field lookup; unpowered field/neighbor fallbacks are unchanged. CNA suite 14/14, including real hardening with zero field searches and restoration after power removal. |
| 11. Gallium occupancy first | Powered empty cells skip field search and force setup. An O(1) source-presence gate avoids added entity queries in field-free levels. CNA 15/15: both plain/mixed gallium retain exact current speed and both polarity directions for a nonmagnetic item. Full-pack occupancy/no-field timing tradeoff remains for final comparison. |
| 12. Local magnetized-fluid groups | Recipient queries are cached per occupied 16³ section; application still follows the original source iteration order. CNA 16/16: a target between distant pools triggers zero applications, moving into one pool applies both nearby sources with exactly the reference combined velocity. |
| 13. Nearby train carriage queries | Uses live Minecraft spatial queries above 16 tracked cars once multiple fields query the same tick, then restores original iteration order. Discovery and a lone field do not build ordering metadata. Small populations retain a short scan; unusual sizes/subclasses and not-yet-visible joins retain conservative fallbacks, invalidated on size/lifecycle changes. Steam Rails runtime 2/2 (including the final lazy-setup refinement): 40-car fixture verifies subset/order, exact coupled force, same-tick movement and removal. |
| 14. Spatial magnetized-fluid field lookup | Chunk-local lookup preserves the existing WEAK-radius membership query. Mutable registry views update the index; palette-gated chunk reload restores source polarity, while unload/removal removes membership. Unit/build checks passed (261 tests); CNA 17/17 includes exact boundary, flowing-cell exclusion, unload/reload and removal. |
| Full Discopanel validation and completion audit | Pending |

## Validation commands

`bash scripts/run-gametest-gate.sh runEnderTransmissionGameTestServer run-ender-transmission-gametest 300 -PmagSkipGameTestCleanup=true`

The new optional Gradle flag disables the legacy global GameTest-process cleanup in isolated worktrees. The gate supervises and terminates only its own process group, protecting concurrent tests in other workspaces. Default existing behavior is unchanged.

## First checkpoint captures

- [Baseline, previous 1.4.6 build](https://spark.lucko.me/L1qX2wzq1L)
- [First four changes](https://spark.lucko.me/jbycQG5qCH)
- [First four changes, repeat](https://spark.lucko.me/2O999PWEwE)

Raw captures, decoded trees, original/candidate JARs and private server logs are retained in `build/reports/field-pass-2026-09-30/` (not committed). Baseline SHA256: `58d7a5cba43b582ed7d5312c1c739411a0c8135f174d35942cf61f004e307bd4`. Both artifact manifests still identify 1.4.6; use the commit and checksum to distinguish the checkpoint. The original world and panel settings are unchanged. The baseline was already warm; the candidate received two minutes of warmup after restart. These are sampling estimates, not direct handler timers.

## Final local regression gate

After all 14 implementations: 261 unit tests, release artifact checks, and 208 required GameTests passed (173 core, 17 CNA/optimization, 3 Ender Transmission, 2 Steam Rails, 5 Immersive Aeronautics/Portals, 5 Immersive Engineering, 3 Create Addition). Optional suites ran separately with their actual runtimes.

The full comparison uses `scripts/generate-field-pass-fixtures.py` to extend the existing disposable stress pack with seven contained scenarios. It uses a separate flat lab dimension inside a copy of the server world, with diagnostic flags enabled equally for checkpoint-four and final builds. This includes the gallium occupied/no-field tradeoff, separated magnetized pools, distant-source queries, stored MR power and vertical external fields. Server results remain pending until both builds complete the same captures.
