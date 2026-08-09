# Magnetization 1.3.0

## Full changelog since 1.2.2

Magnetization 1.3.0 is a major content, compatibility, and stability release for Minecraft 1.21.1. It adds two ship-scale propulsion systems, a complete fusion-fuel progression, new world generation and material families, deeper support for popular Create addons, expanded in-game documentation, and a large collection of physics, automation, GUI, persistence, and multiplayer fixes.

## Important upgrade notes

- Minecraft remains fixed at **1.21.1**.
- Magnetization's own published NeoForge range is **21.1.200 or newer, but below 22.0**. Released Create 6.0.10 requires **21.1.219+**, making 21.1.219 the effective floor for the complete required stack. Development and testing use NeoForge **21.1.248** without forcing users to that newest patch.
- Required dependency floors are now Create **6.0.10+**, Sable **2.0.3+**, Create: Aeronautics **1.3.0+**, Simulated **1.3.0+**, and TerraBlender **4.1.0.8+**. Development uses Create's newer official Maven build without requiring that unreleased build from users.
- Existing worlds are supported. New Lithium and Helium-3 world generation appears in newly generated terrain.
- Magnet-slot machines now consume their installed magnet over time by default. This can be disabled to restore the old infinite-magnet behavior.
- Railguns remain uncapped by default. Server administrators may opt into a configurable maximum rail length and power ceiling.
- Optional integrations remain soft dependencies and are loaded only when their mods are present. Major integrations now also have explicit master switches.

## New propulsion: Fusion Thruster

- Added the **Fusion Thruster**, an expandable flat-panel multiblock designed for Sable/Create: Aeronautics ships.
- Build a panel of Fusion Thruster interior blocks surrounded by a Tokamak Coil perimeter. The minimum panel is 3x3x1, and larger valid panels produce exponentially more thrust.
- The panel pushes the ship opposite its exhaust-facing side and now respects its actual orientation on rotated or moving ships.
- The entire formed panel shares fuel, FE, state, and status. Fuel and FE can be inserted through any valid interior block, while Tokamak Coil frame blocks can also accept panel power.
- Tank capacity scales with panel interior size, allowing larger engines to carry proportionally more fuel.
- Supports Hydrogen, Deuterium Oxide, Tritium, and Helium-3. Higher-tier fuels provide stronger and more efficient propulsion.
- Supports direct fluid piping and bucket-based automation.
- Added formed-state lighting, exhaust behavior, active textures, GUI status, hover information, goggles information, and multiblock construction previews.
- Added configurable panel-size limits, thrust scaling, speed limits, FE capacity and transfer, FE cost, tank capacity, fluid cost, and per-fuel strength/density values.
- Fixed panel direction, side-coil orientation, top-face orientation, shared power input, fuel synchronization, active-state persistence, extreme-capacity math, incorrect HUD size/state, incorrect GUI fuel bars, and glowing ghost blocks left after a formed panel was broken.

## New propulsion: Railgun

- Added the **Railgun Emitter**, paired conductive rail channels, and the **Railgun Remote**.
- Two valid parallel rail lines form a launch channel that captures a Sable ship or magnetic entity and accelerates it along the rail axis.
- Launch force scales powerfully with rail length, making longer installations meaningful rather than merely extending the launch channel.
- Railgun launch behavior now preserves ships through the complete Sable lifecycle instead of despawning them at the end of the rail.
- Ships leave the muzzle with retained forward velocity and continue travelling after the launch force ends.
- Added automatic and manual firing modes.
- In automatic mode, a valid target entering the channel is launched without a remote.
- In manual mode, a paired remote allows the railgun to hold a target for boarding and launch it from the player's hand.
- Removing a paired remote from the emitter no longer cancels manual mode or releases the held ship.
- Sneak-using a remote unbinds it even if the railgun was destroyed, unloaded, or is in another dimension.
- Remote tooltips show the bound railgun's name, coordinates, dimension, direction, and rail length, and report why a remote cannot currently fire.
- One remote pairing covers both emitters in the paired assembly and survives save/reload and supported portal transfers.
- Railguns can break obstructing blocks while launching. Rails and emitters remain protected from their own destruction pass.
- Block-breaking mode now breaks an obstruction before collision response can bounce the launched ship backward, allowing the launch to continue forward.
- Non-breaking mode now resolves collisions without allowing ships to tunnel through terrain or disappear into the ground.
- Block destruction now respects cancellable break events, claim/protection mods, and the `doTileDrops` game rule.
- More than two parallel rails dissipate the arc. Three-rail detection is now reliable across the complete supported gap range.
- Added live GUI/HUD status for rail length, mode, target state, arc state, and stored FE.
- Added Ponder/build previews, manual-mode guidance, lifecycle tests, moving/rotated ship tests, collision tests, remote persistence tests, and portal-remapping tests.
- Added configuration for enablement, automatic fire, cooldown, hold duration/cost, launch update interval, minimum rail length, channel gap/thickness, lateral damping, base force, length exponent, entity scaling, FE costs/capacity/receive rate, block breaking, and destruction budget.
- Added an optional maximum rail length/power ceiling. It is **disabled by default**, preserving uncapped scaling unless a server administrator enables it.

