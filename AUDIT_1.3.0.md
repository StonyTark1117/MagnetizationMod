# Magnetization 1.3.0 — Final Release Audit

**Audit date:** 2026-08-09
**Target:** current `main` release candidate, 59 commits ahead of `origin/main` before this audit commit
**Minecraft / loader:** Minecraft 1.21.1; NeoForge 21.1.248 development runtime
**Published NeoForge range:** `[21.1.200,22.0)` — the upper bound is intentional
**Verdict:** no confirmed release-blocking defect remains in the automated audit. The candidate is ready for the required manual playtest matrix.

## Executive summary

The complete release gate now passes from a fresh GameTest world, including the build, 154 JUnit tests, 102 standard GameTests, a hard-dependencies-only dedicated-server boot, two absent-mod compatibility assertions, and all 16 configured present-mod compatibility profiles. Data generation is reproducible, every JSON resource parses, model texture references resolve, the processed JAR contains no duplicate paths or unresolved replacement tokens, and the final JAR hash is recorded below.

This rerun found test-infrastructure defects rather than a remaining gameplay defect: several tests assumed newly placed block entities or fluids would receive their first scheduled tick at a fixed time; the manual-playtest preset test synchronously generated remote chunks; and one magnetic item fixture survived into later tests and could be accelerated into an unloaded chunk. The tests now invoke the real production tick paths where appropriate, wait on observable state transitions, stage the large preset in generated spawn chunks, and remove their entities. The original 300-second GameTest supervisor remains in place and the final matrix passed without relaxing it.

Automated validation cannot establish presentation quality, balance, moving-ship feel, renderer behavior, or full survival progression. Those remain explicit release gates below.

## Audited scope

- 343 Java source files under `src/main/java`.
- 23 JUnit source files under `src/test/java`.
- 1,932 files under main/generated resources, including 1,444 JSON files.
- 59 commits relative to `origin/main` before this audit commit.
- The full 1.3.0 feature and remediation delta: progression, Lithium/Gallium/Helium-3 material families, inverse-water fusion gas flow, machine and railgun fixes, client/server display synchronization, manual playtest presets, Field Manual refresh, dependency metadata, and the expanded compatibility layer.
- The processed `magnetization-1.3.0.jar`, not only source-tree resources.

## Remaining confirmed issues

None found by the automated audit.

Manual playtesting remains mandatory and may still reveal visual, balance, interaction, renderer, or moving-physics issues that cannot be proven headlessly.

## Findings fixed during this rerun

1. **Optional-profile compilation regression — fixed.** A missing `MagConfig` import in the Induction Pad compatibility gate was exposed when the matrix reached Copycats. The final tree compiles in every isolated profile.
2. **Scheduled-tick GameTest assumptions — fixed.** Electrolyzer, Tokamak, emitter drain, Pyrrhotite heat, sensors, kinetic coils, railgun cooldown, fusion gases, Gallium phase changes, and Gallium field discovery now wait on observable behavior or drive their real production ticker instead of relying on a fixed newly-placed-block schedule.
3. **Fusion gas timing flake — fixed.** Direct GameTest block placement now explicitly seeds the initial production fluid tick, while subsequent flowing cells continue through the normal scheduler. Ceiling flow, edge waterfalls, sky-limit despawn, source travel, and feed-loss retraction passed in the final 102-test batch.
4. **Shared-world magnetic item leak — fixed.** The ore-break item-toggle fixture is discarded after its assertion. It can no longer be accelerated by a later test into an unloaded chunk and stall the server in chunk acquisition.
5. **Manual-playtest preset worldgen stall — fixed.** The multi-chunk lab/survival preset test stages at the already generated spawn area rather than synchronously generating several remote chunks around a randomized GameTest coordinate.
6. **Railgun remote lifecycle isolation — fixed.** The mock player is removed after remote use and the real railgun ticker drives cooldown/re-arm, preventing the test actor from becoming a new automatic target.

## Original audit resolution ledger

### Original P0 findings

