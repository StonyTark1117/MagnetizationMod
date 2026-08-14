# Magnetization 1.4.1

## Full changelog since 1.4.0

Magnetization 1.4.1 is a corrective content and stability release for Minecraft 1.21.1. It finishes several systems that 1.4.0 advertised prematurely, adds coolant and golem content, restores behavior lost in earlier updates, and strengthens the release gates that now verify those features.

## Important upgrade notes

- Minecraft remains fixed at **1.21.1**.
- Existing worlds are supported; no world reset is required.
- Required dependency floors are unchanged: NeoForge **21.1.219+ and below 22.0** for the complete stack, Create **6.0.10+**, Sable **2.0.3+**, Create: Aeronautics **1.3.0+**, Simulated **1.3.0+**, and TerraBlender **4.1.0.8+**.
- Coolant is optional. Dry Tokamaks and Fusion Thrusters preserve their former performance.
- The new Alfvén high-altitude restriction is opt-in and defaults off.
- All six custom golems have independent, enabled-by-default content switches.

## Expandable Tokamak completed

- Completed the odd-edged Tokamak progression from 3x3 through the configured maximum.
- A valid reactor now requires a complete Tokamak-Coil perimeter around a solid Reactor-Core interior: one core at 3x3, nine at 5x5, twenty-five at 7x7, and so on.
- The deterministic center controller owns formation, fuel, FE, generation, output, and lit state. Follower cores and perimeter coils forward access to that shared state rather than operating independently.
- FE capacity, generation, output, and fuel duration scale from one authoritative formation snapshot.
- The GUI, Create goggles, Jade, WTHIT, and The One Probe now report ring dimensions, coil count, core count, multiplier, coolant, and explicit Invalid / Formed / Active state.
- Looking at an outer coil previews the exact configured ring and reports missing edges or an incomplete solid core.
- Updated Ponder to demonstrate a real 5x5 solid-core reactor instead of the old fixed eight-coil layout.
- Added fueled 5x5 regression coverage for formation, 3x scaling, synchronization, diagnostics, and controller discovery.

## Coolant loops

- Added optional coolant tanks to Tokamaks and Fusion Thrusters.
- Vanilla water is the baseline coolant, Deuterium Oxide is a modest upgrade, liquid Gallium is the high-efficiency liquid-metal option, and datapack fluids in `c:cooling_fluid` use a configurable tagged-fluid curve.
- Better coolant is consumed more slowly and provides stronger configured bonuses.
- Cooled Tokamaks generate and output more FE while extending fuel-cell life.
- Cooled Fusion Thrusters gain thrust and speed while consuming less FE and propellant.
- Shared coolant tanks accept buckets and Create pipes through Fusion interiors, Reactor Cores, and Tokamak-Coil frames.
- Added a separate `compat.tfmgCoolingFluidEnabled` switch so TFMG Cooling Fluid can be disabled without turning off the broader TFMG integration.

## Railgun projectile assembly and mounted operation

- Added optional Railgun projectile auto-assembly. A paired railgun can collect ordinary blocks staged between its rails into one centered Sable projectile ship.
- Added a configurable maximum assembled block count; oversized staged payloads are rejected whole instead of being truncated.
- Auto-assembly state is synchronized across both emitters and exposed in the GUI, Ponder, and automated playtest fixture.
- Fixed Railguns mounted on Sable ships targeting their own carrying craft. Mounted emitters now ignore the host during capture, hold, and launch, while staged projectiles remain valid targets.
- Mounted emitters now retain their Sable tick lifecycle so cooldown, capacity, and HUD synchronization continue after assembly.
- Added an end-to-end mounted-sublevel regression that assembles a railgun-bearing ship, creates a payload from its plot, launches that payload, and verifies the host is not accelerated.

## Magnetic golems

