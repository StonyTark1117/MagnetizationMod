# Changelog

## 1.2.0 — (in development)

### New content
- **EMP Flux Charge**: a single-use electromagnetic bomb. Power it with redstone and it detonates — every magnetic emitter in a 12-block radius goes dark for 10s and every machine/battery's stored FE is wiped, with a flash + thunderclap, then the charge is consumed. Fries electronics, not terrain (no block damage).
- **Kinetic Induction Coil**: Faraday's law as a generator — a magnetic ship moving through the coil induces an EMF proportional to its speed, so the coil generates FE (pushed to adjacent machines/cables) and emits a redstone pulse while the magnet passes. No fuel; pure kinetic-to-electric. Pairs with Lenz braking and propulsion tracks.
- **Barkhausen Noise Generator**: a true-RNG block. With a magnet (any `#magnetization:anvil_dampeners` block) placed against it, its iron domains flip chaotically and it emits a rapid, unpredictable stream of random redstone strengths; idle otherwise. For random lighting, casino logic, or unpredictable spawners.
- **Magnetostrictive Sensor**: a buried magnetic tripwire. Nearby motion — a player or mob running/jumping within 8 blocks — flexes its magnetostrictive core and it reads out an analog redstone signal (stronger the closer/faster), decaying when things go still. Detects through blocks, so it makes an invisible perimeter alarm or seismic mob detector.
- **Induction Charging Pad**: a block that buffers FE (fed from cables/generators) and wirelessly tops up every energy-storing item a nearby player carries — main inventory, armor, offhand, and Curios slots (when Curios is installed). Electromagnetic induction, gamified.
- **Lenz-effect braking**: a magnetic ship moving over conductive non-ferrous blocks (`#magnetization:eddy_conductors` — copper, aluminium, …) is dragged to a slow float/coast by induced eddy currents, exactly like a magnet falling through a copper pipe. Lay a copper track to brake or gently lower a craft. Tunable via `physics.lenzBrakingStrength` (0 disables).
- **Magnetic Item Frame**: a thin magnetic wall plate that holds one item/tool/armor stuck flat to it for display (no rotation) — right-click to stick on, empty-hand to pop off. Crafted from a vanilla item frame + a magnetic plate.
- **Anvil magnetic dampener**: place any magnet or magnetic storage block (`#magnetization:anvil_dampeners`) touching an anvil — its field steadies the anvil so it never degrades, and deadens the use-clang.
- **Ore Dowsing Compass**: a compass whose needle points to the nearest metallic ore vein (anything in `#magnetization:metallic_ores` — our ferrous ores plus vanilla/modded `#c:ores/*`). Anvil-combine it with a specific ore to **tune** it to track only that ore. Like the other compasses, the needle scrambles inside the Magnetic Anomaly biome. The world scan is bounded + throttled (nearest-first expanding sweep, capped, re-run every second). Crafted from a vanilla compass + 2 raw magnetite.

### Dependencies
- **Updated the Create:Aeronautics + Sable stack to latest (still MC 1.21.1)**: Sable **2.0.1** (from 1.2.2), Create Aeronautics **1.3.0**, Create Simulated **1.3.0**, Sable Companion **1.6.0**, Create **6.0.11-292**, NeoForge **21.1.233**. Sable's `mods.toml` range moved to `[2.0.0,3.0.0)`. Sable 2.0's reworked physics-constraint API, the split-out `sable_rapier` native module, and the newly-`@Internal` classes required **no source changes** on our side — the APIs we use (connected-chain walk, `RigidBodyHandle`, sub-level container, mass/bounding-box) are unchanged. Verified: compiles clean and a dedicated server boots in ~1.6s with Rapier scenes initialized.

## 1.1.4 — Self-pull freeze fix + armor-reaction toggle (from player feedback)

### Fixes
- **Build against our blocks by default**: right-clicking an interactible emitter (Electromagnet, Anchor, Repulsor, Tractor Beam, Excavator, Permanent/Temporary Magnet) while holding a block now **places** the block; **shift**-right-click opens the GUI / flips polarity. Previously you had to sneak just to place a block next to a magnet. Empty hand and non-block items (tools) still interact on right-click as before. New `content.blockPlacementFirst` toggle (default on; off restores the legacy click-to-interact / sneak-to-place behavior).
- **Permanent & Temporary Magnets no longer claim they take power**: both are passive (always-on, no redstone/FE), but WTHIT / Jade / goggles were showing a power-source / energy line copied from the powered emitters. They now report no power (`acceptsPower=false`), matching the other passive blocks.
- **Magnetic Elytra stays magnetic when `armorReactsToFields` is off**: the new armor-reaction toggle is meant to stop *incidental* metal armor from yanking a wearer around — but the Magnetic Elytra's whole purpose is magnetic reaction (rail-riding between emitters), so turning the toggle off was wrongly killing it. The Magnetic Elytra is now an explicit exception: it reacts to fields regardless of the toggle, while plain armor still goes inert. Other worn armor on the same wearer stays gated by the toggle.
- **Aircraft no longer pulls on itself / freeze fix**: an emitter mounted on a Create: Aeronautics craft previously excluded only the single Sable sub-level it physically sat on. But a craft built with bearings, springs or hinges is several sub-levels joined by physics constraints, so a magnet on the body would yank its own bearing/spring-mounted subgroups around. Beyond looking wrong (the craft pulling on itself), this set up a feedback loop — the magnetic pull fighting the constraint that holds the parts together — which could spike or hard-freeze the physics solver, matching the "tick rate suddenly freezes, have to force quit" reports when moving a craft with active magnets. Emitters now exclude their **entire connected assembly** (walked via Sable's `getConnectedChain`), not just the host sub-level. Toggle via `physics.excludeConnectedSubLevels` (default true; off restores 1.1.x behaviour).

### Config accessibility (main-menu editable)
- **Player-facing settings can now be configured from the title screen, before a world exists.** Previously the entire config was a single SERVER-type spec — and NeoForge only lets you edit SERVER configs while *in* a world, so a single player couldn't set worldgen options (biome rarity/enable) before generating the world they applied to. The config is now split: player-facing and worldgen-affecting categories (**physics, content, items, tools, worldgen, lightning, compat**) moved to a global **COMMON** spec (`config/magnetization-common.toml`) editable from Mods → Magnetization → Config at the main menu; admin/server-authoritative categories (**guiLimits, debug command-permissions**) stay in the per-world **SERVER** spec. Dedicated servers are unaffected — the owner edits the same common file in `config/`. (MP note: COMMON values aren't synced server→client; all the moved settings drive server-side logic, so the server's file is authoritative — a client's local copy only affects its own display.)

