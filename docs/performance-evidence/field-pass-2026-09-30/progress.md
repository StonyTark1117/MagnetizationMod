# Field and fluid performance pass — 2026-09-30

Scope: implement all 14 items in the user goal, retain gameplay, commit/push sequentially, run a short Discopanel comparison after items 1–4 and a full comparison after 1–14. Worktree branch: `codex/field-performance`, starting at `c3f2bd8`. Concurrent, uncommitted compatibility work in the main checkout is not part of this build.

| Item | Implementation / validation |
|---|---|
| 1. Disabled relay early exit | Both entrypoints check the live enable flag before copying the registry. Ender Transmission runtime suite: 3/3 required tests passed, including disabled/enabled one-hop projection. |
| 2. Three-dimensional candidate rejection | Conservative inclusive 3D recipient bounds before field evaluation; unknown adapter ranges remain eligible. Includes ship hulls, fluid sections, portal apertures and enabled train carriages. Preserves candidate attempts/cursor and relay query. Unit suite passed; CNA runtime 13/13, including vertical target movement and budget rotation. Large-hull/aperture boundary unit cases passed; full optional runtime regression remains for final checkpoint. |
| 3. Lazy ship field setup | Configuration and biome setup occurs once, after the first intersecting, registered, valid-mass, accepted, nonzero-polarity ship. Force integration and caps unchanged. Core gameplay suite: 173/173 required tests passed. Added opt-in ship_field_preparations counter for workload verification. |
| 4. Direct candidate list query | Direct bounded list from unique chunk keys; same bucket and caller order, cap, cursor behavior and defensive snapshot. Reference comparison covers limits, negative chunks, deduplication and unload. Full unit suite: 255 tests, 0 failures/errors; release build and artifact checks passed. |
| First Discopanel checkpoint | Build 8e2b99d deployed to original world; SHA256 be33761c7095dda5db6837e84a262daaf1bebf66c6471f51fc2190fae3031b83. Both captures 60 seconds / 1,200 ticks, 846 chunks, no players, 1,746 emitters and 5 ships. Baseline 49 entities, candidate 50. Sampled outermost mod time 0.347 → 0.087 ms/tick; median tick 8.005 → 7.136 ms. One capture each is directional evidence, not a stable percentage guarantee. Repeat pending. |
| 5. Lazy redstone fallback | Pending |
| 6. Early Tesla pulse gate | Pending |
| 7. Cached adapter reflection metadata | Pending |
| 8. Empty recession gate | Pending |
| 9. Occupied emitter queries for ferrofluid | Pending |
| 10. MR stored power first | Pending |
| 11. Gallium occupancy first | Pending |
| 12. Local magnetized-fluid groups | Pending |
| 13. Nearby train carriage queries | Pending |
| 14. Spatial magnetized-fluid field lookup | Pending |
| Full Discopanel validation and completion audit | Pending |

## Validation commands

`bash scripts/run-gametest-gate.sh runEnderTransmissionGameTestServer run-ender-transmission-gametest 300 -PmagSkipGameTestCleanup=true`

The new optional Gradle flag disables the legacy global GameTest-process cleanup in isolated worktrees. The gate supervises and terminates only its own process group, protecting concurrent tests in other workspaces. Default existing behavior is unchanged.

## First checkpoint captures

- [Baseline, previous 1.4.6 build](https://spark.lucko.me/L1qX2wzq1L)
- [First four changes](https://spark.lucko.me/jbycQG5qCH)

Raw captures, decoded trees, original/candidate JARs and private server logs are retained in `build/reports/field-pass-2026-09-30/` (not committed). Baseline SHA256: `58d7a5cba43b582ed7d5312c1c739411a0c8135f174d35942cf61f004e307bd4`. Both artifact manifests still identify 1.4.6; use the commit and checksum to distinguish the checkpoint. The original world and panel settings are unchanged. The baseline was already warm; the candidate received two minutes of warmup after restart. These are sampling estimates, not direct handler timers.
