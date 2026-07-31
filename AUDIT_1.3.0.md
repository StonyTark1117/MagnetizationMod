# Magnetization 1.3.0 — Full Release Audit

**Audit date:** 2026-07-31  
**Target:** current `main` worktree, 17 commits ahead of `origin/main` at audit start  
**Minecraft / loader:** Minecraft 1.21.1, NeoForge 21.1.233  
**Policy:** audit only. No gameplay, resource, configuration, or asset fixes were made as part of this report.

## Executive verdict

**Not release-ready yet.** The candidate compiles and has unusually strong automated coverage, but the audit found three release-blocking defects:

1. The Railgun Remote's normal player workflow cannot keep the railgun in manual mode while the remote is held for firing.
2. The biome surface-repaint system reprocesses the same chunks after every server restart. It can overwrite player-placed surface blocks and can be deliberately used to convert renewable water/dirt into ferrofluid and ores.
3. Several distinct 1.3.0 resources use byte-identical textures, including every fusion-fluid bucket fill, all three fuel cells, Lithium/Raw Lithium/Raw Gallium, and Lithium Ore/Helium-3 Crystal/Hematite Ore. The new progression is not visually distinguishable in inventory or world.

There are also confirmed multiplayer, cross-world lifecycle, documentation, and release-tooling problems listed below. These should be resolved or consciously accepted before publishing 1.3.0.

## Scope and evidence standard

The audit covered:

- 271 Java source files at audit start, organized across API, physics, content, networking, menus, client rendering, compatibility, registries, commands, data generation, and world generation.
- 1,600+ resource files across hand-written and generated resources.
- Block/item/entity registration, capabilities, inventory persistence, recipe/loot/tag coverage, advancement and Patchouli resources, models, blockstates, textures, localization, packaging, dedicated-server loading, unit tests, and GameTests.
- The 6,649-line 1.3.0 change from `origin/main`, including the Fusion Thruster, Railgun, Electrolyzer, fusion fuels, lithium/Helium-3 worldgen, machine rebalance, and the accumulated audit fixes already committed locally.

An item is called a **confirmed issue** only when the current code/resources directly demonstrate it or a runtime validation reproduced it. Items needing human playtesting are kept in a separate section and are not asserted as bugs.

### Concurrent worktree caveat

During this audit, changes not made by the auditor appeared in:

- `src/main/java/com/stonytark/magnetization/Magnetization.java`
- `src/main/java/com/stonytark/magnetization/gametest/MagGameTests.java`
- new file `src/main/java/com/stonytark/magnetization/content/MachineFuelItemHandler.java`

Those changes add automated item input for several machines and related GameTests. They were preserved. The final compile/unit-test and 48-test GameTest passes include them.

## Confirmed issue list

### P0 — Release blockers

#### 1. Railgun manual mode cannot be used through the documented player workflow

**Evidence**

- `RailgunEmitterBlockEntity.onRemoteSlotChanged()` sets `manualMode` directly from whether a remote is physically present in the slot (`RailgunEmitterBlockEntity.java`, lines 105–115).
- Removing the remote sets manual mode false and returns a holding arc to `IDLE`.
- `RailgunRemoteItem.use()` requires the bound remote to be in a player's hand (`RailgunRemoteItem.java`, lines 52–67).
- The manual workflow GameTest leaves the remote inside the emitter and calls `master.requestFire()` directly (`MagGameTests.java`, lines 912–952). It never removes the remote, puts it in a player's hand, or invokes `RailgunRemoteItem.use()`.

**Impact**

A normal player cannot both keep the railgun in manual/holding mode and hold the same bound remote to fire it. The headline manual boarding-and-launch feature is therefore unreachable without commands, code calls, or duplicated/copied remotes.

**Fix direction**

Persist the binding/manual state independently of slot occupancy, or create a distinct pairing/trigger design. Add an end-to-end test that inserts the remote, removes it into a player's hand, confirms the arc remains `HOLDING`, invokes the real item `use()` method, and observes launch.