### New config
- **`physics.excludeConnectedSubLevels`** (default true): exclude an emitter's whole assembly from its own force (see above).
- **`content.armorReactsToFields`** (default true): whether worn metal armor makes its wearer react to magnetic fields. Turn off so players (and armored mobs) are no longer dragged toward ores, emitters or any field through their gear — for players who kept getting yanked into ore deposits and hazards. Does not affect intrinsically-magnetizable entities (e.g. iron golems) or ferromagnetic item drops.

### Performance
General performance pass (no behaviour change) targeting the lag/freeze reports on busy worlds:
- **Connected-assembly walk is now cached** per host with a 1 s TTL. The new self-exclusion check above walks the Sable assembly graph; without caching, every emitter on a magnet-heavy craft re-walked the same graph and rebuilt the same set every tick. Now it's one walk per craft per second, shared across all its emitters.
- **Item-vacuum + anomaly-chaos entity scans now filter during chunk traversal.** `MagnetizedArmorVacuum` and `AnomalyMagneticChaos` previously materialised *every* item entity in a large box each pass and filtered afterward; the ferromagnetic/pickup-delay filter is now pushed into `getEntitiesOfClass` so non-matching items are never collected.
- **Surface-repaint scanner no longer spikes on region entry.** The chunk-repaint pass (anomaly/petrified biomes) now (a) skips entirely when both biomes are config-disabled, (b) charges its per-tick budget for every chunk *examined*, not just chunks painted — so dropping into a fresh region examines at most a few chunks per tick instead of walking the whole render-distance neighborhood in one tick — and (c) reuses a `MutableBlockPos` instead of allocating per column.
- **Emitter block-path lookup memoised.** Each emitter reverse-looked-up its block's registry path every tick for the soft-disable check; it's now resolved once per block-entity.
- **Ship-scan TTL fallback corrected** from a stale 100 ticks to the configured 20 when the config spec isn't loaded yet.

