# Field and fluid performance pass — 2026-09-30

Scope: implement all 14 items in the user goal, retain gameplay, commit/push sequentially, run a short Discopanel comparison after items 1–4 and a full comparison after 1–14. Worktree branch: `codex/field-performance`, starting at `c3f2bd8`. Concurrent, uncommitted compatibility work in the main checkout is not part of this build.

| Item | Implementation / validation |
|---|---|
| 1. Disabled relay early exit | Both entrypoints check the live enable flag before copying the registry. Ender Transmission runtime suite: 3/3 required tests passed, including disabled/enabled one-hop projection. |
| 2. Three-dimensional candidate rejection | Conservative inclusive 3D recipient bounds before field evaluation; unknown adapter ranges remain eligible. Includes ship hulls, fluid sections, portal apertures and enabled train carriages. Preserves candidate attempts/cursor and relay query. Unit suite passed; CNA runtime 13/13, including vertical target movement and budget rotation. Large-hull/aperture boundary unit cases passed; full optional runtime regression remains for final checkpoint. |
| 3. Lazy ship field setup | Configuration and biome setup occurs once, after the first intersecting, registered, valid-mass, accepted, nonzero-polarity ship. Force integration and caps unchanged. Core gameplay suite: 173/173 required tests passed. Added opt-in ship_field_preparations counter for workload verification. |
| 4. Direct candidate list query | Pending |
| First Discopanel checkpoint | Pending |
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
