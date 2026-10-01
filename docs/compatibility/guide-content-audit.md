# Patchouli and Ponder content accuracy audit

Completed 2026-10-01 against base commit `3d1c78b3ff1c6492df5072024d399d5e5cb654e5` and the source hashes in [checks.json](evidence/guide-content/checks.json). This is an identification audit: the content defects below remain open; no gameplay or guide fixes are included.

The review found **28 confirmed content defects**: five P1 progression/build errors, 22 P2 incorrect or misleading instructions, and one P3 formatting error. The most urgent fixes are the alloy, plate, Lodestone Core and sapling recipes, plus the Fusion Thruster Ponder geometry. Prior successful scene playback establishes that scenes run, not that their depicted builds work.

The findings below describe the audited baseline. See [corrections and acceptance](guide-content-corrections.md) for the resulting changes and fresh evidence.

## Scope and evidence

- Reviewed all 88 English entries and 237 pages across eight categories: 109 text, 93 spotlight, 29 crafting and six smelting pages. The [inventory](evidence/guide-content/inventory.json) retains every page definition, resolved text, referenced local recipe, source review target and finding ID.
- Reviewed all 18 advertised Ponder scenes (16 core and two optional), their 33 instructions, all 51 English localization keys, and scene construction. The per-scene findings appear below.
- Structural checks found no duplicate book-definition JSON keys, missing referenced translations/categories, missing referenced local recipe files or local item/block assets. There are 33 distinct recipe references and 108 distinct item/icon references. Asset existence does not establish runtime registry or recipe availability under every configuration.
- Fresh targeted tests passed: `PatchouliFieldManualTest` (10) and `PonderSceneCatalogTest` (4), zero failures/errors/skips. These ran in the existing isolated snapshot after all 101 relevant content/plugin/test files matched the checkout byte-for-byte. See [unit log](evidence/guide-content/unit-tests.log) and [JUnit results](evidence/guide-content/junit).
- Existing [native lifecycle/presentation evidence](lifecycle-presentation-validation.md) covers book opening and selected pages, recovery crafting, gifts, reload/restart, and full scene playback. All seven recorded Java/resource source hashes still match this audit. The earlier release suite was not rerun for this documentation-only audit.
- No new full-book native rendering pass or every-mod integration pass was performed. “No confirmed finding recorded” in the entry inventory means source review found no recorded defect; it is not a guarantee of every interaction, configuration or GUI layout.