1. **Railgun manual workflow — fixed.** Pairing/manual mode, installed and held remotes, firing, cooldown, unbinding, persistence, missing targets, and reconstructed ships have automated coverage.
2. **Restart-driven surface repaint exploit — fixed.** Surface repainting uses versioned per-level persistent chunk migrations and exposes audit state through the admin command.
3. **Byte-identical progression textures — fixed.** Buckets, fuel cells, Lithium/Gallium stages, Helium-3, railgun, machines, and Gallium Golem assets are differentiated where they convey different content.

### Original P1 findings

4. **Curios Repulsor cooldown bypass — fixed.** Hand and Curios activation share the server-authoritative path and cooldown; isolated Curios coverage passes.
5. **Curios Grapple wrong-stack mutation — fixed.** Activation updates the actual equipped Curios stack with sound/visual parity coverage.
6. **Transient LIRM cross-world leak — fixed.** Level unload and server stop clear transient state; invalid ages are guarded.
7. **AE2 meteorite scan cross-world leak — fixed.** Scan gates clear on lifecycle events and remain separate from persistent meteor data.

### Original P2/P3 findings

8. **Client-local machine display values — fixed.** Machine capacity/current/tier/status values use the synchronized display schema. Multiplayer payload and menu-data regressions are covered.
9. **Stale release copy — fixed.** README metadata now states the published NeoForge floor of 21.1.200, development runtime 21.1.248, and intended `<22.0` bound.
10. **Dependency range policy — accepted by design.** Minecraft remains exactly 1.21.1. NeoForge intentionally stays `[21.1.200,22.0)`. Other declared mod/API dependencies have no upper bounds.
11. **GameTest non-termination — fixed.** Release smoke tasks require explicit pass markers, reject assertion failures/timeouts, isolate each process group, preserve prior generated worlds under `/tmp`, and terminate only their own lingering test JVM.
12. **Dedicated-server compatibility pollution — fixed.** The minimal profile includes only the intended hard-dependency stack. Its 12 known third-party `ClientLevel` probes are matched exactly and capped.
13. **Connected-chain cache retention — fixed.** Server stop clears the UUID cache and resets its pruning clock.

## 1.3.0 feature audit

### Materials, storage, equipment, and progression

- Material-family completeness is enforced by JUnit coverage. Appropriate solid materials now have storage blocks, and appropriate wearable/tool materials have armor, sword, pickaxe, axe, shovel, hoe, and horse-armor families.
- Lithium has raw/storage blocks and a complete equipment family. Gallium, Magnetite, Maghemite, Hematite, Pyrrhotite, Titanomagnetite, and Ferromagnetic families are covered according to their intended roles.
- Helium-3 crystal/geode/storage naming and resources are internally consistent.
- Recipes, loot, tags, models, translations, creative-tab exposure, repair ingredients, tool tiers, armor materials, and equippable assets are covered by data and completeness tests.
- The generated survival preset intentionally supplies raw inputs but no completed Tritium or Helium-3 fuel, preserving progression validation.

### Machines and physics

- Fusion Thruster thrust direction and power input through any panel of the formed multiblock are fixed. HUD energy/current values are server-authoritative.
- Electrolyzer has distinct block and item art; its default Tokamak Coil recipe remains, with Immersive Engineering and TFMG coil alternatives enabled conditionally.
- Dipole Electromagnet HUD information represents both poles. Its block/config/registration/data paths are present, with analog-redstone force scaling and two-pole acceleration coverage.
- Railgun automatic/manual lifecycle, remote binding, speed cap, collision behavior, optional block breaking, persistence, advancements, and reconstructed-ship bindings are covered.
- Tokamak and Fusion Thruster formation boundaries, master ownership, persistence, fuel/container state, FE state, and automation rejection/stall rules are covered.
- Lithium ore, Helium-3 geodes, Magnetic Anomaly, and Petrified Forest placement remain attached to their intended biome/worldgen paths.

### Fusion fluids and Gallium

- Hydrogen, Tritium, and Helium-3 behave as rising gases: source travel, ceiling-supported horizontal flow, edge waterfalls, bounded lateral behavior, continuous columns, sky-limit handling, and source-loss retraction pass.
- Deuterium Oxide, Hydrogen, Tritium, Helium-3, and Liquid Lithium bucket overlays align with the established layered bucket art and remain visually distinct.
- Gallium freezing/melting, registry lifecycle, mixed/plain Lorentz wiring, solid Gallium appearance, and Gallium Golem material consistency are represented in source/assets and automated coverage where headless validation is meaningful.