#### 2. Surface repaint repeats after every restart, overwrites eligible player blocks, and enables renewable resource conversion

**Evidence**

- `ChunkSurfaceRepaintHandler.SEEN` is an in-memory set and is cleared on every `ServerStoppedEvent` (`ChunkSurfaceRepaintHandler.java`, lines 54–67).
- On the next session, every nearby relevant chunk is processed again and marked seen only for that session (`lines 69–125`).
- In an Anomaly biome, any surface water column is replaced with ferrofluid up to 12 blocks deep (`lines 158–173`).
- Eligible surface grass, dirt, coarse dirt, podzol, sand, gravel, or snow is replaced with a deterministic mix containing Magnetite Ore, iron ore, Hematite, Maghemite, Pyrrhotite, Titanomagnetite, gold ore, and Raw Magnetite Block (`lines 175–235`).
- Petrified Forest columns similarly replace eligible surface blocks with coarse dirt (`lines 189–207`).
- The code has no generated-chunk marker, persistent per-chunk marker, age test, or player-placement distinction.

**Impact**

- A player landscaping with eligible vanilla surface blocks in either custom biome can have those blocks changed after a restart.
- Surface water builds in an Anomaly biome can become ferrofluid after a restart.
- A player can intentionally place renewable water or dirt at suitable coordinates, restart the server, and convert it into ferrofluid or deterministic ore outputs. This is a survival resource exploit, not just cosmetic repainting.
- Existing worlds are reprocessed every session rather than only during generation or a one-time migration.

**Fix direction**

Move the transformation into generation where possible. If a compatibility repaint remains necessary, persist a per-chunk version marker in chunk data/SavedData and process each chunk once per migration version. Provide an explicit admin migration command for already-generated chunks rather than resetting the gate every restart.

#### 3. Major 1.3.0 progression assets are byte-identical and visually ambiguous

**Evidence**

SHA-256 comparison found these exact duplicates:

- `textures/item/deuterium_oxide_fill.png`, `hydrogen_fill.png`, `tritium_fill.png`, `helium_3_fill.png`, and `liquid_lithium_fill.png` are identical (`e5bdfd…`). Their item models all place the same fill over the same vanilla bucket, and no item-color handler tints them.
- `textures/item/deuterium_cell.png`, `tritium_cell.png`, and `helium_3_cell.png` are identical (`0121d1…`).
- `textures/item/lithium.png`, `raw_lithium.png`, and the existing `raw_gallium.png` are identical (`897ade…`).
- `textures/block/lithium_ore.png`, `helium_3_crystal_block.png`, and `hematite_ore.png` are identical (`6d1d63…`).
- `textures/block/deepslate_lithium_ore.png` and `deepslate_hematite_ore.png` are identical (`c8267b…`).
- `textures/item/railgun_remote.png` and `repulsor_gun.png` are identical (`1fcbbc…`).
- Several new machine faces are also exact copies of older machines: Fusion Thruster core = Micro Thruster nozzle; Railgun muzzle/side = Tokamak Controller top/side. Reuse may be stylistic, but the named progression items above need clear identification.

**Impact**

Players cannot reliably distinguish fuels, cells, lithium stages, Helium-3 crystals, and some tools without hovering text. This is especially problematic in inventories, JEI/REI/EMI, automation filters, guide screenshots, and color-vision/accessibility contexts. The Helium-3 Crystal also visually presents as ordinary red-flecked ore despite being described as a rare luminous crystal.

**Fix direction**

Give each progression tier a distinct silhouette plus color/value pattern; do not rely on hue alone. At minimum differentiate bucket fills, cell markings, lithium/raw lithium, Helium-3 crystal geometry/palette, and the Railgun Remote.

### P1 — High-priority confirmed issues