## New machine: Electrolyzer

- Added the powered **Electrolyzer**, the entry point to the new fusion progression.
- Feed it water and FE to produce Hydrogen.
- Its basin visually fills with water and displays Hydrogen output while processing.
- Supports standard NeoForge fluid and energy capabilities for pipes, pumps, tanks, and FE networks.
- Rejects invalid inputs and stalls safely when its output is full without consuming extra water or FE.
- Added GUI, machine status, Jade/WTHIT/The One Probe support, recipe-viewer information, Patchouli documentation, and dedicated item/block textures.
- Optional compatible coil ingredients may be used in its recipe when the corresponding addons are installed.
- Fixed basin rendering, machine/item texture consistency, excessive copper coloring, GUI text overlapping slots and bars, and output synchronization.

## Fusion fuels, fluids, and isotope progression

- Added the progression chain **Water -> Hydrogen -> Deuterium Oxide -> Tritium -> Helium-3**.
- Tritium breeding requires Lithium, creating a full material and machine progression instead of a single endgame recipe.
- Added Hydrogen, Tritium, Helium-3, and Liquid Lithium fluids with buckets, textures, tinting, recipes, tags, machine behavior, and tooltips.
- Added Tritium and Helium-3 fuel cells for the Tokamak.
- Renamed the former **Helium-3 Gas** item to **Helium-3 Crystal** everywhere it appears.
- Renamed the naturally occurring Helium-3 Crystal block to **Helium-3 Geode**.
- Added **Solid Helium-3**, a reversible storage block crafted from nine Helium-3 Crystals.
- Helium-3 Geodes generate rarely at deepslate depths in the Overworld and in the End and drop Helium-3 Crystals with normal ore-loot behavior.
- Helium-3 remains obtainable through late-stage fuel processing as well as world generation, closing the recipe-viewer acquisition gap.
- Reworked the Helium-3 Crystal, geode, storage block, inventory highlight, fluid, bucket, cell, and related colors around a more natural crystalline palette.
- Updated Hydrogen fluid coloring to match its texture family more closely.
- Added isotope badges and descriptive tooltips so Hydrogen, Deuterium, Tritium, Helium-3, and Lithium remain distinguishable without relying only on color.
- Tooltips report live configured thrust, duration, conductivity, Tokamak generation rate, burn time, and total energy where appropriate.
- Fusion gases now behave like bounded inverse water: they rise, spread along supported ceilings, turn upward around ceiling edges, avoid unsupported sky branches, and disappear safely at the world ceiling.
- Fixed gas waterfalls, source reseeding, excessive horizontal spread, unsupported flow, sky-limit behavior, rendering, bucket placement, and save/reload handling.

## Lithium, Gallium, and expanded material families

- Added Lithium Ore and Deepslate Lithium Ore, Raw Lithium, refined Lithium, and Liquid Lithium.
- Added compact **Lithium Block** and **Raw Lithium Block** storage recipes.
- Added **Raw Gallium Block** storage and the refined phase-changing Solid Gallium block.
- Added **Ferromagnetic Block** storage for Ferromagnetic Ingots.
- Added complete sword, pickaxe, axe, shovel, hoe, helmet, chestplate, leggings, boots, and horse-armor families for **Lithium, Pyrrhotite, Hematite, and Titanomagnetite**.
- Integrated the new families with vanilla tool/armor tags, enchantability, repairs, recipes, creative tabs, loot, advancements, magnetized-tool abilities, metal-armor reactions, item models, translations, tooltips, and Patchouli pages.
- Magnetite, Ferromagnetic Alloy, Maghemite, Gallium, Lithium, Pyrrhotite, Hematite, and Titanomagnetite now present consistent tool and armor progression.
- Corrected missing ferromagnetic behavior for Maghemite, Pyrrhotite, Hematite, and Titanomagnetite item forms.
- Corrected Maghemite tools so they count as metal gear consistently with Maghemite armor.
- Added common storage, raw-material, ingot, tool, armor, and enchantment tags for cross-mod interoperability.