Reproduction of the targeted checks (Java 21, isolated snapshot):

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test --tests '*PatchouliFieldManualTest' --tests '*PonderSceneCatalogTest' --console=plain
```

## Confirmed content defects

P1 means the instructions prevent core progression or teach an invalid build; P2 means a materially incorrect gameplay instruction or explanation; P3 means a cosmetic defect. These are audit priorities, not claims of crashes or data loss.

### F01 · P1 · Starter alloy recipe

Entries: `basics/ferromagnetic_ingot`, `basics/magnetite`.

Reader claim: A center Lodestone Core, four magnetite ingots, and four iron ingots produce three Ferromagnetic Ingots.

Correction: The shipping recipe is eight Iron Ingots around a vanilla Lodestone, yielding eight Ferromagnetic Ingots. The spotlight also incorrectly describes magnetite as an ingredient. Keep the separate meteorite shortcut, which is correct.

Evidence: [en_us.json:1636](../../src/main/resources/assets/magnetization/lang/en_us.json#L1636), [ferromagnetic_ingot.json:13](../../src/generated/resources/data/magnetization/recipe/ferromagnetic_ingot.json#L13), [ferromagnetic_ingot.json:18](../../src/generated/resources/data/magnetization/recipe/ferromagnetic_ingot.json#L18), [MagRecipeProvider.java:92](../../src/main/java/com/stonytark/magnetization/data/MagRecipeProvider.java#L92).

### F02 · P1 · Plate ingredient and yield

Entries: `basics/magnetic_plate`, `basics/magnetite`.

Reader claim: Three magnetite ingots in a row produce one Magnetic Plate.

Correction: Three c:ingots/magnetic_alloy in a row produce three plates. The default local ingredient is Ferromagnetic Ingot; Magnetite Ingot is not in that tag. This disagrees with the actual recipe page in the same manual.

Evidence: [en_us.json:1632](../../src/main/resources/assets/magnetization/lang/en_us.json#L1632), [magnetic_plate.json:13](../../src/generated/resources/data/magnetization/recipe/magnetic_plate.json#L13), [magnetic_alloy.json:4](../../src/main/resources/data/c/tags/item/ingots/magnetic_alloy.json#L4).

### F03 · P1 · Lodestone Core recipe

Entries: `basics/lodestone_core`, `advanced/meteorite_fragment`, `basics/magnetite`.

Reader claim: Eight Magnetic Plates surround one Lodestone.

Correction: Use PLP / LFL / PLP: four magnetic-alloy plates at the corners, four vanilla Lodestones on the edges, and one magnetic-alloy ingot in the center. The meteorite-fragment entry repeats the obsolete eight-plate comparison.

Evidence: [en_us.json:1640](../../src/main/resources/assets/magnetization/lang/en_us.json#L1640), [lodestone_core.json:16](../../src/generated/resources/data/magnetization/recipe/lodestone_core.json#L16), [lodestone_core.json:6](../../src/generated/resources/data/magnetization/recipe/lodestone_core.json#L6).

### F04 · P1 · Meteorite Sapling ingredient counts

Entries: `advanced/meteorite_sapling`.

Reader claim: One Meteorite Fragment is cradled in four Raw Magnetite.

Correction: The shipping pattern is " F " / "RFR" / " R ": two fragments and three Raw Magnetite. Confirm which recipe is intended before changing the implementation; the page should match the shipping recipe in the meantime.

Evidence: [en_us.json:1136](../../src/main/resources/assets/magnetization/lang/en_us.json#L1136), [meteorite_sapling.json:14](../../src/generated/resources/data/magnetization/recipe/meteorite_sapling.json#L14), [MagRecipeProvider.java:390](../../src/main/java/com/stonytark/magnetization/data/MagRecipeProvider.java#L390).

### F05 · P1 · Fusion panel scene has the wrong plane

Reader claim: The depicted NORTH-facing Fusion Thruster is a valid framed panel.

Correction: The scene places the frame in the X/Z plane at y=1, but NORTH requires an X/Y frame at constant Z. It therefore lacks required above/below coils and cannot form. Build the scene perpendicular to FACING and validate its world with FusionThrusterPanel.validate. The scene also tells readers to expand the panel without actually expanding it.

Evidence: [en_us.json:2288](../../src/main/resources/assets/magnetization/lang/en_us.json#L2288), [MagPonderPlugin.java:179](../../src/main/java/com/stonytark/magnetization/client/MagPonderPlugin.java#L179), [MagPonderPlugin.java:183](../../src/main/java/com/stonytark/magnetization/client/MagPonderPlugin.java#L183), [FusionThrusterPanel.java:297](../../src/main/java/com/stonytark/magnetization/content/jet/FusionThrusterPanel.java#L297), [FusionThrusterPanel.java:119](../../src/main/java/com/stonytark/magnetization/content/jet/FusionThrusterPanel.java#L119), [native final-frame capture](evidence/lifecycle-presentation/screenshots/validation-ponder-fusion_panel-last.png).

### F06 · P2 · Anchor binding gesture

Entries: `emitters/anchor`.

Reader claim: Sneak-right-click a ship while holding an anchor to bind it.

Correction: A powered placed anchor automatically binds the closest eligible ship when unbound. Sneak-wrench the placed anchor to release its binding. There is no described item-to-ship binding gesture in the anchor item/block path.

Evidence: [en_us.json:1436](../../src/main/resources/assets/magnetization/lang/en_us.json#L1436), [MagneticAnchorBlockEntity.java:97](../../src/main/java/com/stonytark/magnetization/content/anchor/MagneticAnchorBlockEntity.java#L97), [MagneticAnchorBlockEntity.java:98](../../src/main/java/com/stonytark/magnetization/content/anchor/MagneticAnchorBlockEntity.java#L98), [MagneticAnchorBlockEntity.java:72](../../src/main/java/com/stonytark/magnetization/content/anchor/MagneticAnchorBlockEntity.java#L72), [EmitterPlacementHandler.java:66](../../src/main/java/com/stonytark/magnetization/content/EmitterPlacementHandler.java#L66).

### F07 · P2 · Nonexistent entity-locked compass

Entries: `advanced/compass_grapple`.

Reader claim: Shift-right-click creates an entity-locked Field Compass variant.

Correction: FieldCompassItem has no use override or entity-lock component. It is a passive active-field indicator. Remove the nonexistent mode; describe the current HUD and Curios behavior instead.

Evidence: [en_us.json:1493](../../src/main/resources/assets/magnetization/lang/en_us.json#L1493), [FieldCompassItem.java:20](../../src/main/java/com/stonytark/magnetization/content/item/FieldCompassItem.java#L20).

### F08 · P2 · Vector Core thrust direction

Entries: `ships/vector_core`.

Reader claim: Installing a Vector Core thrusts ships along the Repulsor Coil facing.

Correction: The cone follows FACING, but the added conveyor thrust follows one of four perpendicular directions selected in the GUI. Explain both directions and the GUI cycling control.

Evidence: [en_us.json:1724](../../src/main/resources/assets/magnetization/lang/en_us.json#L1724), [RepulsorCoilBlockEntity.java:90](../../src/main/java/com/stonytark/magnetization/content/repulsor/RepulsorCoilBlockEntity.java#L90), [RepulsorCoilBlockEntity.java:93](../../src/main/java/com/stonytark/magnetization/content/repulsor/RepulsorCoilBlockEntity.java#L93), [RepulsorCoilBlockEntity.java:99](../../src/main/java/com/stonytark/magnetization/content/repulsor/RepulsorCoilBlockEntity.java#L99).

### F09 · P2 · Hematite strength ladder and floor

Entries: `advanced/hematite_block`.

Reader claim: EXTREME → STRONG → WEAK; WEAK is the floor.

Correction: The enum has MEDIUM between STRONG and WEAK, and stepDown clamps at NONE. Correct the ladder to EXTREME → STRONG → MEDIUM → WEAK → NONE and explain complete suppression.

Evidence: [en_us.json:1623](../../src/main/resources/assets/magnetization/lang/en_us.json#L1623), [HematiteBlock.java:53](../../src/main/java/com/stonytark/magnetization/content/hematite/HematiteBlock.java#L53), [MagneticStrength.java:16](../../src/main/java/com/stonytark/magnetization/api/MagneticStrength.java#L16).

### F10 · P2 · Halbach neighbor bonuses

Entries: `emitters/halbach`.

Reader claim: Each adjacent same-pole magnet raises the tier by one.

Correction: The implementation groups neighbors: one or two aligned neighbors give +1 tier, three through six give +2; the maximum bonus is two tiers, clamped at EXTREME.

Evidence: [en_us.json:1706](../../src/main/resources/assets/magnetization/lang/en_us.json#L1706), [HalbachArray.java:25](../../src/main/java/com/stonytark/magnetization/content/HalbachArray.java#L25), [HalbachArray.java:39](../../src/main/java/com/stonytark/magnetization/content/HalbachArray.java#L39).

### F11 · P2 · Plain Ferrofluid behavior

Entries: `advanced/ferrofluid`.

Reader claim: Plain Ferrofluid is field-immune and does not move.

Correction: Plain Ferrofluid grows transient tendrils toward either magnetic pole, at the configured plain-fluid interval. It emits no field itself, which is different from being immune to fields. Explain recession and the original-source restriction on bucket pickup.

Evidence: [en_us.json:1738](../../src/main/resources/assets/magnetization/lang/en_us.json#L1738), [FerrofluidCreepHandler.java:200](../../src/main/java/com/stonytark/magnetization/content/fluid/FerrofluidCreepHandler.java#L200), [FerrofluidCreepHandler.java:302](../../src/main/java/com/stonytark/magnetization/content/fluid/FerrofluidCreepHandler.java#L302), [FerrofluidCreepHandler.java:226](../../src/main/java/com/stonytark/magnetization/content/fluid/FerrofluidCreepHandler.java#L226).

### F12 · P2 · Titanomagnetite overwrite rule

Entries: `advanced/titanomagnetite_block`.

Reader claim: Only a stronger later field overwrites the recorded field.

Correction: The block picks the strongest currently eligible nearby source and overwrites its saved record whenever one exists, even if weaker than its previous record. Explain current-source replacement, not stronger-only retention.

Evidence: [en_us.json:1626](../../src/main/resources/assets/magnetization/lang/en_us.json#L1626), [TitanomagnetiteBlockEntity.java:156](../../src/main/java/com/stonytark/magnetization/content/titanomagnetite/TitanomagnetiteBlockEntity.java#L156), [TitanomagnetiteBlockEntity.java:160](../../src/main/java/com/stonytark/magnetization/content/titanomagnetite/TitanomagnetiteBlockEntity.java#L160).

### F13 · P2 · Titanomagnetite reset instructions

Entries: `advanced/titanomagnetite_block`.

Reader claim: Break and replace the block to wipe its imprint.

Correction: Loot copies magnetization:recorded_field onto the dropped item, and placement restores it. Normal break-and-replace preserves the record. Do not promise a reset gesture that the implementation does not provide.

Evidence: [en_us.json:1628](../../src/main/resources/assets/magnetization/lang/en_us.json#L1628), [titanomagnetite_block.json:18](../../src/generated/resources/data/magnetization/loot_table/blocks/titanomagnetite_block.json#L18), [TitanomagnetiteBlockEntity.java:63](../../src/main/java/com/stonytark/magnetization/content/titanomagnetite/TitanomagnetiteBlockEntity.java#L63).

### F14 · P2 · Elytra pole directions

Entries: `advanced/magnetic_elytra`.

Reader claim: NORTH emitters pull and SOUTH emitters push.

Correction: For an unstamped/default NORTH wearer, NORTH repels and SOUTH attracts. Stamped armor can invert the result. The nearby Polarity chapter already states the correct rule.

Evidence: [en_us.json:1128](../../src/main/resources/assets/magnetization/lang/en_us.json#L1128), [FieldApplicator.java:830](../../src/main/java/com/stonytark/magnetization/physics/FieldApplicator.java#L830), [FieldApplicator.java:936](../../src/main/java/com/stonytark/magnetization/physics/FieldApplicator.java#L936).

### F15 · P2 · Fusion fuel processing routes

Entries: `fluids/fusion_fuels`.

Reader claim: Every fusion-fuel tier is a fluid first, converted to a cell with a frame.

Correction: The default ladder crosses item/fluid stages: Hydrogen buckets can craft a Deuterium Cell directly; a Deuterium Cell plus Lithium makes a Tritium Cell; a Tritium Cell plus two Glowstone blocks makes a Helium-3 Cell. Tritium/Helium-3 buckets are then made from cells. Heavy Water also has independent bucket recipes. List the actual routes instead of a fluid-only ladder.

Evidence: [en_us.json:1782](../../src/main/resources/assets/magnetization/lang/en_us.json#L1782), [deuterium_cell_from_hydrogen.json:25](../../src/generated/resources/data/magnetization/recipe/deuterium_cell_from_hydrogen.json#L25), [tritium_cell.json:6](../../src/generated/resources/data/magnetization/recipe/tritium_cell.json#L6), [helium_3_cell_from_tritium.json:6](../../src/generated/resources/data/magnetization/recipe/helium_3_cell_from_tritium.json#L6), [tritium_bucket_from_cell.json:6](../../src/generated/resources/data/magnetization/recipe/tritium_bucket_from_cell.json#L6).

### F16 · P2 · Helium-3 output terminology

Entries: `fluids/fusion_fuels`.

Reader claim: Helium-3 has the highest Tokamak output.

Correction: Distinguish generation, extraction rate, and energy per cell. Defaults: Tritium generates 3500 FE/t, Helium-3 3000 FE/t; Helium-3 has the higher extraction ceiling (24000 vs 16000 FE/t) and longer burn (7200 vs 4800 ticks), hence greater total energy. The neighboring Tritium page calls Tritium the highest FE/t, so this wording is internally ambiguous.

Evidence: [en_us.json:1785](../../src/main/resources/assets/magnetization/lang/en_us.json#L1785), [MagConfig.java:3496](../../src/main/java/com/stonytark/magnetization/config/MagConfig.java#L3496), [MagConfig.java:3499](../../src/main/java/com/stonytark/magnetization/config/MagConfig.java#L3499), [MagConfig.java:3501](../../src/main/java/com/stonytark/magnetization/config/MagConfig.java#L3501).

### F17 · P2 · Meteorite Core acquisition

Entries: `advanced/meteorite_core`.

Reader claim: Meteorite Core is worldgen-only.

Correction: Cores can be reassembled from four fragments and one magnetic-alloy ingot, and grown from a Meteorite Sapling. The loot table also drops the core itself along with fragments. Describe natural discovery and the repeatable acquisition routes.

Evidence: [en_us.json:1094](../../src/main/resources/assets/magnetization/lang/en_us.json#L1094), [meteorite_core_from_fragments.json:19](../../src/generated/resources/data/magnetization/recipe/meteorite_core_from_fragments.json#L19), [meteorite_core.json:9](../../src/generated/resources/data/magnetization/loot_table/blocks/meteorite_core.json#L9), [MeteoriteSaplingBlockEntity.java:67](../../src/main/java/com/stonytark/magnetization/content/meteorite/MeteoriteSaplingBlockEntity.java#L67).

### F18 · P2 · Enhanced Catalyst ingredients

Entries: `advanced/pyrrhotite_catalyst`.

Reader claim: The Enhanced Catalyst recipe includes Netherite Scrap.

Correction: It is a Basic Catalyst surrounded by four Pyrrhotite Ingots. The Cosmic recipe uses an Enhanced Catalyst, two fragments, and two Pyrrhotite Ingots. Keep the correct 3/5/7 reach values.

Evidence: [en_us.json:1116](../../src/main/resources/assets/magnetization/lang/en_us.json#L1116), [enhanced_pyrrhotite_catalyst.json:9](../../src/generated/resources/data/magnetization/recipe/enhanced_pyrrhotite_catalyst.json#L9), [cosmic_pyrrhotite_catalyst.json:16](../../src/generated/resources/data/magnetization/recipe/cosmic_pyrrhotite_catalyst.json#L16).

### F19 · P2 · Catalyst chaining and priority

Entries: `advanced/pyrrhotite_catalyst`.

Reader claim: A single burner can drive a long string through a Catalyst spine.

Correction: There is no catalyst-to-catalyst heat relay. Each catalyst reads heat directly adjacent to itself. Overlapping coverage works, and the hottest eligible source wins, not the larger catalyst tier. Explain reach and heat priority separately.

Evidence: [en_us.json:1114](../../src/main/resources/assets/magnetization/lang/en_us.json#L1114), [PyrrhotiteHeatResolver.java:29](../../src/main/java/com/stonytark/magnetization/content/pyrrhotite/PyrrhotiteHeatResolver.java#L29), [PyrrhotiteHeatResolver.java:30](../../src/main/java/com/stonytark/magnetization/content/pyrrhotite/PyrrhotiteHeatResolver.java#L30).

### F20 · P2 · Elytra ingot layout

Entries: `advanced/magnetic_elytra`.

Reader claim: Ferromagnetic ingots go in the corners and bottom.

Correction: The actual pattern is FPF / FEF / " F ": five magnetic-alloy ingots occupy both top corners, both middle side cells, and bottom center. The bottom corners are empty.

Evidence: [en_us.json:1126](../../src/main/resources/assets/magnetization/lang/en_us.json#L1126), [magnetic_elytra.json:17](../../src/generated/resources/data/magnetization/recipe/magnetic_elytra.json#L17), [magnetic_elytra.json:18](../../src/generated/resources/data/magnetization/recipe/magnetic_elytra.json#L18).

### F21 · P2 · Horse armor recipe layout

Entries: `advanced/armor_tools`.

Reader claim: Horse armor is seven ingots in a U shape.

Correction: All three shown horse-armor recipes use M M / MMM / M M, an H shape. Count seven is correct; replace the layout description or rely on the native recipe drawing.

Evidence: [en_us.json:1479](../../src/main/resources/assets/magnetization/lang/en_us.json#L1479), [en_us.json:1481](../../src/main/resources/assets/magnetization/lang/en_us.json#L1481), [en_us.json:1483](../../src/main/resources/assets/magnetization/lang/en_us.json#L1483), [maghemite_horse_armor.json:11](../../src/generated/resources/data/magnetization/recipe/maghemite_horse_armor.json#L11), [magnetite_horse_armor.json:11](../../src/generated/resources/data/magnetization/recipe/magnetite_horse_armor.json#L11), [ferromagnetic_horse_armor.json:11](../../src/generated/resources/data/magnetization/recipe/ferromagnetic_horse_armor.json#L11).

### F22 · P2 · Magnetite ore base yield

Entries: `basics/magnetite`.

Reader claim: Ore drops one to two Raw Magnetite before Fortune.

Correction: The default ore loot is one Raw Magnetite, with the standard Fortune ore-drops multiplier and a Silk Touch branch. There is no independent 1–2 base roll.

Evidence: [en_us.json:1420](../../src/main/resources/assets/magnetization/lang/en_us.json#L1420), [magnetite_ore.json:36](../../src/generated/resources/data/magnetization/loot_table/blocks/magnetite_ore.json#L36), [magnetite_ore.json:48](../../src/generated/resources/data/magnetization/loot_table/blocks/magnetite_ore.json#L48).

### F23 · P2 · Excavator renewability claim

Entries: `basics/magnetite`.

Reader claim: Magnetite is renewable from the Magnetic Excavator.

Correction: The excavator extracts and consumes existing world blocks and evaluates their loot. That is mining/collection, not a built-in renewable magnetite source. Name a real renewable route if one is intended, otherwise say it can be collected by an excavator.

Evidence: [en_us.json:1420](../../src/main/resources/assets/magnetization/lang/en_us.json#L1420), [MagneticExcavatorBlockEntity.java:813](../../src/main/java/com/stonytark/magnetization/content/excavator/MagneticExcavatorBlockEntity.java#L813), [MagneticExcavatorBlockEntity.java:730](../../src/main/java/com/stonytark/magnetization/content/excavator/MagneticExcavatorBlockEntity.java#L730).

### F24 · P2 · Magnetized tool polarity

Entries: `advanced/armor_tools`.

Reader claim: A held magnetized tool always vacuums ferromagnetic item drops.

Correction: Net SOUTH tools attract; net NORTH tools repel; opposite weighted contributions cancel. The neodymium double-radius statement is correct. Add polarity instructions so players can actually obtain item collection.

Evidence: [en_us.json:1469](../../src/main/resources/assets/magnetization/lang/en_us.json#L1469), [en_us.json:1471](../../src/main/resources/assets/magnetization/lang/en_us.json#L1471), [MagneticToolPullHandler.java:82](../../src/main/java/com/stonytark/magnetization/content/item/MagneticToolPullHandler.java#L82), [MagneticToolPullHandler.java:95](../../src/main/java/com/stonytark/magnetization/content/item/MagneticToolPullHandler.java#L95).

### F25 · P3 · Malformed elytra markup

Entries: `advanced/magnetic_elytra`.

Reader claim: The 4× susceptibility bonus uses $(4 4×)$.

Correction: The shipped text contains an unsupported command token and an unmatched trailing dollar sign. Use $(4)4×$(). The pinned Patchouli parser recognizes a color code only when the command is one hexadecimal character; unknown commands fall back to visible text. The numeric default itself is correct.

Evidence: [en_us.json:1125](../../src/main/resources/assets/magnetization/lang/en_us.json#L1125), [MagneticElytraItem.java:13](../../src/main/java/com/stonytark/magnetization/content/item/MagneticElytraItem.java#L13), [pinned Patchouli parser excerpt](evidence/guide-content/patchouli-parser.txt).

### F26 · P2 · Ship propulsion polarity

Entries: `ships/propulsion`.

Reader claim: Opposing-polarity magnets mounted aboard set up the ship-side attraction/repulsion.

Correction: Mounted magnet blocks increase susceptibility but do not set the ship pole. Ship polarity comes from the parity of Polarity Inverters. Make the propulsion example name the ship inverter count and world emitter poles; otherwise this chapter contradicts the correct Ship Polarity chapter.

Evidence: [en_us.json:1461](../../src/main/resources/assets/magnetization/lang/en_us.json#L1461), [ShipMagneticScanner.java:96](../../src/main/java/com/stonytark/magnetization/physics/ShipMagneticScanner.java#L96), [ShipMagneticScanner.java:106](../../src/main/java/com/stonytark/magnetization/physics/ShipMagneticScanner.java#L106).

### F27 · P2 · Anchor leveling claim

Entries: `ships/anchor_docking`.

Reader claim: Two anchors make the ship settle level.

Correction: Cooperating anchors damp angular velocity; they do not implement a restoring torque toward a level orientation. Say they reduce spin. A tilted ship can stop spinning while remaining tilted.

Evidence: [en_us.json:1466](../../src/main/resources/assets/magnetization/lang/en_us.json#L1466), [MagneticAnchorBlockEntity.java:136](../../src/main/java/com/stonytark/magnetization/content/anchor/MagneticAnchorBlockEntity.java#L136).

### F28 · P2 · Intrinsic undead magnetism

Entries: `basics/intro`.

Reader claim: Undead mobs are intrinsically magnetizable.

Correction: The shipping intrinsic entity tag does not include ordinary zombies or skeletons. Those mobs can react through eligible armor or a datapack entity-tag opt-in; the Magnetized effect only multiplies an already-positive susceptibility. Qualify the example instead of implying all undead react automatically.

Evidence: [en_us.json:1416](../../src/main/resources/assets/magnetization/lang/en_us.json#L1416), [magnetizable.json:4](../../src/main/resources/data/magnetization/tags/entity_type/magnetizable.json#L4), [FieldApplicator.java:808](../../src/main/java/com/stonytark/magnetization/physics/FieldApplicator.java#L808), [FieldApplicator.java:632](../../src/main/java/com/stonytark/magnetization/physics/FieldApplicator.java#L632).

## Ponder accuracy and presentation coverage

All scenes below have prior native full-playback evidence. Static markers, textual instructions and successful playback do not prove the corresponding machines were operated inside Ponder. This table records the actual explanatory coverage, including gaps that are not necessarily false statements.

| Scene | Accuracy assessment | Remaining explanatory gap |
| --- | --- | --- |
| `tokamak_ring` | Core-count and geometry instructions agree with solid-core rules. | The depicted 5×5 uses 16 coils and nine cores. Fuel insertion, coolant routing and shared FE forwarding are described but not demonstrated. |
| `fusion_panel` | Incorrect build geometry: F05. | NORTH-facing center is surrounded in X/Z rather than X/Y. No expansion or working I/O demonstration. |
| `railgun_pair` | Parallel rail layout and same-facing instruction agree with pairing rules. | No launched target, FE feed or auto-assembly demonstration. Qualify “every block” with strict interior, protected-block and scan limits. |
| `electrolyzer` | High-level water + FE → hydrogen instruction agrees. | Static machine only; no water/FE plumbing, output or fuel-chain demonstration. |
| `gas_exciter` | FE requirement and redstone disable rule agree; scope needs qualification. | Markers change purple → pink. “Entire” connected volume omits the configured flood-fill cap and loaded-chunk restriction. |
| `gas_vent` | 1000 mB source, outlet and adjacent excitation instructions agree. | Glass represents the cloud. No pipe, fluid selection, blocked-outlet failure or bucket-recovery demonstration. |
| `air_separator` | Mechanical input, 64 RPM default and five independent output tanks agree. | Shaft and five markers are placed. No GUI assignment, rotating shaft, gas-to-face legend, pipes or installed Isotope Separation Module. |
| `mhd_jet` | High-level magnet, conductive fluid and FE setup agrees. | Static machine only; no magnet installation, tank/FE connection or thrust. |
| `micro_thruster` | Ferrofluid + FE setup agrees. | Static machine only; no fluid input, power or thrust. |
| `ion_thruster` | Propellant tradeoffs and ship mounting instruction agree. | Three explicitly labeled gas markers; no supply connection, ship motion, exhaust-direction vector or hazard example. |
| `solar_sail` | Wrench aiming and empty-hand night-cutoff control agree. | Static panel only; no ship, day/night transition or cutoff interaction. |
| `kinetic_coil` | Passing magnetic ship induction instruction agrees. | Static coil only; no passing ship, generated FE or redstone pulse. |
| `homopolar_motor` | Installed magnet and shaft-output instruction agrees. | Static motor only; no magnet insertion, attached shaft or RPM comparison. |
| `structural_inducer` | Power, facing and range setup agrees at a high level. | Static inducer only; no staged structure, scan selection or launch. |
| `dipole_electromagnet` | Separated pole origins and wrench aiming instruction agree. | Static block only; no NORTH/SOUTH origins, field vectors or response example. |
| `rare_earth_magnets` | Two processing branches and material dependencies agree at a high level. | Four ores and two finished magnets; no intermediates, processing machines or recipe sequence. |
| `steam_rails_magnetism` | Train sharing and assembled-train exclusion agree with the compat path. | Only a coupler and electromagnet; no coupled consist, track, acceleration/braking or disassembly. |
| `copycat_magnetism` | Stored-material susceptibility and goggles text agree with the compat path. | An empty copycat is placed beside iron; iron is never applied to its stored material. No assembly or goggles classification demonstration. |

Scene implementation: [MagPonderPlugin.java](../../src/main/java/com/stonytark/magnetization/client/MagPonderPlugin.java). Scene inventory and text: [PonderSceneCatalog.java](../../src/main/java/com/stonytark/magnetization/compat/ponder/PonderSceneCatalog.java).

## Remaining documentation and validation gaps

1. **Cross-entry navigation.** No local `$(l:magnetization:...)` links exist anywhere in the shipped book text. “See” references name chapters in bold but do not navigate to them. The existing link-resolution test therefore passes without exercising a single authored local link. Add links for the actual referenced entries and verify their destinations in the native reader.
2. **Manual recovery and lifecycle guidance.** The book lacks reader instructions for `/magnetization manual`, the two default recovery recipes (Book + Raw Magnetite and Book + Lodestone), the default-disabled iron alternative and its collision risk, deferred automatic gifting, or the need to restart both client and server after re-enabling the package. README, config tooltips and lifecycle evidence explain parts of this; the manual should provide an access/configuration page that uses the same rules. Do not imply `/reload` alone rediscovers a removed book.
3. **Ponder semantic assertions.** Current tests check metadata/localization, and the playback fixture checks elapsed time/completion plus selected gas markers. They never assert that depicted multiblocks pass their shipping validators, which allowed F05 to pass. Add validator-backed checks for depicted Tokamak/Fusion builds and rail pairing. Assert actual Copycats stored material when teaching inheritance, and use real machine state or explicit diagram labeling for operational examples.
4. **Bounded and conditional behavior.** Qualify the Gas Exciter’s “entire connected” statement with the configured cell cap and loaded-chunk limit. Qualify railgun auto-assembly with protected/excluded blocks and scanning bounds. Do not present defaults as universal when server configuration or optional tags change them.
5. **Recipe/prose drift.** Native crafting pages render recipe data, while nearby prose repeats obsolete ingredients/counts. Prefer the native diagram for layouts; where numbers are instruction-critical, cross-check the referenced shipping recipe and default tags. Test behavioral facts, not merely that a chosen sentence still exists.
6. **Native page layout.** All 237 pages have source coverage; only selected pages have native UI evidence. Render the complete book, especially all 29 crafting and six smelting pages, dense compatibility lists and F25, at supported GUI scales. Check clipping, item readability, formatting, pagination and newly added links. No specific clipping defect is established by this audit.
7. **Optional compatibility and localization scope.** The authored book content is English. Translation-key resolution does not prove other-language layout or translation quality. Version the optional-mod profiles used for native checks; the 18-scene pack does not exercise every compatibility claim in the manual. Inspect other locale fallback and each advertised integration/config combination before labeling the whole book runtime-validated.
8. **Stale developer comments.** Some comments repeat superseded behavior (for example the Hematite floor and the old meteorite “Phase B” plan). Use executable rules and generated resources as the correction source, then update those comments with the player documentation. Meteorite crater generation exists in `MeteoriteCraterFeature`; the stale deferred-plan comment is not evidence that craters are absent.

Implementation sources for these gaps: [FieldManualGiver.java](../../src/main/java/com/stonytark/magnetization/content/FieldManualGiver.java), [CommonConfigSyncPayload.java](../../src/main/java/com/stonytark/magnetization/network/CommonConfigSyncPayload.java), [GasExcitation.java](../../src/main/java/com/stonytark/magnetization/content/fluid/GasExcitation.java), [LifecyclePresentationAuditClient.java](../../src/main/java/com/stonytark/magnetization/client/LifecyclePresentationAuditClient.java), [PatchouliFieldManualTest.java](../../src/test/java/com/stonytark/magnetization/data/PatchouliFieldManualTest.java), [PonderSceneCatalogTest.java](../../src/test/java/com/stonytark/magnetization/compat/PonderSceneCatalogTest.java).

## Entry review index

The complete resolved page text and source review targets are in [inventory.json](evidence/guide-content/inventory.json). The following index distinguishes confirmed defects from entries with no confirmed defect recorded; presentation and configuration coverage still have the limits above.

| Entry | Pages | Confirmed findings |
| --- | ---: | --- |
| [`advanced/armor_tools`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/armor_tools.json) | 8 | F21, F24 |
| [`advanced/biomes`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/biomes.json) | 2 | None recorded |
| [`advanced/compass_grapple`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/compass_grapple.json) | 2 | F07 |
| [`advanced/compatibility`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/compatibility.json) | 12 | None recorded |
| [`advanced/configuration`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/configuration.json) | 3 | None recorded |
| [`advanced/cosmic_compass`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/cosmic_compass.json) | 2 | None recorded |
| [`advanced/deuterium_oxide`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/deuterium_oxide.json) | 1 | None recorded |
| [`advanced/diamagnetic`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/diamagnetic.json) | 2 | None recorded |
| [`advanced/fe_rf_power`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/fe_rf_power.json) | 4 | None recorded |
| [`advanced/ferrofluid`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/ferrofluid.json) | 1 | F11 |
| [`advanced/g_force_cushion`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/g_force_cushion.json) | 1 | None recorded |
| [`advanced/hematite_block`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/hematite_block.json) | 2 | F09 |
| [`advanced/hematite_lens`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/hematite_lens.json) | 3 | None recorded |
| [`advanced/imprint_module`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/imprint_module.json) | 3 | None recorded |
| [`advanced/lirm`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/lirm.json) | 1 | None recorded |
| [`advanced/magnet_burning`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/magnet_burning.json) | 1 | None recorded |
| [`advanced/magnetic_anvils`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/magnetic_anvils.json) | 1 | None recorded |
| [`advanced/magnetic_elytra`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/magnetic_elytra.json) | 3 | F14, F20, F25 |
| [`advanced/magnetic_item_frame`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/magnetic_item_frame.json) | 1 | None recorded |
| [`advanced/magnetized_ferrofluid`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/magnetized_ferrofluid.json) | 1 | None recorded |
| [`advanced/magnetoresistive_boots`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/magnetoresistive_boots.json) | 1 | None recorded |
| [`advanced/meteorite_core`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/meteorite_core.json) | 4 | F17 |
| [`advanced/meteorite_fragment`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/meteorite_fragment.json) | 3 | F03 |
| [`advanced/meteorite_sapling`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/meteorite_sapling.json) | 3 | F04 |
| [`advanced/mr_armor`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/mr_armor.json) | 1 | None recorded |
| [`advanced/mr_fluid`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/mr_fluid.json) | 1 | None recorded |
| [`advanced/mr_fluid_golem`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/mr_fluid_golem.json) | 1 | None recorded |
| [`advanced/ore_compass`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/ore_compass.json) | 1 | None recorded |
| [`advanced/pyrrhotite_block`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/pyrrhotite_block.json) | 3 | None recorded |
| [`advanced/pyrrhotite_catalyst`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/pyrrhotite_catalyst.json) | 7 | F18, F19 |
| [`advanced/repulsor_gun`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/repulsor_gun.json) | 4 | None recorded |
| [`advanced/titanomagnetite_block`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/titanomagnetite_block.json) | 2 | F12, F13 |
| [`advanced/tokamak`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/advanced/tokamak.json) | 4 | None recorded |
| [`basics/anomaly_terrain`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/basics/anomaly_terrain.json) | 10 | None recorded |
| [`basics/ferromagnetic_ingot`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/basics/ferromagnetic_ingot.json) | 2 | F01 |
| [`basics/intro`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/basics/intro.json) | 2 | F28 |
| [`basics/iron_oxide_family`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/basics/iron_oxide_family.json) | 11 | None recorded |
| [`basics/lodestone_core`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/basics/lodestone_core.json) | 2 | F03 |
| [`basics/magnetic_plate`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/basics/magnetic_plate.json) | 2 | F02 |
| [`basics/magnetite`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/basics/magnetite.json) | 5 | F01, F02, F03, F22, F23 |
| [`basics/polarity`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/basics/polarity.json) | 2 | None recorded |
| [`emitters/anchor`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/anchor.json) | 2 | F06 |
| [`emitters/barkhausen`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/barkhausen.json) | 1 | None recorded |
| [`emitters/dipole_electromagnet`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/dipole_electromagnet.json) | 3 | None recorded |
| [`emitters/electromagnet`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/electromagnet.json) | 2 | None recorded |
| [`emitters/emp_charge`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/emp_charge.json) | 1 | None recorded |
| [`emitters/excavator`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/excavator.json) | 2 | None recorded |
| [`emitters/gyrostabilizer`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/gyrostabilizer.json) | 1 | None recorded |
| [`emitters/halbach`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/halbach.json) | 1 | F10 |
| [`emitters/induction_pad`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/induction_pad.json) | 1 | None recorded |
| [`emitters/kinetic_coil`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/kinetic_coil.json) | 1 | None recorded |
| [`emitters/kinetic_electromagnet`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/kinetic_electromagnet.json) | 1 | None recorded |
| [`emitters/magnetostrictive_sensor`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/magnetostrictive_sensor.json) | 1 | None recorded |
| [`emitters/permanent`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/permanent.json) | 2 | None recorded |
| [`emitters/repulsor`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/repulsor.json) | 1 | None recorded |
| [`emitters/structural_inducer`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/structural_inducer.json) | 2 | None recorded |
| [`emitters/switch`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/switch.json) | 2 | None recorded |
| [`emitters/tractor`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/emitters/tractor.json) | 1 | None recorded |
| [`fluids/fusion_fuels`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/fluids/fusion_fuels.json) | 7 | F15, F16 |
| [`fluids/gallium`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/fluids/gallium.json) | 2 | None recorded |
| [`fluids/lithium`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/fluids/lithium.json) | 2 | None recorded |
| [`fluids/mixed_gallium`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/fluids/mixed_gallium.json) | 1 | None recorded |
| [`fluids/noble_gases`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/fluids/noble_gases.json) | 3 | None recorded |
| [`fluids/solid_gallium`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/fluids/solid_gallium.json) | 1 | None recorded |
| [`gear/gallium_gear`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/gear/gallium_gear.json) | 3 | None recorded |
| [`gear/gas_detector`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/gear/gas_detector.json) | 2 | None recorded |
| [`gear/rare_earth_magnets`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/gear/rare_earth_magnets.json) | 17 | None recorded |
| [`machines/air_separator`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/machines/air_separator.json) | 5 | None recorded |
| [`machines/automation`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/machines/automation.json) | 3 | None recorded |
| [`machines/electrolyzer`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/machines/electrolyzer.json) | 2 | None recorded |
| [`machines/gallium_golem`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/machines/gallium_golem.json) | 2 | None recorded |
| [`machines/gas_exciter`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/machines/gas_exciter.json) | 2 | None recorded |
| [`machines/gas_vent`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/machines/gas_vent.json) | 3 | None recorded |
| [`machines/iron_oxide_golems`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/machines/iron_oxide_golems.json) | 6 | None recorded |
| [`ships/alfven_backpack`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/alfven_backpack.json) | 1 | None recorded |
| [`ships/anchor_docking`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/anchor_docking.json) | 1 | F27 |
| [`ships/fusion_thruster`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/fusion_thruster.json) | 4 | None recorded |
| [`ships/homopolar_motor`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/homopolar_motor.json) | 1 | None recorded |
| [`ships/ion_thruster`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/ion_thruster.json) | 4 | None recorded |
| [`ships/lenz`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/lenz.json) | 1 | None recorded |
| [`ships/mhd_jet`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/mhd_jet.json) | 1 | None recorded |
| [`ships/micro_thruster`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/micro_thruster.json) | 1 | None recorded |
| [`ships/propulsion`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/propulsion.json) | 2 | F26 |
| [`ships/railgun`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/railgun.json) | 4 | None recorded |
| [`ships/ship_polarity`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/ship_polarity.json) | 2 | None recorded |
| [`ships/solar_sail`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/solar_sail.json) | 1 | None recorded |
| [`ships/thrust_controls`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/thrust_controls.json) | 2 | None recorded |
| [`ships/vector_core`](../../src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/vector_core.json) | 1 | F08 |

## Recommended correction order and acceptance

1. Correct F01–F05 first. Native recipe drawings and prose must agree; the scene’s Fusion panel must pass `FusionThrusterPanel.validate` with its rendered facing.
2. Correct polarity, binding, recording/reset, catalyst and fluid instructions (F06–F24, F26–F28), then the formatting token F25. Verify the actual interaction described for each correction and update related repeated statements.
3. Add access/lifecycle guidance and functional cross-entry links. Check recovery/gift/restart workflows with the existing native fixture, and manually follow the new book links.
4. Upgrade the scenes that promise operational explanations, prioritizing copied material, coupled trains, fluid/FE routing and input/output changes. Keep substitute diagram markers explicitly labeled.
5. Run the targeted documentation tests after edits, validator-backed scene checks, and a complete native page rendering sweep. Preserve captures and source hashes for the corrected content rather than reusing the currently inaccurate scenes as proof of correctness.