#### 4. Curios Repulsor Gun packets bypass the cooldown check

**Evidence**

- Normal hand use checks `player.getCooldowns().isOnCooldown(this)` before firing (`RepulsorGunItem.java`, lines 61–74).
- The serverbound Curios handler calls `gun.fire()` directly and only adds a cooldown afterward (`UseCurioPayload.java`, lines 77–83).
- It never checks whether the gun is already on cooldown.
- The normal client sends once per key press, but the server handler is the security boundary and accepts repeated valid packets from a modified or rapidly scripted client.

**Impact**

A client can fire the ship impulse/entity push/self-recoil path every packet, bypassing the configured rate limit and potentially creating a gameplay and server-load exploit.

**Fix direction**

Check the server player's cooldown before calling `fire()`, or expose a shared `tryFire()` method used by hand and Curios paths so validation, visual stamping, sounds, firing, and cooldown remain atomic and identical.

#### 5. Curios Grapple invokes hand-use against the wrong ItemStack

**Evidence**

- The Curios handler finds the actual grapple stack but calls `grapple.use(..., InteractionHand.MAIN_HAND)` without passing that stack (`UseCurioPayload.java`, lines 69–75).
- `MagneticGrappleItem.use()` obtains `player.getItemInHand(MAIN_HAND)` and writes the `FIRED_AT` component to it (`MagneticGrappleItem.java`, lines 71–92).
- Therefore the Curios grapple's visual state is written to the unrelated main-hand stack (or an empty stack), not the Curios item.
- The Curios Repulsor path has the opposite inconsistency: it fires directly and never stamps its Curios stack or plays the normal hand-use sound.

**Impact**

Curios activation is observably different from normal item use, can attach Magnetization's fired-time component to an unrelated held item, and does not reliably display the intended glow/sound feedback on the actual Curios item.

**Fix direction**

Refactor both items around a shared server-side activation method that accepts the real source stack. Use that method from both hand and Curios entry points.

#### 6. Transient LIRM fields leak across worlds and can become over-strength ghost fields

**Evidence**

- `TemporaryLirmFields.ENTRIES_BY_LEVEL` is keyed only by `ResourceKey<Level>` such as `minecraft:overworld`, not by the actual level/server instance (`TemporaryLirmFields.java`, lines 53–66).
- There is no unload or server-stop handler that clears this map.
- A second world opened in the same JVM uses the same dimension key and receives entries from the first world.
- Field age is calculated as `newWorldGameTime - oldBornTick` (`lines 160–180`). If the new world's clock is lower, age is negative, expiration does not occur, and `remaining = 1 - age/duration` becomes greater than 1, increasing the stale field's range above its configured base.

**Impact**

Saving/quitting one world and opening another in the same game session can carry transient magnetic fields into unrelated coordinates in the second world. Depending on game times, those fields can persist much longer and have an inflated range.

**Fix direction**

Key by the actual `ServerLevel` using weak level ownership, or clear the dimension entry on `LevelEvent.Unload` and clear all entries on server stop. Clamp negative ages defensively.

#### 7. AE2 meteorite scan gates leak across worlds and suppress scanning in a second world

**Evidence**

- `AeMeteoriteScanner.SCANNED_CHUNKS` is keyed only by dimension resource key (`AeMeteoriteScanner.java`, lines 51–70).
- It has no level-unload or server-stop cleanup.
- Once a chunk coordinate has been scanned in one world, the same coordinate in a second world opened in the same JVM is treated as already scanned.

**Impact**

AE2 meteorites in a second single-player world can fail to register their magnetic fields for chunk coordinates visited in the first world. The set also grows across sequential world sessions.

**Fix direction**

Own scan state by the actual level instance, clear it on unload/server stop, or avoid the transient scan gate and rely on the SavedData registry's idempotent registration.

### P2 — Medium-priority confirmed issues

#### 8. Multiplayer machine bars use the client's unsynchronized COMMON config as their denominator