## Machine and fuel rebalances

- The **Tokamak** now supports three distinct fuel tiers:
  - Deuterium provides the baseline D-D reaction.
  - Tritium provides high raw D-T power.
  - Helium-3 provides the endgame D-He3 efficiency tier with the strongest sustained output.
- Added separate configurable generation, output-rate, and burn-duration values for the new Tokamak fuels.
- The **MHD Jet** now requires a conductive working fluid: Gallium, Mixed Gallium, or Liquid Lithium.
- Liquid Lithium provides the strongest default MHD conductivity.
- The **Ferrofluid Micro-Thruster** now accepts Magnetized Ferrofluid for a configurable thrust bonus.
- Fixed Magnetized Ferrofluid buckets incorrectly becoming plain Ferrofluid when inserted into the Micro-Thruster.
- Homopolar Motor and MHD Jet magnet slots now consume their installed magnets over a strength-, quantity-, and block-form-scaled burn time.
- Storage blocks last substantially longer than individual ores or ingots.
- The Vector Core remains a non-consumed Repulsor catalyst.
- Added configurable base burn time, strength scaling, block-form multiplier, and a master toggle for magnet consumption.

## Automation and capabilities

- Hoppers, Create funnels, belts, mechanical arms, and compatible item pipes can now feed every item-burning Magnetization machine.
- Added automated fuel input for Tokamak cells, Homopolar Motor and MHD Jet magnets, and bucket-fed Micro/Fusion Thrusters.
- Machines reject the wrong fuel, cells, buckets, fluids, and slot items.
- Active fuel cannot be extracted to reset a burn timer or steal unconsumed machine fuel.
- Empty buckets and containers can be extracted for closed-loop automation.
- Fusion Thruster input through any interior block feeds the shared multiblock tank.
- Added a **Hopper Fuel Intake** configuration switch, enabled by default.
- Thruster fluid capabilities are insert-only: pipes may refuel them but cannot siphon unburnt propellant back out.
- Fixed multiple bucket duplication paths involving Ferrofluid, MR Fluid, Deuterium Oxide, Mixed Gallium, and growing magnetic-fluid tendrils.

## New Dipole Electromagnet and analog redstone control

- Added the **Dipole Electromagnet**, including both active poles, all six orientations, recipes, models, block states, GUI/HUD reporting, Ponder coverage, and GameTests.
- Both poles contribute correctly under the shared per-ship acceleration cap instead of one pole starving the other.
- Added optional analog-redstone force scaling for the Electromagnet, Dipole Electromagnet, Magnetic Anchor, Repulsor Coil, Tractor Beam, Magnetic Excavator, and Structural Inducer.
- Analog scaling is disabled by default for existing-world compatibility.
- With scaling enabled, redstone level 1 produces the Weak-tier force and level 15 produces the Extreme-tier force, with geometric steps between them.
- The selected GUI tier continues to control range; the redstone level controls force.
- FE-powered operation continues at full configured force, and Hematite/Halbach modifiers scale from the throttled result.
- GUI, goggles, accessibility meters, and HUD integrations report the effective throttled force rather than a misleading nominal tier.

## Multiblock previews, Ponder, goggles, and documentation

- Added or completed build previews for the Fusion Thruster, Tokamak ring, Railgun, and other multiblock construction paths.
- Audited wrench, goggles, and Ponder coverage so blocks use the appropriate interaction and documentation surface.
- Added Ponder guidance for supported optional integrations where Magnetization contributes behavior, including Steam 'n' Rails and Copycats+.
- Expanded the Patchouli Field Manual to cover the complete 1.3.0 progression, multiblocks, fusion fuels, equipment families, automation, ship polarity, and advanced systems.
- Audited Field Manual item names, recipes, links, icons, and progression text after the Helium-3 and material-family changes.
- Added visible progression advancements for isotope processing, machine construction, multiblock completion, and Railgun use.
- Added construction and usage tooltips across machines, fuels, materials, tools, armor, and multiblocks.

## HUD, recipe viewers, and accessibility