### Textures
- **Storage-block variety**: the refined ingot storage blocks (Block of Magnetite / Maghemite / Pyrrhotite / Hematite / Titanomagnetite) all shared one horizontal-band pattern and only differed by hue. Each now has a distinct pattern built from its own palette — magnetite beveled plates with rivets, maghemite keeps the banding, pyrrhotite a studded diamond-plate, hematite diagonal facets, titanomagnetite a cut-crystal facet — so they read as different materials at a glance. (Raw storage blocks already had their own chunky look and are unchanged.)
- **Hematite ore retexture**: the hematite ore (stone + deepslate) read as odd red splatter — its redstone-ore-derived base had big diffuse blobs and an incoherent palette (dark blood-red mixed with washed-out pink). Rebased on the dense, crisp magnetite speck pattern (mirrored so it doesn't look like a recolor of magnetite) with a single coherent metallic-red value ramp, so it now reads as a proper ore alongside the rest of the set.
- **Electromagnet / Kinetic Electromagnet top** (`electromagnet_top`, shared by both blocks): the red "N" was offset down-right and its diagonal was malformed. Redrawn as a clean, centered, rotationally-symmetric glyph on a rebuilt beveled panel (both the idle and active/glowing variants).
- **Lodestone Core block**: replaced the flat 2-colour concentric-square with a dark beveled metal casing, corner bolts and a glowing amber magnetic core that tiles cleanly across faces.
- **Repulsor Gun + Magnetic Grapple items**: both redrawn from the previous unreadable "tube" silhouette into a matched pair — a clean sci-fi launcher body (receiver, sight, angled grip, trigger guard) with an amber repulsor emitter muzzle on the gun and a red/blue bipolar-magnet head on the grapple. Both now also carry custom held-display transforms so the muzzle points **forward** instead of back at the player.
- **Magnet-block panel symmetry pass**: every dark-panel magnet block (Electromagnet, Temporary/Permanent Magnet, Polarity Inverter, Repulsor, Magnetic Anchor, Magnetic Switch, **Tractor Beam, Magnetic Excavator** — idle + active faces) had an asymmetric grey bevel in the black panel (grey on the right/bottom, black on the top/left) that read as a dirty corner. The grey bevel is now blacked out for a clean, symmetric panel, while intentional symmetric detail (the switch's diamond ring, coil/winding stripes, anchor frame) is preserved.
- **Lodestone Core re-symmetrised**: the casing's directional bevel (light top-left, dark bottom-right) looked inconsistent on a cube-all block; rebuilt with a 4-fold-symmetric concentric frame so every face matches and it tiles cleanly.
- **Guns are now distinct, detailed 3D models**: the Repulsor Gun and Magnetic Grapple are voxel models built from a 16-shade palette with baked shading (lighter top decks, darker undersides) and greeble detail. The **Repulsor** reads as an energy push-cannon — gunmetal body, glowing orange power cell on top, copper barrel with a ring, wide copper bell emitter with an amber/white-hot core. The **Grapple** reads as a magnetic harpoon launcher — slimmer body, a cable reel on top, a long thin barrel with a steel ring, and red-top/blue-bottom magnet claw prongs with darker tips. They were scaled down (were oversized) and are the model in every context including the inventory icon.

### Gun effects
- **Repulsor field is now visible**: firing draws the actual conical repulsion volume as expanding particle rings along the look axis, so you can see the area being pushed instead of a thin dotted line.
- **Grapple line reaches the target**: while the grapple pulls, a taut particle line is drawn from the player to whatever's being grappled (emitter, ship or magnetized mob), tracking a moving anchor each tick.
- **Muzzle lights up when fired**: instead of a particle burst, the gun *model itself* glows at the nozzle for a few ticks after a shot — a `fired` item-model property swaps in a variant whose muzzle (Repulsor bell/core, Grapple claw prongs) is white-hot. Driven by a synced `fired_at` data component.

## 1.1.2 — Anomaly biome overhaul, themed stone family, ore texture refresh

### New blocks: Anomaly Stone family
- **`anomaly_stone`** + **`cobbled_anomaly_stone`**: vanilla stone-pattern blocks with iron-oxide green tint, the natural-look "bedrock" of the Anomaly biome. Mining `anomaly_stone` without silk drops cobbled; silk-touch keeps the smooth form (same as vanilla stone/cobblestone).
- **Stairs + slabs + walls**: `anomaly_stone_stairs`/`_slab`, `cobbled_anomaly_stone_stairs`/`_slab`/`_wall`. Full vanilla parity — crafting recipes (4-stair, 6-slab, 6-wall) plus stonecutter shortcuts.
- **`magnetic_gravel`**: dark gravel with magnetite specks. Falls under gravity (`ColoredFallingBlock` parent), lightly magnetic (`#magnetization:ferromagnetic_blocks`), mined with a shovel. Custom loot: 5% chance of raw magnetite, 3% raw maghemite (instead of vanilla's flint roll), otherwise drops itself.
- **Magnetic-gravel worldgen** (`magneticGravelInVanillaBiomes`, default true): a sparse disk of magnetic_gravel attempts placement in ~1-in-24 chunks across overworld biomes, gated via `ConfigGatedAddFeaturesModifier`. Lets survival players who never reach an Anomaly biome still find a small supply through normal exploration.
- **Patchouli "Anomaly Terrain" entry**: walks players through anomaly_stone (silk vs cobbled drop, smelt to revert), the cobbled-stone family (stairs/slabs/walls), and magnetic_gravel (loot table + worldgen footprint).

### Anomaly biome surface workaround
- **Chunk-paint scanner**: TerraBlender's `NoiseGeneratorSettings.surfaceRule()` mixin gets silently overridden by Citadel (Alex's Caves dep) when both target the same dimension's surface rule. Diagnosed the conflict (the static rule map IS populated with our namespace, mixin chain just doesn't fire), and worked around it with a `ServerTickEvent.Post` scanner that paints the visible surface of anomaly/petrified chunks once per chunk, near each player, capped at 8 chunks per tick. The original `MagSurfaceRules` stays registered so users on non-Citadel installs still get the surface for free.
- **Surface palette**: anomaly chunks now read as stone-bulk (80% `anomaly_stone` + `magnetic_gravel`) with 20% ore veining (magnetite, vanilla iron, hematite, maghemite, pyrrhotite, titanomagnetite, vanilla gold, rare `raw_magnetite_block` outcrop).
- **`AnomalyRegion` weight 2→1**: TerraBlender's minimum, so the biome claims a smaller slice of parameter space. Combined with the existing `EXTREMELY_RARE` rarity default, the biome should be properly hard to stumble into on a fresh world.

### Ore texture refresh
- All 10 ferrous ore textures rebased on distinct vanilla bases for visual variance — magnetite from coal_ore (dense specks), maghemite from copper_ore, pyrrhotite from gold_ore, hematite from redstone_ore, titanomagnetite from lapis_ore. Stone/deepslate base pixels preserved byte-for-byte; only speck colors swapped via per-variant 5-color map.

### Player onboarding
- **Field Manual auto-give**: new players receive the Patchouli field manual on first login to a world or server. Per-player flag persists in NBT so reconnecting doesn't re-give. Toggle via `compat.fieldManualAutoGive` (default true).
- **Field Manual multi-recipe**: three cheap recipes (Book + Raw Magnetite, Book + Iron Ingot, Book + Lodestone) so players can craft a replacement from whatever they find first.

### Testing & observability
- **GameTest infrastructure** (`./gradlew runGameTestServer`): initial coverage for emitter `onLoad`/`setRemoved` lifecycle + per-tick energy drain. Sable-dependent multi-ship + excavator tests deferred (need contraption-assembly machinery).
- **Crash report context**: emitter BEs now attach polarity, energy buffer, range/strength overrides, and cached ship state via `fillCrashReportCategory`. Anchor BE adds the bound ship UUID; excavator BE adds in-flight ship count.
- **SableBridge logging**: 8 previously-silent `catch (Throwable)` blocks now emit throttled WARN logs (30s per call-site) with ship UUID context, so Rapier JNI failures stop disappearing into the void.

### Fixes
- **Deepslate maghemite + stone titanomagnetite now actually spawn (rarely)**: previous worldgen only placed these in their natural Y ranges (maghemite Y 40..120 never reached deepslate; titanomagnetite Y -64..-8 never reached stone), so JER charted them as "straight lines" and survival players who only mined one rock layer would never see the off-layer variant. Added `ore_maghemite_deep_rare` (Y -16..-1, rarity 1/4) and `ore_titanomagnetite_shallow_rare` (Y 0..32, rarity 1/6) placed features + matching biome modifiers; JER chart now shows the rare band at ~5% of the main-layer chance.
- **`/magnetization tp` now lands on the actual visible surface**: previously generated the chunk to SURFACE status and read MOTION_BLOCKING heightmap, which could leave the player a block below the painted top. Now forces FULL chunk generation and walks up past any post-heightmap decorations.
- **Dedicated server crash on boot**: the use-curio payload registration was triggering class-load of the client-only `MagKeyBindings` (KeyMapping field initializers), which fatally errored on dedicated servers. Moved the payload + handler to `com.stonytark.magnetization.network.UseCurioPayload` (server-safe package); `MagKeyBindings` stays client-only.
- **Tag references cross-mod**: wrapped 84 bare `#c:` refs in `ferromagnetic` + `ferromagnetic_blocks` with `{"id": "...", "required": false}` so tag load doesn't fail when optional mods (cobalt/tin/steel providers) aren't installed. Added c-tag JSONs for our own magnetite items (`c:ingots/magnetite`, `c:raw_materials/magnetite`, `c:ores/magnetite`, `c:storage_blocks/magnetite`, `c:storage_blocks/raw_magnetite`).
- **`field_manual.json` recipe format**: migrated from the obsolete `"type": "neoforge:conditional"` wrapper to the inline `"neoforge:conditions": [...]` format required by NeoForge 1.21+.
- **`ShipMagneticRegistry` silent scan failure**: scanner exceptions now log WARN with ship UUID instead of silently falling back to DEFAULT.
- **`MagItemTooltips` silent build failure**: tooltip-key map build failures now WARN once and poison the map so we don't retry every tooltip call.
- **Patchouli Magnetic Switch entry**: was missing from the field manual; added with usage explainer.

### Config reorganization
- **`ae2MeteoriteHookEnabled`** moved from `[worldgen]` to `[compat]` — it's a mod-compatibility toggle that only fires when AE2 is installed.
- **`magneticSwitchRange`** moved from `[worldgen]` to `[content]` — it's per-block redstone-sensor tuning, not worldgen.
- **Upgrade note**: existing `magnetization-server.toml` files from 1.0.1/1.1.0 keep these values under `[worldgen]` and will silently revert to the new defaults (`ae2MeteoriteHookEnabled = true`, `magneticSwitchRange = 8`). Behavior is unchanged unless you'd previously customised them — in which case copy the value over to the new section after first load.

### Internal
- New package `com.stonytark.magnetization.network` for server-safe payload definitions.
- New package `com.stonytark.magnetization.gametest` for in-world integration tests.
- `gameTestServer { type = 'gameTestServer' }` run added to `build.gradle`.
- `ChunkSurfaceRepaintHandler` (new) — the player-tick-driven surface scanner described above.
- Cross-mod smoke-test runtime: build.gradle now `localRuntime`s Macaw's 6, Supplementaries (+Moonlight), Sophisticated Storage (+Core), Immersive Aircraft, and Create: New Age alongside existing Create / IE / Alex's Caves / AE2 / Curios / Patchouli, so the ferromagnetic tag refs to each are exercised every runClient launch. The Aether is tag-supported but kept out of the dev runtime — its transitive `accessories` mixin currently breaks against NeoForge 21.1.230's renderer dispatcher.

## 1.1.0 — Whole-ship polarity + physics overhaul

### New: ship polarity & susceptibility
- Ships now have a magnetic polarity (NORTH by default; flipped by parity of Polarity Inverters on board — 1 = SOUTH, 2 = NORTH, etc.) and a susceptibility multiplier derived from ferromagnetic blocks and magnet emitters on board.
- Magnets on a ship contribute to susceptibility but **never** to polarity — toggling a mounted electromagnet north↔south will not invert and tear apart the contraption.
- Surfaced on Create goggles, Jade, WTHIT, and TOP overlays: `On ship: NORTH ×1.4 (12 ferrous, 3 magnets, 1 inverter)`.
- Five config knobs: `shipBaselineSusceptibility`, `shipPerFerrousSusceptibility`, `shipPerMagnetSusceptibility`, `shipMaxSusceptibility`, `shipScanIntervalTicks`.

### Physics overhaul
- **Torque from off-center impulses**: extended ships now rotate naturally when forces are applied off the center of mass (`τ = r × F`, ∆ω via the sub-level's inverse inertia tensor).
- **Multi-sample field integration**: each emitter samples a 3×3×3 grid across the target ship's AABB by default (configurable 1–7), so non-uniform fields produce realistic torque on large ships. The per-tick acceleration cap is applied once across the summed force and distributed proportionally — preserving the relative magnitude of each sample.
- **Linear drag**: ships under sustained magnetic pull settle to a terminal velocity instead of accelerating without bound (`shipLinearDrag`, 2 %/tick default).
- **Angular drag**: counterpart to linear drag — without it the torque from off-center sample forces could keep a ship spinning indefinitely (`shipAngularDrag`, 5 %/tick default).
- **Cumulative per-ship-per-tick budget**: multiple emitters touching the same ship now share the acceleration cap.

### Quality-of-life
- **Repulsor Coil defaults** to 8-block range (was a config-derived 128 default which was nonsensical for a short-range pusher); its GUI range buttons now step by 1 instead of 8 for finer tuning.
- **Emitters now react to existing redstone signals at placement time** — placing an electromagnet next to an active lever no longer leaves it inert until the lever is toggled.
- **Angular drag** companion to linear drag (`shipAngularDrag`, 5%/tick default) so ships don't spin indefinitely under sustained off-center pull.

### Fixed
- **Rotation bug**: magnets on simulated contraptions no longer stick to the cardinal direction they were placed in — both the field origin and axis now follow the ship's pose. (Root cause: `SableBridge.promoteToWorldSpace` was re-querying the host via a sub-level-local blockpos against the outer world level, silently failing the lookup and leaving the field in local space. Fix: takes `Pose3dc` directly from `host.logicalPose()`.)
- **Wrong-frame impulses**: `addLinearAndAngularVelocity` operates in world frame, but our `dv`/`dω` were being computed in the ship's local frame. For un-rotated ships this looked fine; for rotated ships the impulse direction got rotated by the ship's own orientation, presenting as "spins near a magnet" and "repelled despite opposite poles". Now correctly transformed to world before applying.
- **Magnetizable entity tag too broad**: vanilla zombies, husks, drowned, skeletons, strays, wither_skeletons, vindicators, pillagers, ravager, and falling_block were all pulled by magnets regardless of whether they wore metal. Trimmed to `iron_golem`, `arrow`, `trident` — mobs in metal armor still get pulled via the armor predicate.
- **Slow polarity updates** when an inverter was broken: `shipScanIntervalTicks` default lowered 100 → 20 (5 s → 1 s), AND the inverter block now invalidates the ship state cache on place/break for instant polarity flips (~50 ms).
- **Advancement false-triggering**:
  - `first_emitter` was triggering on any `#magnetization:ferromagnetic` item (picking up an iron ingot completed the chain). Now requires one of the seven actual emitter items.
  - `inverted_quarry` description mentioned wrenching but the trigger was plain block placement. Now uses `item_used_on_block` with `#c:tools/wrench` on `magnetic_excavator`.
  - `dual_magnetized` triggered on any `#magnetization:metal_armor` (including plain vanilla iron). Now requires both a NORTH-stamped and a SOUTH-stamped magnetized armor piece, matching the name.
  - `full_kit` description says "every emitter in the addon" but `permanent_magnet` was missing from the criteria; added.

### Tooling
- `/magnetization debug rotate <deg> [yaw|pitch|roll]` — teleport-rotates the nearest sub-level by absolute world-frame angle, for testing magnet behavior on arbitrarily oriented ships.
- Test count 13 → 50: new coverage on pose-transform math and the ship-state scanner.

## 1.1.1 — Cross-mod sweep, Repulsor Gun, FE/RF, Curios keybinds

### New: admin & test commands
- `/magnetization help` — permission-aware list of every subcommand with a one-line description. No-perm, available to any player.
- `/magnetization version` — prints mod + Create + Sable + Aeronautics + NeoForge versions.
- `/magnetization stats` — counts loaded emitters / live Sable ships / magnetized entities in the player's level.
- `/magnetization config show [filter]` — reflection-driven dump of live `MagConfig` values, optional substring filter.
- `/magnetization debug energy <pos> [fill|drain|set <n>]` — read or write an emitter's FE buffer directly. Replaces having to set up a real FE source to validate the energy path.
- `/magnetization debug curios` — list contents of the running player's Curios slots; confirms slot membership when validating HUD/keybind behaviour.
- `/magnetization tp <biome> [player]` — extended with optional player arg (op-gated) so admins can yank another player into the anomaly / petrified forest.
- `/magnetization spawn_test_ship [size] [material]` — extended with optional size (1–5) and material (magnetite|iron|raw_iron|gold|copper) args so the same command produces tiny test cubes or hefty contraptions on demand. Defaults stay 1×1×1 magnetite.
- `MagCommands.onRegister` refactored to delegate to subtree-builder helpers (`buildDebugSubtree`, `buildLirmSubtree`, `buildTpSubtree`, `buildSpawnTestShipSubtree`) — future additions don't require editing a 100-line fluent-chain.

### New: advancements
- New `MagSimpleTrigger` infrastructure under `com.stonytark.magnetization.advancements` + `MagTriggers` DeferredRegister. Generic empty-condition `SimpleCriterionTrigger` reused under two registry IDs (`energy_activated`, `recoil_launch`).
- **Recoil Engineer** (`crafted_repulsor_gun`) — fires when a Repulsor Gun enters the player's inventory.
- **Newton's Third** (`recoil_launch`, hidden challenge) — fires when the gun's self-recoil ray-trace actually lands on a magnetic emitter block (i.e. the player gets the kickback). Triggered from `RepulsorGunItem.applySelfRecoil`.
- **Wired In** (`powered_by_energy`, goal) — fires when an emitter's `energyActiveThisTick` transitions false→true with a `ServerPlayer` within 16 blocks. Triggered from `AbstractEmitterBlockEntity.tickEmitter`.

### Repulsor Gun: mass-aware ship push
- Ship pushes now go through `RigidBodyHandle.applyLinearImpulse` directly instead of the sustained `FieldApplicator.apply` path (which caps per-tick acceleration — neutered the gun's one-tick burst on medium-mass ships). Sable computes `velocity_delta = impulse / mass`, so small test cubes fly while heavy contraptions budge proportionally.
- Two new config knobs: `items.repulsorGunShipImpulse` (default 40, Sable kg·m/s) and `items.repulsorGunShipMaxVelocityDelta` (default 8 m/s, per-shot velocity cap so a 1-block magnetite cube doesn't launch across the world).
- Entity / dropped-item / self-recoil paths unchanged; ship path is the only behavioural diff.
- New public `FieldApplicator.applyEntitiesOnly(level, field)` entry so callers can use the entity pipeline without the ship side.

### Curios slot UI
- Curios capability was registered for Field Compass / Magnetic Grapple / Repulsor Gun in the 1.1.0 cycle, but no slots existed for them to live in (Curios 1.21.1 ships no default entity assignments). Added `data/magnetization/curios/entities/player.json` giving the player one **charm**, one **back**, and one **hands** slot. Added matching `data/curios/tags/item/slot/{charm,back,hands}.json` tags so the slot validators accept our items.
- Slot mapping: Field Compass → charm (passive — needle + HUD read the slot directly), Magnetic Grapple → back (active via `USE_GRAPPLE` keybind), Repulsor Gun → hands (active via `USE_REPULSOR_GUN` keybind).

### GUI fixes
- Emitter energy bar moved up out of the range `+` button row (was overlapping at y=50). Now 8×40 px at y=5–45, clears the button row.
- Energy bar colour simplified to a red fill on a black border (was source-coloured red/orange/grey/blue). Source info is still in the hover tooltip and the tooltip line on the emitter itself.

### Dependency bumps
- NeoForge `21.1.219` → `21.1.230`
- Sable `1.1.3` → `1.2.2` (no API surface changes affecting our code — audited)
- Create: Aeronautics `1.1.0` → `1.2.1`
- Simulated `1.1.0` → `1.2.1` (`1.1.3` proposed but never published to any configured maven)
- Jade `15.10.0+neoforge` → `15.10.5+neoforge`
- All other soft-dep versions verified current as of the release date.

### Fixes
- **`en_us.json` was missing its closing `}`** — JSON parser failed at the last line, so every translation key in the mod fell back to its raw form (config menu, blocks, items, HUD, Patchouli). The missing closing brace shipped silently from the Phase 19.1 batch.
- **Repulsor Gun missing from creative tab + JEI** — `MagCreativeTab` listed `MAGNETIC_GRAPPLE` and `LODESTONE_CORE` consecutively but skipped the gun.
- **`/magnetization version` showed `Aeronautics absent`** — used `create_aeronautics` as the mod ID; actual is `aeronautics`.
- **`temporary_magnet` missing from `#minecraft:mineable/pickaxe`** — block existed but couldn't be efficiently mined with a pickaxe.
- **Eight missing translation keys** filled in: `item.minecraft.{potion,splash_potion,lingering_potion,tipped_arrow}.effect.magnetized` (Potion of Magnetization + variants), `book.magnetization.entry.repulsor_gun.recipe` (Patchouli crafting page caption), `magnetization.configuration.items.repulsorGunShipImpulse` + `repulsorGunShipMaxVelocityDelta` (the new ship-impulse config knobs).
- **Four orphaned textures removed** — `block/magnetic_excavator_back.png`, `block/tractor_beam_back.png`, `item/petrified_wood.png`, `particle/mag_field.png` (no model references).
- **README drift** — Sable / NeoForge / Aeronautics / Simulated version requirements caught up to the bumps; Curios description updated to mention the three actual slot types; Repulsor Gun added to the items list; new commands added to the player-interactions list.

### Hardening
- Concurrency: `TemporaryLirmFields.ENTRIES_BY_LEVEL` switched from `HashMap` to `ConcurrentHashMap` with `Collections.synchronizedList`-wrapped inner lists; the tick-handler's iterate-with-remove holds the list lock for the whole pass. Removes a theoretical CME if a future lightning event handler runs off the server-main thread.
- Concurrency: `ActiveEmitterScanner.listeners` + `postScanHooks` switched from `ArrayList` to `CopyOnWriteArrayList`. Zero perf cost (writes only at client-setup) but bulletproof against any future mid-tick registration.

### Compatibility & tagging
- **Magnetizing** (Command17) — ingot fungible via `c:ingots/magnetite`; block/item magnets recognized as ferromagnetic; honour `magnetizing:unmoveable_by_magnets` entity tag so admins curate one list.
- **Create: Magnetics** (Koudesuk) — ingot/sheet/block fungible via `c:` tags; Kinetic Magnet counts as a magnet for ship susceptibility; magnetized crystals are ferromagnetic items.
- **Simulated** — Redstone Magnet recognized as a magnet emitter, so contraptions carrying one feel our fields naturally.
- Recipe ingredients (excavator, magnetite block) switched to `c:ingots/magnetite` tag so any of the three mods' ingots feed our recipes.
- Broader `c:` metal tags added to `ferromagnetic_blocks` and `ferromagnetic` (items) — steel, nickel, cobalt, zinc, brass, tin, lead, silver, osmium, uranium, aluminum, neodymium, electrum, invar, constantan, bronze. Any tech/Create addon populating these gets free integration.
- **Alex's Caves** — Magnetron + Ferrouslime in `magnetizable` entity tag; Azure/Scarlet magnet blocks and their neodymium ores/ingots/blocks in `ferromagnetic_blocks`/`ferromagnetic`; Heart of Iron, Galena, Ferrouslimeball as ferromagnetic items.
- **Twilight Forest** — knightmetal/steeleaf/ironwood/fiery ingots + blocks tagged as ferromagnetic; their full armor sets in `metal_armor` so they accept polarity stamps from the Electromagnet GUI.
- **Create: New Age** — its `electromagnet` coil + `tesla_coil` recognized as ferromagnetic blocks (the names overlap with ours but the mechanics don't conflict — theirs is a power-generation coil, ours is a field emitter).
- **Create: Crafts & Additions** — copper wires, iron rods, capacitor, electric motor tagged ferromagnetic.
- **Mekanism** — osmium/lead/tin/uranium/refined alloys are auto-covered by the `c:` tag expansion above.
- **EMI** plugin — recipe-viewer info pages for ferromagnetic items and excavator targets, mirroring the existing JEI and REI plugins.
- **Curios** plugin — Field Compass and Magnetic Grapple registered as curios so they work from a charm slot.
- **Patchouli** book — craftable in-game guide (Book + Raw Magnetite) with four chapters covering basics, emitters, ship polarity, and advanced topics. Auto-registers when Patchouli is installed.
- **Alex's Caves** — Magnetron + Ferrouslime in magnetizable entity tag; their magnets and neodymium content in ferromagnetic tags. New config `compat.alexsCavesPotionMode` controls whether AC's Magnetizing potion and our Magnetized effect coexist (`BOTH`, default), or one mod's effect supersedes the other (`OURS_ONLY` / `THEIRS_ONLY`).
- **The Aether** — gravitite tagged ferromagnetic (item + block).
- **Iron Chests / Sophisticated Storage** — metal chest/barrel variants in ferromagnetic_blocks.
- **Immersive Engineering** — steel/aluminum/electrum/constantan ingots, plates, wirecoils, railgun in ferromagnetic; their ore variants in ferromagnetic_blocks.
- **Modular Golems / Extra Golems / Cataclysm / Bosses of Mass Destruction** — metallic golem entities and metal bosses (Ignis, Netherite Monstrosity, Gauntlet) in magnetizable entity tag.
- **Quark** — Iron and Copper Oretoises in magnetizable entity tag.
- **Cross-mod lightning sources** — new `#magnetization:lightning_sources` damage-type tag lets non-vanilla lightning attacks trigger LIRM stamping + log petrification. Curated entries for Iron's Spells (Chain Lightning, Lightning Lance, Thunderstorm, Ascension), Cataclysm Scylla (Lightning Spear, Electric Shock), Alex's Caves (Tesla, Magnetron Shock), IE Tesla Coil + razor shock + razor wire, Twilight Forest lightning. Datapacks can extend the list without touching code. Refactored `LightningRemnantMagnetism.applyLirmStamp(target, sourceLabel)` to be public so the new `LivingIncomingDamageEvent` listener can share the vanilla-bolt code path.
- **Supplementaries** — iron_gate, gold/netherite doors+trapdoors, pulley_block, faucet, bellows, cog_block, spring_launcher, globe, wind_vane, hourglass, cannon, cannonball, rocket all ferromagnetic.
- **Immersive Engineering deeper pass** — Tesla Coil, Floodlight, Transformers, Capacitors (LV/MV/HV), Generators, Coil blocks, full steel/aluminum architecture line (fences, gates, posts, doors, trapdoors, catwalks, scaffolding, ladders, sheetmetal), feedthrough — plus `#c:sheetmetals` for any-mod metal sheet coverage.
- **Macaw's mods** — Doors (7 metal variants), Fences (17 metal variants), Lights (15 iron/gold/copper candle holders + chandeliers), Bridges (iron variants), Trapdoors (3 metal variants), Windows (6 metal variants).
- **Immersive Aircraft + Aviator Dreams** — all 7 IA aircraft (Gyrodyne, Biplane, Airship, Cargo Airship, Warship, Quadrocopter, Bamboo Hopper) and all 8 Aviator Dreams aircraft entities tagged magnetizable; metal crafting components (engines, propellers, boilers, gyroscope, gears, pipes, hull reinforcement, landing gear, rotary cannon, bomb bay, etc.) tagged ferromagnetic. Aircraft are plain entities (not Sable sub-levels), so velocity injection from our fields works directly.

### Grapple targeting expanded
- The Magnetic Grapple now hooks attractive emitters (unchanged), Sable sub-levels with non-zero susceptibility, and magnetized living entities (any armor with the polarity stamp, or carrying the Magnetized effect). Mobile targets are tracked via a position-supplier each tick, so the grapple pulls toward a moving ship's current pose-center or a fleeing entity's current position rather than where they were at click time.

### FE/RF as alternative power source
- The 5 redstone-powered emitters (Electromagnet, Magnetic Anchor, Repulsor Coil, Tractor Beam, Magnetic Excavator) now expose an `IEnergyStorage` capability and accept FE/RF from any mod that provides it (Create: Crafts & Additions, Mekanism, Thermal, IE generators, AE2, etc.) — no hard dep on any specific energy mod required, FE is a NeoForge-native API.
- Internal one-way buffer (50 000 FE default, 200 FE/tick transfer rate, 10 FE/tick drain while emitting). External `extractEnergy` returns 0 by design — the emitter is the only thing that drains the buffer.
- Power resolution: redstone takes priority (free); if redstone is off and energy is allowed + sufficient, one tick's drain consumes from the buffer. Energy stays buffered when idle.
- New admin config: `compat.allowRedstonePower` (default true), `compat.allowEnergyPower` (default true), `compat.emitterEnergyCapacity` (50 000), `compat.emitterEnergyDrainPerTick` (10), `compat.emitterEnergyTransferRate` (200). Setting `allowRedstonePower=false` forces players to use FE/RF, useful on hardcore-leaning servers where infinite redstone trivialises the loop.
- The KineticElectromagnet is unchanged — it uses Create kinetic stress; adding FE on top would conflict with the kinetic balance and isn't what was asked.

### New items
- **Repulsor Gun** — hand-held conical repulsor, opposite of the Magnetic Grapple. Right-click fires a one-tick pulse along the look direction: ships in the cone get pushed away, magnetized entities get knockback, dropped items + falling blocks get nudged out. Self-recoil triggers when the shot lands on a magnetic emitter block — closer = harder kickback, giving traversal options ("aim down at a lodestone to launch backward"). Cooldown-gated (no FE/RF). Texture is a hue-shifted recolour of the Magnetic Grapple to read as a paired item.
- **Field Compass overhaul** — replaces the right-click scan with a vanilla-compass-style passive needle. 32 hue-shifted compass frames pick the needle direction toward the nearest active emitter; HUD overlay above the hotbar shows bearing (cardinal + degrees), distance, polarity, and strength when the compass is in hand OR any Curios charm slot. Inside the Magnetic Anomaly biome the needle spins erratically and the overlay flips to a flavour line.
- **Vanilla compass anomaly behaviour** — vanilla `minecraft:compass` needle also scrambles inside the Magnetic Anomaly biome (theming match with our Field Compass). Config-gated `compat.anomalyAffectsVanillaCompass` (default true) for admins who want vanilla untouched.
- **Curios keybinds** — Magnetic Grapple and Repulsor Gun are activatable from Curios charm slots via configurable keybinds under *Options → Controls → Key Binds → Magnetization*. Default unbound so players pick a key with no vanilla conflicts. Field Compass stays passive in curio slots (no keybind needed — the needle and HUD both read the slot directly).

### Modded armor + tools recognized
- Immersive Engineering (Steel + Faraday armor sets, Hammer, Wirecutter, Revolver, Railgun, Drillheads), Iron's Spells (iron-plated and netherite-plated armor sets, Cultist/Executioner/Knight swords), Cataclysm (Ignitium armor + elytra chestplate + tool set), Alex's Caves (Hazmat suit), Mekanism (Steel armor + tools), TF tools (knightmetal/steeleaf/ironwood/fiery sword/pickaxe/axe/etc.) all in `metal_armor` and `metal_tools` — so they accept polarity stamps from the Electromagnet GUI and feed our magnetized-armor pull system.

## 1.0.1 — Worldgen hotfix
- Fixed silent worldgen breakage (custom biome modifier codec, `minecraft:ore_copper` was the wrong target).

## 1.0.0 — Initial release
- Magnets, anchors, electromagnets, tractor beams, repulsor coils, excavators, magnetic switches, polarity inverters.
- Ferromagnetic and magnetite ore/ingot/blocks.
- Magnetized armor and tools (per-tool signature abilities; lightning induces remnant magnetism).
- Magnetic Anomaly and Petrified Forest biomes (via TerraBlender).
- Field Compass, Magnetic Grapple, mob effect, particles.
- Create goggle, Jade, WTHIT, TOP, JEI, REI, and Ponder integrations.
