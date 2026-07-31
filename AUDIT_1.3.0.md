# Magnetization 1.3.0 — Full Release Audit (rerun)

**Audit date:** 2026-07-31
**Target:** current `main` release candidate, 52 commits ahead of `origin/main` before this audit-coverage commit
**Minecraft / loader:** Minecraft 1.21.1, NeoForge 21.1.247
**Policy:** full audit rerun followed by remediation and verification of the two remaining release-engineering findings.

## Executive verdict

**The candidate has no confirmed release-blocking defect or remaining automated release-engineering finding in this rerun.** All three original P0 findings and all original P1 findings are fixed, the standard and compatibility GameTests pass and terminate under bounded supervision, the minimal server profile contains only the intended hard-dependency stack, the clean build passes 140 unit tests, and the previously duplicated progression textures are now distinct.

The candidate is **ready to move into final manual playtesting**. `releaseGate` now completes unattended; the remaining work is the human-observation matrix for presentation, balance, moving-ship feel, portal edge cases, and full survival progression.

The NeoForge range `[21.1.247,22.0)` is intentional. The open-ended dependency policy applies to the mod/API dependencies that previously locked users out after compatible major updates; NeoForge remains capped at the next loader generation by design.

The required manual playtest matrix remains a release gate. Automated tests cannot validate visual presentation, balance, moving-ship feel, or full survival progression.

## Scope and evidence standard

This rerun covered:

- 293 Java source files and 1,626 files under main/generated resources.
- The full 1.3.0 delta from `origin/main`, including all earlier audit fixes, the Dipole Electromagnet, analog-redstone force scaling, multiblock previews, accessibility work, AeroPortals support, and Immersive Aeronautics/Immersive Portals support.
- Dependency metadata and resolved development versions.
- Registration/resource/package consistency through a clean Gradle build.
- All JSON resources, unit tests, the standard GameTest profile, both portal compatibility profiles, and the minimal dedicated-server smoke profile.
- A targeted repeat of the original texture hash audit.

A **confirmed issue** below is directly demonstrated by current source, metadata, or reproduced runtime behavior. Items requiring human observation remain in the manual matrix and are not asserted as bugs.

The worktree was clean when the rerun began. The remediation changed the Gradle release harness, added its bounded GameTest supervisor, and corrected a GameTest cleanup race exposed by repeated fresh-world execution; this audit records the resulting evidence.

## Remaining confirmed issues

None found by the automated audit rerun. The manual playtest matrix remains required and may still reveal visual, balance, interaction, or portal-geometry defects that static and headless tests cannot establish.

## Original issue resolution ledger

### Original P0 findings

1. **Railgun manual workflow — FIXED.** Inserting a remote latches pairing/manual mode; removing it no longer cancels `HOLDING`. Unpairing is explicit, and real remote-use coverage exists. Follow-up commit `2f0c4f8` also corrected the hold behavior discovered during verification.
2. **Restart-driven surface repaint exploit — FIXED.** Repainting now uses versioned per-level persistent chunk migrations. Restarting no longer reopens already examined chunks, and the migration framework is exposed through the admin audit command.
3. **Byte-identical progression textures — FIXED.** Bucket fills, fuel cells, lithium stages, Helium-3, railgun remote, machine faces, and gallium assets are distinct. The Gallium Golem texture was also updated to match solid gallium.

### Original P1 findings

4. **Curios Repulsor cooldown bypass — FIXED.** Hand and Curios paths share server-side activation with cooldown enforcement; packet-spam GameTests cover the security boundary.
5. **Curios Grapple wrong-stack mutation — FIXED.** Activation receives and updates the actual Curios stack, with sound/visual parity coverage.
6. **Transient LIRM cross-world leak — FIXED.** Level unload/server stop clear state and negative ages are guarded.
7. **AE2 meteorite scan cross-world leak — FIXED.** Scan gates clear on level unload/server stop and remain separated from persistent meteor registry data.

### Original P2/P3 findings

8. **Client-local machine bar denominators — FIXED.** Machine display capacity/current/tier/status are synchronized from the authoritative server through the shared display schema.
9. **Stale README/CurseForge copy — FIXED.** Both now list NeoForge 21.1.247, Create 6.0.11, Sable 2.0.3, Aeronautics/Simulated 1.3.0, the 1.3.0 progression, and the corrected undead behavior.
10. **Dependency-range policy — ACCEPTED BY DESIGN.** Mod/API dependencies intentionally have open upper ends. Minecraft remains pinned to 1.21.1 and NeoForge intentionally remains capped below loader generation 22.
11. **GameTest non-termination — FIXED.** Each smoke task now launches its raw profile in an isolated process group, requires the explicit passing summary and shutdown marker within a five-minute bound, and terminates only that profile's lingering process tree. Old generated worlds are preserved under `/tmp` and each gate starts fresh. The full `releaseGate` completed unattended.
12. **Dedicated-server compatibility-pack pollution — FIXED.** Aeronautics and Simulated no longer import their optional integrations transitively; Curios is added explicitly only to the standard GameTest profile that exercises it. The minimal server mod list no longer contains Curios, JEI, either compass, or CC:Tweaked. Its remaining 12 hard-dependency `ClientLevel` probes must match the complete audited logger/class/dist signature and cannot exceed the audited count.
13. **Connected-chain cache session retention — FIXED.** Server stop clears the UUID cache and resets its prune clock.