- Jade, WTHIT, and The One Probe now expose live information for Magnetization machines and emitters rather than only basic emitter information.
- Added live status for the Electrolyzer, Fusion Thruster, Railgun, Tokamak, Homopolar Motor, MHD Jet, Micro-Thruster, Kinetic Coil, Solar Sail, and other machine blocks.
- Unified machine display data so GUI and HUD values come from the authoritative server, including on multiplayer servers with retuned configuration values.
- Fixed stale/frozen Railgun status, incorrect Fusion Thruster panel size and fuel state, Tokamak bars accumulating sync packets, and machine bars using client-local capacities.
- JEI, REI, and EMI now share one information catalog covering ferromagnetic materials, Excavator targets, Magnetite, specialist magnetic ores, Lithium, Gallium, fusion fuels, the Electrolyzer, Dipole Electromagnet, Structural Inducer, MHD Jet, Fusion Thruster, Tokamak, and Railgun.
- Added readable synthetic recipe identifiers and translated common material-tag names where required by the recipe viewers.
- Added Just Enough Resources world-generation/drop entries for all magnetic ore families, Lithium, and Helium-3 Geodes in both supported dimensions.
- Fixed the missing recipe-viewer path for obtaining Helium-3 and audited the remaining information gaps.
- Polarity now includes shape/text channels in addition to color: North, South, and neutral states have distinct glyphs.
- Strength now includes filled/hollow pip meters in addition to color and translated text.
- Fuel tier and active/idle machine state are explicitly named.
- Fixed raw internal enum names appearing in GUI/HUD text.

## Optional compatibility added or expanded

- **Create: AeroPortals**: refreshes magnetic ship state after cross-dimensional transfer, remaps installed and player-held Railgun Remotes, preserves connected child ships, and repairs transferred Simulated swivel coordinates/constraints.
- **Immersive Aeronautics / Immersive Portals**: preserves ship and Railgun state through cross-dimensional reconstruction and propagates magnetic fields through reachable portal apertures with transformed origin, axis, scale, clipping, and recursion protection.
- **Create: Coasters Simulated**: coaster cars react through the normal Sable magnetic-force path; Structural Inducers recognize an already assembled coaster train instead of duplicating it.
- **Create: Big Cannons**: cannon shells, autocannon rounds, and projectile bursts react to magnetic fields; cannon materials receive appropriate magnetic and eddy-current roles.
- **Create: New Age**: native magnets emit fields using New Age's published strength ladder; motors, Energisers, connectors, coils, and wires receive magnetic/conductive roles; supplemental progression recipes accept Magnetization materials.
- **Create Crafts & Additions**: powered Tesla Coils emit charge-scaled fields and trigger LIRM; electric machines/materials receive magnetic/conductive roles; motor and alternator progression recipes accept compatible magnets.
- **Immersive Engineering**: powered Electromagnets and Tesla Coils emit fields, Railgun Shots react to fields, machines/materials gain appropriate roles, and Mixer/Metal Press recipes bridge Ferrofluid and Magnetic Plates.
- **Create: The Factory Must Grow**: Hydrogen and Lithium interoperate in both directions; magnetic materials, machines, industrial fluids, gas tags, fuel tanks, crushing, casting, steelmaking, and component recipes bridge both progression trees. TFMG magnets can power Magnetization magnet-slot machines, molten steel can feed the MHD Jet, and an opt-in voltage-scaled Polarizer field is available.
- **Create: Tracks**: Track Mounts, suspension tracks, and drive wheels contribute magnetic susceptibility; assembled tracked vehicles react as whole Sable ships; Structural Inducer assembly preserves Track Mount block entities.
- **Create: Steam 'n' Rails**: magnetic force is projected along the rail and applied once to the complete coupled train, preserving the track graph; Locometal/mechanical parts receive material parity; assembled trains are excluded from Structural Inducer block capture; the Track Coupler gains Ponder guidance.
- **Create: Diesel Generators**: engine structures contribute susceptibility/conductivity while retaining native kinetic/goggle behavior; Ferrofluid-loaded sprayers and turrets can apply Magnetized.
- **Create: Copycats+**: susceptibility is inferred from each block's persisted copied material, including multi-material copycats and Sable assemblies; goggles show the resolved material class and Ponder explains the behavior.
- **Create: Enchantment Industry**: metal processing machines receive conductive parity while experience fluids and enchantment processing remain intentionally non-magnetic.
- **Create: Ender Transmission**: compatibility tests cover linked Create rotation, chunk tickets, Magnetization fluid transport, and item data-component preservation; an experimental one-hop field relay exists but is disabled by default.
- **Alex's Caves**: Azure and Scarlet Magnets now project opposite-polarity ship fields without doubling Alex's Caves entity force; neodymium/magnet materials, machine fuels, shared recipes, Ferrofluid, levitation, and effect-coexistence controls were expanded.
- Existing Magnetizing, Create: Magnetics, Simulated, broader common-metal tags, modded metal armor, metallic entities, cross-mod lightning, and standard FE/RF interoperability remain supported.
- Optional dependency upper bounds remain open where the integration can safely tolerate newer compatible versions. NeoForge alone remains intentionally capped below 22.0.
- Added individual master switches for optional HUD, recipe-viewer, guide-book, equipment-slot, Create-addon, and behavior integrations so administrators and pack authors can disable an integration cleanly without uninstalling its mod.
- Added isolated compatibility GameTest profiles and release-matrix coverage for published optional integrations.