**Evidence**

- The changelog explicitly states that COMMON config values are not synced server-to-client and that the server's copy is authoritative (`CHANGELOG.md`, line 167).
- `MachineScreen.fluidBarMax()` reads local client `MagConfig` values for Micro Thruster, Fusion Thruster, MHD Jet, Electrolyzer, and Tokamak burn duration (`MachineScreen.java`, lines 27–51).
- The menu syncs current fluid/burn values but not the authoritative maximum values required by these bars.
- WTHIT providers also derive some displayed timings/ranges from local config.

**Impact**

When a multiplayer server retunes a tank capacity or burn duration, clients with default/different COMMON files can see incorrect fill percentages and timing text even though server simulation is correct.

**Fix direction**

Sync each required maximum through `MachineMenu`/BE update data, or implement explicit server-to-client config synchronization for display-relevant values.

#### 9. Public documentation is stale and contradicts the 1.3.0 candidate

**Evidence**

- `README.md` lines 5–12 and `CURSEFORGE_Description` lines 97–104 still list NeoForge 21.1.230+, Create 6.0.9+, Sable 1.2.2+, Aeronautics 1.2.1+, and Simulated 1.2.1+.
- The candidate builds against NeoForge 21.1.233, Create 6.0.11, Sable 2.0.3, Aeronautics 1.3.0, and Simulated 1.3.0 (`gradle.properties`, lines 5–31).
- README and CurseForge copy do not document the Fusion Thruster, Railgun, Electrolyzer, fusion-fuel ladder, lithium/Helium-3 worldgen, or new machine behavior.
- `CURSEFORGE_Description` line 10 says undead mobs are pulled intrinsically, while the current `magnetizable` tag was deliberately trimmed to remove ordinary undead; armor can still make them susceptible.

**Impact**

Users can install an unsupported old dependency stack and miss the primary content of the release. The mob description also sets incorrect expectations.

**Fix direction**

Update both public descriptions from the already comprehensive 1.3.0 changelog, and make the supported dependency floor/range match the release metadata.

#### 10. Dependency metadata accepts untested future major APIs

**Evidence**

- `neoforge.mods.toml` declares Create `[6.0.10,)`, Sable `[2.0.0,)`, Aeronautics `[1.3.0,)`, and Simulated `[1.3.0,)` without upper bounds (`lines 35–65`).
- These are direct compile-time/API dependencies, including physics and sub-level APIs.

**Impact**

An incompatible future major release can satisfy dependency resolution and proceed into classloading/runtime failure instead of producing a clear dependency error. This is a release-hardening risk; no current configured dependency failed during the audit.

**Fix direction**

Bound the supported major lines, for example Sable `<3` and Create `<7`, then widen after compatibility validation.

### P3 — Release engineering and maintainability

#### 11. GameTest completes but its Gradle task does not terminate

**Evidence**

- Both audited runs printed a successful completion marker (44 tests on the earlier snapshot and 48 tests on the current worktree) and began normal Minecraft shutdown.
- The JVM remained alive afterward due to the Sable physics thread and the Gradle task stayed at 91% until manually interrupted.
- `build.gradle` already contains pre-run cleanup for lingering prior GameTest JVMs, confirming this is a known recurring harness condition, but it does not make the current invocation exit cleanly.

**Impact**

The test cannot serve as a reliable unattended CI gate and can leave background JVMs/session locks between runs.

**Fix direction**

Fix shutdown at the Sable integration boundary if possible, or wrap the test task with a bounded process supervisor that treats the explicit successful GameTest completion marker plus normal server shutdown as success and then terminates the lingering process safely.

#### 12. Dedicated-server test runs are polluted with client-only compatibility mods

**Evidence**