## New 1.3.0 work audited after the original report

### Dipole Electromagnet and analog redstone

- Dipole block, block entity, registration, creative-tab entry, recipe, loot table, models, blockstate, localization, and generated resources are present.
- Analog force is carried through `MagneticField.forceOverride()` and physics now uses `field.force()` rather than the nominal tier force.
- Unit coverage includes magnetic-strength scaling and override serialization/stepping behavior.
- The standard 64-test profile passed with these changes present.

No concrete defect was found in the code/resource pass. Manual validation is still required for redstone levels 1/7/15, GUI/HUD readings, polarity orientation, active texture state, and force balance on differently sized ships.

### AeroPortals compatibility

- AeroPortals is a published optional dependency with no upper bound and an isolated test runtime.
- Transfer compatibility refreshes reconstructed ship state and remaps Railgun Remote bindings.
- The real transfer GameTest passed on AeroPortals 1.2.3.
- AeroPortals itself logged that a Simulated swivel-bearing `reattachConstraint` method was not found. The simple transfer test still passed, so this is not asserted as a Magnetization defect, but transferred ships containing swivel-bearing constraints need manual testing before compatibility is advertised without qualification.

### Immersive Aeronautics / Immersive Portals compatibility

- `immersive_portals_core` is a published optional dependency with no upper bound.
- Railgun state and installed/player-held remotes survive the real cross-dimensional Sable reconstruction path.
- Magnetic ship fields project through reachable portal apertures with transformed origin, axis, and scale, without recursive portal application.
- Both isolated tests passed: remote transfer and an Overworld electromagnet pulling a Nether ship through an Immersive Portals transform.
- The upstream runtime emits repeated client-class probes on a dedicated-server test profile. These are third-party test-runtime noise, not failures in the two Magnetization assertions.

### Multiblock previews, progression, accessibility, and diagnostics

- Preview/tooltip coverage now includes Railgun, Fusion Thruster, Tokamak, and other multiblock construction paths.
- Visible 1.3.0 progression advancements are present in addition to recipe unlocks.
- Fuel identity uses texture, badge/symbol, and tooltip text rather than hue alone.
- Polarity, strength, machine state, and fuel tier have secondary text/shape channels.
- `/magnetization debug audit` reports migration, transient-field, meteorite, and per-level cache state.

No missing registration/resource path was found for these additions.

### Additional pre-playtest GameTest coverage

- Railgun lifecycle NBT now covers `HOLDING`, `LAUNCHING`, and `COOLDOWN`, including counters, manual pairing, rail length, and buffered FE.
- Active Tokamak and Fusion Thruster persistence now validates queued fuel/container state, burn/fuel amount, generated/stored FE, formed state, interior count, and deterministic master ownership.
- Dipole persistence now covers strength, range, polarity, redstone level, and facing.
- Tokamak and Fusion Thruster boundary coverage now exercises the fixed Tokamak ring, minimum/maximum Fusion panels, oversized/incomplete/obstructed structures, break/reform, and stable shared master selection.
- Machine automation coverage now rejects wrong cells, items, buckets, and fluids; prevents extraction of active fuel; checks one-for-one empty-container handling; and proves a full Electrolyzer output stalls without consuming water or FE.
- Advancement coverage now performs real isotope inventory changes and real Fusion-panel formation/Railgun completion and firing before checking runtime criterion progress. Synthetic players are removed after each assertion to keep the shared server hermetic.
- Moving-ship coverage now includes powered Fusion and Railgun behavior on rotated, already-moving Sable ships. The Railgun fixture uses a temporary launcher deck so gravity cannot invalidate the channel assertion before the arc scans.

## Asset and resource audit

### Passed checks

- Every JSON file under main and generated resources parsed successfully.
- The clean Gradle resource/build pipeline completed without duplicate-resource failure.
- Every targeted original duplicate now has a different SHA-256: five fluid fills, three fuel cells, Lithium/Raw Lithium/Raw Gallium, Lithium/Helium-3/Hematite ores, deepslate Lithium/Hematite, and Railgun Remote/Repulsor Gun.
- All PNGs referenced by the newly added models are present and the JAR builds successfully.
- The built JAR has no duplicate ZIP entries and no unresolved `${...}` token in processed `neoforge.mods.toml`.
- Current JAR SHA-256 from this rerun: `4fbc8eacaf83180aeea9204e50a1694ba69477e609d01c0d3ec3579f8daff150`.

### Still requires visual playtesting