## Configuration and administration

- Split development dependency pins from published user-facing minimum versions.
- Added a complete generated configuration reference and freshness check.
- Added synchronization and validation for common configuration so server-authoritative capacities, limits, and behavior reach clients correctly.
- Added master toggles and independent behavior controls for optional integrations.
- Added soft-disable handling for blocks and items. Disabled content is hidden where appropriate and placed instances become inert rather than bypassing the setting through alternate interactions.
- Added Structural Inducer pull acceleration, speed, arrival, timeout, scan interval, structure count, and tunnel-budget controls.
- Added tool-pull scan interval tuning.
- Restored configurable mountain Magnetite world generation.
- Added versioned per-world/chunk migrations for data changes and surface-biome repainting.
- Added `/magnetization debug audit` for loaded-level migration state, transient fields, AE2 meteorite fields, Railgun/field/fluid caches, and other lifecycle diagnostics.
- Expanded `/magnetization version` and playtest tooling for support reports and release verification.

## Physics, persistence, and world safety fixes

- Fixed Railgun launches deleting ships, releasing them without meaningful velocity, or dropping them immediately after muzzle exit.
- Fixed Railgun non-breaking collision tunneling and breaking-mode bounce-before-destruction behavior.
- Fixed manual hold/fire state, remote lifecycle, save/reload, chunk unload, cross-dimension, and portal-transfer handling.
- Fixed emitters losing their custom range beside Polarity Inverters, Hematite, Halbach arrays, or Hematite Lenses.
- Fixed directional blocks losing their facing when rotated, mirrored, cloned, placed by structures, or deployed through schematics.
- Fixed Magnetic Switch operation while mounted on a Sable sub-level; it now scans from its real world position and ignores its own craft.
- Fixed cooperative multi-anchor stabilization failing to engage.
- Fixed Pyrrhotite heat activation failing to run.
- Fixed ship-affecting systems throwing when Sable removed a craft during the same physics tick.
- Fixed transient lightning fields, AE2 meteorite scans, connected-ship caches, fluid registries, compass targets, and other per-world state leaking between worlds in one game session.
- Fixed existing Ferrofluid, Magnetized Ferrofluid, MR Fluid, Gallium, and Mixed Gallium pools losing their special behavior after save/reload.
- Surface-biome repainting is now persistent and versioned, preventing restarts from repainting processed terrain or overwriting player builds.
- Magnetic Excavator, Railgun, and Structural Inducer world changes now respect protection events and block-drop rules.
- Fixed the Grapple continuing to pull a player after death/respawn and cleaned up player state on logout, dimension change, and server stop.
- Fixed the Induction Pad losing most of its stored FE during reload.
- Fixed the Meteorite Sapling using full-block collision despite its sapling model.
- Fixed magnetite oxidation timestamps fragmenting otherwise identical items into unmergeable inventory stacks.
- Fixed Magnetoresistive hover state being processed on both client and integrated server.
- Fixed a Meteorite Core placing block-form fuel instead of accepting it as fuel.

## GUI, rendering, textures, and polish