- `runtimeClasspath` extends the entire `localRuntime` configuration for every non-datagen run (`build.gradle`, lines 37–45).
- `localRuntime` includes Iris, Sodium, Immersive Aircraft, JEI/JER, and other client-oriented smoke-test dependencies.
- The dedicated GameTest startup emitted repeated `Attempted to load class net/minecraft/client/multiplayer/ClientLevel for invalid dist DEDICATED_SERVER` errors. It still reached the test server and passed all required tests.

**Impact**

The server smoke test is noisy, makes genuine side violations harder to spot, and does not represent the dependency set a real dedicated server should load.

**Fix direction**

Split client smoke-test dependencies from common/server compatibility dependencies and apply them only to `runClient`. Keep a minimal `runServer`/`runGameTestServer` classpath for a clean physical-server gate.

#### 13. Connected-chain cache is not explicitly reset between server sessions

**Evidence**

- `SableBridge.CHAIN_CACHE` is a static UUID map with tick-based expiry and no server-stop reset (`SableBridge.java`, lines 105–121).
- `lastChainPruneTick` also persists. If a new world's game time is lower, pruning is delayed until its clock passes the old session's tick threshold.

**Impact**

Primarily a bounded session-to-session memory retention issue. A functional collision would require a reused ship UUID, so no gameplay failure is asserted here.

**Fix direction**

Clear the cache and reset the prune clock on server stop.

## Asset and resource audit

### Passed checks

- Every JSON file under hand-written and generated resources parsed successfully.
- No duplicate JSON object keys were found.
- Every registered block has a blockstate and block translation.
- Every registered item has an item model and translation path (special BlockItems correctly use block translations).
- Every referenced Magnetization blockstate model, model parent, model texture, and particle texture exists with matching case.
- Every PNG decoded successfully; no corrupt or non-PNG files were found under texture paths.
- Creative tab coverage is complete: all 166 declared `MagItems` fields are referenced by the tab, with disabled-content filtering applied centrally.
- Solid blocks have expected loot-table coverage. Fluid blocks and hardened MR fluid intentionally use fluid-specific behavior rather than ordinary self-drop loot.
- All 269 detected config leaf keys have matching localization entries.
- The built JAR contains no duplicate entries, unresolved `${...}` metadata tokens, `.DS_Store`, `Thumbs.db`, or source-art leftovers.

### Visual findings requiring correction or playtest

- The exact duplicate progression textures in confirmed issue 3 should be corrected before screenshots or release media are produced.
- The newly added texture set is technically valid at 16×16 and has working alpha, but a real client pass is still required for model rotations, active/inactive faces, emissive expectations, and fluid tint appearance.
- Fluid blocks intentionally reuse vanilla water sprites with distinct runtime tint colors. That covers placed fluids, but not the bucket item layers, which currently have identical untinted fill PNGs.

## Gameplay/system audit notes that passed code and automated review

These areas were explicitly checked and no concrete defect was found in the current candidate:

- Fusion Thruster panel master forwarding for fluid and FE after formation, panel-size overflow clamping, invalid-panel LIT cleanup, rotation/mirror support, fuel type gating, and update syncing.
- Electrolyzer water input, hydrogen-only output drain, FE consumption, bucket interactions, persistence, LIT state, and renderer registration.
- Railgun pairing, rail-length scan, three-rail dissipation logic, claim/break-event protection path, cooldown lifecycle, and entity/ship acceleration. The manual player workflow remains broken as documented above.
- Machine inventories are persisted and dropped on genuine block removal; capability wrappers respect each slot's insertion predicate.
- Unit/network ordinal bounds checks prevent malformed Curios payload enum values from indexing outside the enum.
- Block/item registrations, creative-tab exposure, recipes, loot, tags, localization, and Patchouli JSON load successfully in the dedicated runtime.
- The current dedicated server reaches gameplay and runs tests despite errors originating from the intentionally oversized development compatibility classpath.
- Fluid source registries are rebuilt on chunk load, addressing save/reload loss for magnetized ferrofluid, ferrofluid creep, gallium, mixed gallium, and MR fluid.
- Existing player transient-state cleanup covers logout, respawn/clone, dimension change, and server stop for grapple, hover, and hoe-dowse state.