### Accessibility, information, and playtest tooling

- Fuel identity uses hue plus symbols/badges and text. Polarity, machine state, and strength have secondary text/shape channels.
- JEI, REI, and EMI consume the shared information catalog; JER has Lithium/Helium-3 resource entries.
- Field Manual content and links were refreshed for the current progression and machines.
- Persistent `lab` and `survival` playtest presets stage machines, multiblocks, railgun lanes, inventory supplies, and persistence scenarios reproducibly.
- `/magnetization debug audit` exposes migration, transient-field, meteorite, and cache diagnostics.

## Compatibility and dependency audit

### Required stack

- Minecraft 1.21.1.
- NeoForge published range `[21.1.200,22.0)`; development runtime 21.1.248. The upper bound must remain.
- Create `[6.0.11,)`.
- Sable `[2.0.3,)`.
- Create Aeronautics `[1.3.0,)`.
- Create Simulated `[1.3.0,)`.
- TerraBlender `[4.1.0.8,)`.

### Isolated present-mod profiles passed

1. AeroPortals — 1/1.
2. Immersive Aeronautics / Immersive Portals — 5/5.
3. Create: Coasters Simulated — 2/2.
4. Create: Big Cannons — 3/3.
5. Create: New Age — 3/3.
6. Create Crafts & Additions — 3/3.
7. Immersive Engineering — 4/4.
8. Alex's Caves — 3/3.
9. Create: Tracks — 1/1.
10. Steam 'n' Rails — 1/1.
11. Create: Diesel Generators — 2/2.
12. Create: Copycats+ — 2/2.
13. Create: Enchantment Industry — 1/1.
14. Create: Ender Transmission — 3/3.
15. Create: The Factory Must Grow — 11/11.
16. Curios — 1/1.

The absent-mod profile passed 2/2 checks. Profiles run in separate Gradle processes so their classpaths cannot contaminate the minimal release profile or one another.

### Key compatibility behavior

- AeroPortals/Immersive Aeronautics reconstruction preserves Magnetization block-entity state and remaps installed/held Railgun Remotes.
- Coaster carts use the normal ship magnetic-force path. Grounded/unattached carts are not adopted by Structural Inducers; attached/assembled coaster structures are recognized. Field reaction and Structural Inducer adoption have independent config toggles.
- Optional integrations have master toggles, and foreign registry/class references remain soft when their mod is absent.
- Optional manifest ranges are open-ended. No new upper bound was introduced; NeoForge is the deliberate exception.

## Asset, resource, and package audit

### Passed automated checks

- `runData` completed with zero generated-file writes.
- All 1,444 main/generated JSON files parsed successfully with `jq`.
- Every Magnetization texture referenced by a model exists.
- Every Magnetization model referenced by blockstate/item definitions exists.
- Reviewed bucket composites place their fill layer consistently with the established Ferrofluid/Gallium bucket art.
- Reviewed Fusion Thruster, Railgun Emitter, Electrolyzer, solid Gallium, and Gallium Golem textures have the intended orientation/material relationships.
- Remaining identical texture hashes are explainable shared machine panels, blank dynamic MR armor bases, or adjacent compass animation frames; no progression-item overwrite was identified.
- The JAR contains no duplicate ZIP entries and no unresolved `${...}` token in processed `META-INF/neoforge.mods.toml`.
- `git diff --check` and the generated configuration-reference check passed.

### Final artifact

- File: `build/libs/magnetization-1.3.0.jar`
- Size: approximately 2.4 MiB.
- SHA-256: `39b04e2e4f743a681e796f26c76ce998fb5b9e767ca31363aa4224b382c080c4`

### Still requires visual playtesting

- Inspect every new item/block in inventory, hand, dropped form, item frame, recipe viewers, and active/inactive world state.
- Confirm Gallium Golem reads as solid Gallium under normal lighting, Sodium, Iris, and representative shader packs.
- Check bucket layers, transparent fluids, particles, machine status HUDs, face rotation, multiblock previews, and Ponder overlays.
- Check fuel marks and polarity indicators at GUI scales 1–4 and with common color-vision deficiencies.

## Automated validation record