- Audited all block-machine GUIs for labels overlapping slots, tanks, and bars.
- Fixed overlaps in the Ferrofluid Micro-Thruster, Fusion Thruster, Railgun, Electrolyzer, and other machine screens.
- Fixed large FE/fluid values wrapping through undersized menu data channels and drawing empty or incorrect bars.
- Removed duplicate item-tooltip rendering in machine screens.
- Reworked Fusion Thruster and Railgun textures and corrected face orientation.
- Added distinct Electrolyzer block/item textures and visible basin contents.
- Reworked Helium-3 assets into a natural crystal/geode/storage family and synchronized the inventory badge and fluid palette.
- Updated Hydrogen fluid tinting to match its related textures.
- Refreshed storage blocks so refined material families are recognizable by pattern and not only hue.
- Reworked Hematite ore, Electromagnet/Kinetic Electromagnet top faces, Lodestone Core, magnet-panel symmetry, Repulsor Gun, and Magnetic Grapple visuals.
- Repulsor Gun and Magnetic Grapple now use distinct detailed 3D models with corrected held transforms.
- Added visible Repulsor cone rings, a Grapple tether line, and short-lived glowing fired-state muzzle models.
- Added or corrected item tooltip highlights across the entire mod inventory.
- Fixed the emitter hum continuing after leaving a world.

## General bug fixes

- Fixed an anvil event crash that could also duplicate repaired, enchanted, or renamed items.
- Fixed machine fuel/fluid bars reading from client settings instead of server settings in multiplayer.
- Fixed the Tokamak HUD energy bar accumulating values and appearing permanently full.
- Fixed Fusion Thruster and Railgun live status not updating for nearby clients.
- Fixed closed or oversized machine inventories accepting invalid content through automation paths.
- Fixed tools in the Magnetic Excavator ignoring Unbreaking.
- Fixed sneak-wrenching emitters causing client-side flicker/desynchronization.
- Fixed Ferrofluid/MR/Gallium container and source-cell duplication exploits.
- Fixed thruster tanks allowing pipes to drain unburnt fuel.
- Fixed Solar Sail thrust stopping because sideways or vertical velocity counted against its forward-speed cap.
- Fixed config tooltips stored under incorrect sections.
- Fixed optional content recipes and conditions bypassing their associated compatibility/master settings.
- Hardened Curios use packets against malformed values and guarded Repulsor recoil/advancement paths.
- Curios Repulsor Gun and Magnetic Grapple activation now share the same cooldown, item-state, sound, and validation path as normal in-hand use.
- Corrected dedicated-server client-class loading hazards in optional Curios networking.
- Corrected optional cross-mod tag references so absent metal-provider mods no longer break tag loading.
- Corrected NeoForge conditional recipe formatting for 1.21.1.
- Added warning/diagnostic logging around previously silent Sable bridge and registry failures.

## Performance and networking

- Idle Fusion Thrusters, MHD Jets, Micro-Thrusters, Electrolyzers, Tokamaks, and Railgun Emitters no longer resend unchanged full state on a fixed interval.
- Large Magnetized Ferrofluid pools now use spatially limited neighborhood work instead of quadratic whole-pool comparisons.
- Fluid field passes skip expensive work when no eligible ship/entity is nearby.
- Connected Sable assembly walks are cached briefly and shared by emitters on the same craft.
- Item-vacuum and anomaly-chaos scans filter during entity collection rather than collecting every entity and filtering afterward.
- Surface repainting now honors a strict examination budget, skips entirely when both relevant biomes are disabled, and reduces temporary allocation.
- Emitter block-path soft-disable lookup is memoized.
- Corrected ship-scan fallback timing to match the configured default.
- Added safer bounded handling for very large multiblock capacities, costs, and GUI scales.

## Testing and release tooling

- Added extensive unit tests and GameTests for new machines, multiblocks, fuel progression, automation, configuration synchronization, migrations, Railgun lifecycle/velocity/collisions, moving and rotated ships, portal transfers, Curios activation, optional compatibility, and world generation.
- Added isolated optional-mod runtime profiles so one compatibility stack cannot contaminate another.
- Added minimal dedicated-server, standard GameTest, normal-client, compatibility-client, release-gate, and full compatibility-matrix tasks.
- Added bounded GameTest supervision and stale-process/world-lock cleanup for repeatable headless runs.
- Added persistent prepared Test Lab and Survival Progression playtest profiles plus GUI/playtest automation and save/reopen checks.
- Added generated-data validation for recipes, loot tables, mining tags, crater templates, registrations, language entries, material-family completeness, Patchouli coverage, and GUI layout.

## Documentation

- Updated the README for current dependency floors, optional compatibility, material families, Helium-3 naming/storage, recipe-viewer coverage, Railgun administration, data generation, and release commands.
- Updated the generated configuration reference.
- Expanded and audited the Patchouli Field Manual.
- Added an optional compatibility roadmap and detailed release/playtest audit documentation.

Thank you to everyone who tested the 1.3.0 railgun behavior, reported edge cases, and helped validate the new progression and compatibility work.