## Missing features and proposed additions

These are **proposals, not bugs**. They are based on gaps visible in the current implementation and should be prioritized only after confirmed defects are fixed.

### Recommended for 1.3.x

1. **Railgun/Fusion multiblock build preview.** Add a wrench/goggle/Ponder overlay showing the required frame, master cell, facing, invalid edge, and current effective dimensions. The code exposes formed/size state, but builders otherwise diagnose malformed panels by trial and error.
2. **Server-synced machine display schema.** Beyond fixing current bar desync, formalize a small data schema for capacity, current value, tier, and status so GUI, Jade, WTHIT, TOP, and future displays all consume the same authoritative numbers.
3. **1.3.0 progression advancements.** Current 1.3 advancement hits are recipe unlocks, not visible gameplay milestones. Add advancements for first Electrolyzer hydrogen, first Tritium/Helium-3, forming a Fusion Thruster, and completing/firing a Railgun.
4. **Persistent world-migration framework.** Replace one-session repaint gates with versioned persistent chunk migrations. This can also support future worldgen/resource upgrades safely.
5. **Automation GameTests for every capability.** The concurrent worktree adds Tokamak, Motor, and bucket-machine tests. Extend coverage to MHD magnet insertion, Fusion non-master insertion forwarding, empty-bucket extraction after an actual tick, and capability invalidation when panel master changes.
6. **Real Curios activation tests.** Exercise actual serverbound payload handling, cooldown enforcement, real source-stack component changes, sound/visual parity, logout, and spam attempts.

### Optional polish/features

7. **Fuel identity overlays/tooltips.** In addition to unique textures, show compact isotope/tier markings (H, D, T, He-3) and relative thrust/efficiency in JEI/EMI/Patchouli.
8. **Railgun remote quality of life.** Show dimension and rail name/custom label in the tooltip, provide an explicit unbind action, and give clear feedback for missing/unloaded/different-dimension emitters instead of silently passing.
9. **Admin audit command for world effects.** Extend debug commands to list active transient LIRM fields, repaint migration version, registered AE meteor fields, and per-level cache sizes. This would make future cross-world lifecycle bugs visible in playtesting.
10. **Release smoke-test profiles.** Provide separate Gradle tasks for minimal dedicated server, normal client, compatibility-heavy client, and GameTest. A clean minimal-server log should become a release gate.
11. **Accessibility pass.** Ensure polarity, fuel tier, machine activity, and field strength never depend on red/blue or hue alone; use shapes, symbols, animation, or text as a second channel.

## Required manual playtest matrix

Automated checks cannot prove visual feel, balance, or GUI interaction quality. These are release gates, not claims of current failure.

### Fresh survival progression

- Confirm Lithium Ore and Helium-3 geodes generate at intended rarity and are discoverable without commands.
- Complete Water → Hydrogen → Deuterium → Tritium → Helium-3 progression using survival recipes and recipe viewers.
- Verify every filled/empty container transition and look specifically for bucket duplication or loss.
- Confirm the Field Manual accurately explains each step and every referenced recipe appears.

### Machines and automation

- Electrolyzer: bucket, hopper, pipe, FE input, hydrogen extraction, full-output stall, save/reload, break/re-place.
- Tokamak: all three cells, output rates, burn duration, hopper insertion, empty-container behavior, save/reload.
- Micro/MHD/Fusion thrusters: pipe input, item automation where exposed, fuel mismatch rejection, save/reload, break behavior, and server-retuned capacities.
- Fusion panel: minimum and maximum sizes, every orientation, mirrored/rotated structures, forming after pre-filling multiple cells, breaking/reforming, master changes, FE/fluid input on every interior cell, world reload while firing.
- Railgun: auto and manual workflow, all six facings, min/max length/gap, 3+ rails, multiple nearby railguns, remote removal/use, unloaded target emitter, dimension mismatch, target entering/leaving during launch, obstruction/claim protection, and restart during each arc state.