- `./gradlew releaseGate --no-daemon -PmagSmokeSeconds=20` — passed: build, 154/154 JUnit tests, 102/102 standard GameTests, and minimal dedicated-server smoke.
- `./gradlew releaseMatrixGate --no-daemon -PmagSmokeSeconds=20` — passed all isolated profiles in 12m 7s.
- JUnit summary — 154 tests, 0 failures, 0 errors, 0 skipped.
- Standard GameTest summary — 102 required tests passed. The final direct run completed in 17.97s; the final fresh matrix run completed in 1.329m while Sable persisted its sublevels.
- Minimal server — clean boot/shutdown; 12/12 exact known hard-dependency client-class probes recognized.
- `./gradlew runData --no-daemon` — successful, no output changes.
- `python3 scripts/generate-config-reference.py --check` — successful.
- All JSON parse, model/texture existence, JAR duplicate-entry, processed-token, static-marker, and whitespace checks passed.

Expected development noise remains limited to known third-party mixin/refmap/class probes and Sable/Flywheel warnings. The minimal-server smoke gate validates the exact accepted hard-dependency probe count so a new client-class leak cannot pass silently.

## Required manual playtest matrix

### Fresh survival progression

- Confirm Lithium Ore and Helium-3 geodes generate at intended rarity and are discoverable without commands.
- Complete Water → Hydrogen → Deuterium → Tritium → Helium-3 using survival recipes and at least one recipe viewer.
- Verify every bucket/cell transition, storage block, tool, armor set, repair ingredient, enchantability, durability, mining level, and loot path.
- Confirm the Field Manual accurately describes every step and recipe link.

### Machines, emitters, and automation

- Electrolyzer: bucket, hopper, pipe, FE input, optional coil recipes, output stall, save/reload, and break/re-place.
- Tokamak: each fuel, rates/durations, automation, empty-container handling, formation limits, master changes, and reload while active.
- Micro/MHD/Fusion thrusters: fuel rejection, piping/items, FE from every Fusion panel, thrust direction, moving/rotating craft behavior, HUD updates, save/reload, and break behavior.
- Railgun: automatic/manual flow, all facings and lengths, remote install/use/unbind, unloaded/cross-dimension targets, restart in every arc state, speed cap, and breaking-disabled collision.
- Standard and Dipole Electromagnets: redstone 0–15, both poles, facing/polarity, config toggles, HUD/tooltips, and save/reload.

### Ships, portals, coasters, and physics

- Test tiny/medium/large ships for force scaling, terminal speed, torque, rotated axes, connected-sublevel exclusion, and removal during force application.
- Transfer inventories, remotes, nested/connected sublevels, bearings, and swivel constraints through AeroPortals.
- Test rotated/scaled/one-way/adjacent Immersive Portals, aperture clipping, and duplicate-force prevention.
- Test coaster carts both unattached and attached to track. Magnets should treat an assembled cart like a ship; the Structural Inducer must not adopt a loose ground cart.
- Recheck magnetic state after assembly, disassembly, transfer, derailment, reattachment, and reload.

### Multiplayer and configuration

- Join a dedicated server whose COMMON config differs from client defaults and compare every GUI/HUD value.
- Exercise Curios grapple/repulsor input from two players and spam packets to validate cooldown/source-stack behavior.
- Interact with the same menu, multiblock master, Railgun Remote, and coaster/ship from two players simultaneously.

### Lifecycle, upgrade, client, and assets

- Open two worlds in one client session and verify transient fields, AE scans, migrations, caches, fluid registries, compass scans, and HUD state do not carry across.
- Upgrade a backed-up 1.2.x world and confirm surface migration is one-time and bounded.
- Inspect all 1.3.0 models/textures with Sodium/Iris on and off and at multiple GUI scales.
- Verify fluids, particles, machine faces/status, overlays, Ponder scenes, and accessibility symbols without relying on color alone.

## Release sequence

1. Run the manual matrix on disposable fresh and backed-up upgraded worlds, including a two-player dedicated server.
2. Run normal and compatibility-heavy client smoke tests with the intended renderer/shader stack.
3. Rerun `releaseMatrixGate` after any code/resource/config change.
4. Rebuild the JAR, repeat package inspection, record the new SHA-256 if it changes, and publish only that artifact.