- Added Magnetite, Pyrrhotite, Hematite, and Titanomagnetite Golems.
- Each oxide golem has a distinct mineral silhouette, material state, magnetic behavior, repair item, drops, advancements, sounds, documentation, and Create/Jade/WTHIT/TOP readout.
- Replaced the Gallium Golem's vanilla silhouette with an intentionally slumped cast-metal body and exposed its live thermal and repair state across all four HUD surfaces.
- Added independent content switches for the Gallium, MR Fluid, Magnetite, Pyrrhotite, Hematite, and Titanomagnetite Golems. Disabled golems cannot be built or spawned; existing saved entities retain their data while custom material behavior becomes inert.
- Restored the MR Fluid Golem's complete material identity. The soft form uses a UV-correct 16-frame fluid surface on the same three-tick cadence as worn MR armor, while magnetic hardening uses the existing rigid texture.
- Soft MR Fluid Golems use normal knockback and configured 30% mitigation; hardened golems synchronize 92% mitigation and knockback immunity immediately.
- MR Fluid Buckets repair the golem, iron ingots no longer do, and its drops no longer fall back to vanilla iron nuggets.
- Added subtitled harden/soften sounds, particle cues, saved-golem migration, and live Create/Jade/WTHIT/TOP state.
- Restored magnetizable MR Fluid horse armor and metallic horse-armor recognition for G-Force Cushions.

## Gas Detector and propulsion feedback

- Completed the Gas Detector exposure and safe-distance behavior promised by 1.4.0.
- The expanded HUD and action-bar reading now show server-authoritative Radon dose and threshold, exposed/recovering/clear state, and estimated clearance from Radon cells or active Ion Thruster exhaust.
- Added client-side exhaust plumes for active Micro Thrusters, Fusion Thrusters, MHD Jets, and Ion Thrusters.
- Added a coolant-mist sheath around cooled Fusion Thruster exhaust.
- Added `visuals.thrusterExhaustEffectsEnabled`, a local client option that disables all exhaust particles without changing propulsion gameplay.
- Restored the Magnetosphere Solar Sail's persistent per-panel nighttime cutoff toggle and synchronized its selected state to machine overlays.
- Corrected Alfvén Ribbon Backpack guidance: by default it boosts daylight gliding at any altitude, with stronger performance higher up, and works anywhere in the End.
- Added opt-in `propulsion.alfvenHighAltitudeRequired` for packs that want to require daylight flight above Y=120 outside the End.

## Restored shipped behavior

- Restored Curios `charm`, `back`, and `hands` slots for the Field Compass, Magnetic Grapple, and Repulsor Gun.
- Curios regression coverage now validates the real player capability and exact item-to-slot assignments, and release-JAR verification requires the slot data.
- Fixed EMP Flux Charges leaving FE in receive-only machines. EMPs now clear the real backing buffer of all ten affected machine types without making that energy extractable to cables.
- Restored redstone-controlled MR Fluid hardening alongside magnetic-field activation. Conducted or adjacent power hardens the connected fluid, and removing power melts it back.
- Fixed hardened MR Fluid failing to revert after chunk reload.
- Synchronized the magnetic-emitter gameplay tag and progression with all fourteen block emitters.
- Corrected `full_kit` and `dual_magnetized` advancements so their simultaneous requirements use one inventory snapshot rather than accumulating across unrelated moments.

## Ponder, HUD, and visual polish

- Added all generated-style English Ponder titles and instructions for the eighteen registered scenes.
- Corrected Ponder's one-based instruction numbering and added a synchronization test that rejects missing, stale, reordered, or extra scene translations.
- Restored complete HUD parity for Gallium and all four iron-oxide golems.
- Added shared machine status for the Gas Exciter, Gyrostabilizer, Induction Pad, Barkhausen Generator, and Magnetostrictive Sensor.
- Dedicated entity models prevent Gallium and oxide golems from silently falling back to the vanilla Iron Golem silhouette.
- Fixed overlapping or incorrect 1.4.1 GUI/HUD states discovered during visual acceptance.

## Testing and release hardening

- Replaced mutable, automatically seeded screenshots with **51 committed checksum-verified visual baselines**.
- GUI captures now require a real Magnetization screen-open marker, and fixtures seed active fuel, coolant, and FE state before acceptance.
- Added isolated optional client smoke profiles for Jade, The One Probe, REI, EMI, and optional Ponder integrations.
- Hardened fresh GameTest setup, stale-process cleanup, minimal dedicated-server bootstrapping, and compatibility-matrix isolation.
- Added GitHub Actions build validation and removed stale mixin refmap declarations.
- Release verification now checks required metadata and Curios slot data while rejecting build-cache and operating-system file leakage.
- Expanded automated playtests for Tokamak scaling, Gas Detector HUD, golem states, machine screens, Railgun assembly, exhaust effects, and localized Ponder scenes.

Thank you to everyone who reported the incomplete 1.4.0 behavior and helped identify the ship-mounted Railgun regression before 1.4.1 shipped.