- Inspect every new item/block in inventory, hand, dropped form, item frame, JEI/REI/EMI, and active/inactive world state.
- Confirm Gallium Golem reads as the same material as solid gallium under normal lighting and shaders.
- Confirm fuel marks remain legible at GUI scale 1–4 and for common color-vision deficiencies.
- Check Dipole Electromagnet orientation and active texture in all six facings.
- Check transparent fluids, bucket layers, emissive expectations, model rotations, and Ponder/preview overlays with Sodium and Iris.

## Automated validation record

### Completed successfully in this rerun

- `./gradlew clean test build --no-daemon` — successful.
- 140 JUnit tests — 140 passed, 0 failures, 0 errors, 0 skipped.
- Standard dedicated GameTests — all 78 required tests passed under bounded supervision and the task exited successfully.
- AeroPortals isolated GameTest — 1/1 passed on AeroPortals 1.2.3 under bounded supervision.
- Immersive Aeronautics isolated GameTests — 5/5 passed on Immersive Portals core 6.0.7 under bounded supervision.
- `./gradlew smokeServerMinimal --no-daemon` — successful hard-dependencies-only boot and controlled shutdown; 12 exact, capped hard-dependency `ClientLevel` probes were recognized.
- `./gradlew releaseGate --no-daemon -PmagSmokeSeconds=20` — completed unattended and successfully exercised the build, unit tests, supervised standard GameTests, and minimal dedicated-server smoke.
- All main/generated JSON files parsed with `jq`.
- JAR duplicate-entry and unresolved-token checks passed.

### Harness behavior

Sable's physics thread can still keep a raw Minecraft GameTest JVM alive after its passing summary and normal server shutdown. The release-facing smoke tasks now treat that upstream/runtime behavior as bounded infrastructure: they require positive pass and shutdown markers, detect assertion failures and timeouts, and terminate only the isolated lingering process group. Raw `run*GameTestServer` developer tasks retain their native behavior; all documented release gates use the supervised smoke tasks.

## Required manual playtest matrix

### Fresh survival progression

- Confirm Lithium Ore and Helium-3 geodes generate at intended rarity and are discoverable without commands.
- Complete Water → Hydrogen → Deuterium → Tritium → Helium-3 using survival recipes and recipe viewers.
- Verify every filled/empty container transition and look for bucket/cell duplication or loss.
- Confirm the Field Manual accurately explains each step and every referenced recipe appears.

### Machines, emitters, and automation

- Electrolyzer: bucket, hopper, pipe, FE input, output stall, save/reload, and break/re-place.
- Tokamak: every cell, output rate, burn duration, hopper insertion, empty-container behavior, save/reload.
- Micro/MHD/Fusion thrusters: pipe/item input, fuel rejection, save/reload, break behavior, and retuned server capacities.
- Fusion panel and Tokamak: min/max sizes, every orientation, preview accuracy, breaking/reforming, master changes, reload while active.
- Railgun: auto/manual flow, all facings, min/max length, multiple rails, removal/use/unbind, unloaded and cross-dimension target, restart in every arc state.
- Standard and Dipole Electromagnets: redstone 0–15, polarity/facing, configured tier interaction, force scaling, GUI/HUD/tooltips, and save/reload.

### Ships, portals, and physics

- Test tiny, medium, and large Sable ships for force scaling, terminal speed, torque, rotated axes, connected-sublevel exclusion, and removal during force application.
- Validate Fusion Thruster and Railgun on moving/rotating ships.
- Transfer ships with inventories, Railgun Remotes, nested/connected sublevels, bearings, and swivel constraints through AeroPortals.
- Test Immersive Portals with rotated, scaled, one-way, paired, and adjacent portals; verify aperture clipping and ensure a ship cannot receive duplicate force through overlapping portal paths.
- Recheck ship magnetic state after assembly, disassembly, transfer, and reload.

### Multiplayer and configuration

- Join a dedicated server whose COMMON config differs from client defaults and compare every GUI/HUD value.
- Spam Curios activation and recoil inputs from two players; verify cooldown and source-stack behavior.
- Interact with the same menu, multiblock master, and Railgun Remote from two players simultaneously.

### World lifecycle and data safety

- Open two worlds in one client session and verify LIRM, AE scans, migrations, ship caches, fluid registries, compass scans, and HUD state do not carry across.
- Repeatedly restart after placing water/dirt/build blocks in both custom biomes and confirm the migration never reprocesses them.
- Upgrade a backed-up 1.2.x world and confirm migration is one-time and bounded.

### Client and assets

- Inspect all 1.3.0 models/textures at multiple GUI scales and with Sodium/Iris on and off.
- Verify fluid heights/transparency, particle sprites, machine status HUDs, active faces, overlays, and absence of missing-model textures.
- Confirm accessibility symbols/text remain understandable without relying on red/blue or fuel hue.

## Recommended release sequence

1. Execute the manual matrix on a disposable upgraded world and a fresh survival world, including portal ships with constraints.
2. Run normal and compatibility-heavy client smoke tests with the intended release modpack/renderers.
3. Re-run `releaseGate` and both supervised compatibility GameTest profiles after any final change.
4. Rebuild the release JAR, inspect it, record a new final SHA-256, and publish only that artifact.