### Ships and physics

- Test tiny, medium, and large Sable ships for force scaling, terminal speeds, torque, rotated local/world axes, connected sub-level exclusion, and ship removal during force application.
- Validate Fusion Thruster and Railgun on a moving/rotating craft, not only static GameTest arrangements.
- Recheck magnetic switch ship-on-ship detection after assembly, disassembly, dimension transfer, and world reload.

### Multiplayer/configuration

- Join a dedicated server whose COMMON config differs from the client's defaults and compare every GUI/HUD maximum and timing.
- Spam Curios activation inputs and verify cooldowns server-side after fixes.
- Test two simultaneous players interacting with the same machine/menu/remote.

### World lifecycle and data safety

- Create two worlds in one client session and verify LIRM, AE meteor scanning, surface migration, ship caches, fluid registries, compass scans, and HUD state do not carry across.
- In each custom biome, place a controlled grid of water, dirt, grass, sand, gravel, containers, redstone, and decorative builds; restart repeatedly and verify no player blocks are transformed after the repaint fix.
- Upgrade a copy of an existing 1.2.x world and confirm migration is one-time, bounded, backed up, and does not touch builds.

### Client/assets

- Inspect every 1.3 item in creative inventory, JEI/REI/EMI, dropped form, hand, item frame, and GUI slot.
- Inspect every new block in all orientations with active/inactive state, Sodium, Iris shaders on/off, and vanilla renderer where supported.
- Verify Electrolyzer water height, transparent fluid rendering, particle sprites, machine status HUDs, and no missing-model purple/black textures.

## Validation record

### Completed successfully

- `./gradlew test build` — successful on the pre-concurrent snapshot.
- 126 JUnit tests — 126 passed, 0 failures, 0 errors, 0 skipped.
- Dedicated GameTest runtime on the earlier snapshot — all 44 then-current required tests passed in 9.580 seconds.
- Current worktree `./gradlew test compileJava` after the concurrent automation changes — successful.
- Current worktree `./gradlew test build` after those changes — successful; 126 unit tests passed with no failures, errors, or skips.
- Current-worktree dedicated GameTest runtime — all 48 required tests passed in 1.252 minutes. After the server began shutdown, the Gradle task again remained alive and required manual interruption, reproducing issue 11.
- All resource JSON parse and duplicate-key checks — passed.
- Registration/model/texture/localization/creative-tab/package consistency checks — passed, aside from the confirmed byte-identical visual assets.

### Runtime log context

The compatibility-heavy GameTest server also logged errors from third-party development dependencies:

- client `ClientLevel` class probes on a dedicated-server dist;
- Create: New Age recipes using an unavailable `neoforge:never` condition codec;
- Supplementaries/Moonlight missing optional Alex's Caves target items.

These did not originate from Magnetization resources and did not stop either GameTest run. They demonstrate why the minimal dedicated-server profile proposed above is needed.

### Final artifact

The release artifact must be rebuilt after all concurrent/current changes and any eventual fixes. Do not publish the earlier audit artifact hash as the final release binary.

## Recommended release sequence

1. Fix P0 issues: Railgun Remote workflow, persistent/safe surface migration, and distinct 1.3 assets.
2. Fix Curios server validation/source-stack handling and cross-world static state cleanup.
3. Sync authoritative display capacities and update public documentation/dependency ranges.
4. Add focused regression tests for each fix.
5. Run the full manual matrix on a disposable copy of a real world plus fresh survival world.
6. Run clean minimal dedicated-server and compatibility-heavy client smoke tests.
7. Rebuild the JAR, inspect its contents, record its final SHA-256, and only then publish 1.3.0.
