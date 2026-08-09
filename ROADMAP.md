# Compatibility roadmap

This document is the repository-owned specification for the current optional-mod compatibility pass. Each substantial integration requires focused present-mod GameTests, absent-mod coverage, runtime controls for behavior hooks, and actual magnetic fields where applicable.

## Priority 1: Create New Age

Create New Age is the closest progression match because its technology is explicitly magnetic.

- Make CNA magnet blocks emit actual Magnetization fields.
- Map CNA's native magnet strength to field strength and machine-magnet potency.
- Add CNA magnets to `machine_magnets`, not merely `magnetic_emitter`.
- Support polarity-aware redstone, layered, fluxuated, and netherite magnets.
- Add alternate motor and generator-coil recipes using Permanent Magnets.
- Add Energiser recipes for producing Permanent Magnets or magnetizing compatible items.
- Expand eddy-conductor coverage to motors, connectors, the Energiser, and electrical machinery.
- Add runtime configs and dedicated present/absent GameTest profiles.

## Priority 2: Immersive Engineering

Immersive Engineering provides the broadest industrial progression bridge.

- Powered IE Electromagnets emit configurable real fields.
- Tesla Coils emit pulsed fields and their damage counts as a lightning source for LIRM.
- Add an IE Mixer route from raw Magnetite or Hematite plus `c:plantoil` to Ferrofluid.
- Add an IE Metal Press route from ferromagnetic ingots to Magnetic Plates.
- Make `immersiveengineering:railgun_shot` react to magnetic fields.
- Investigate Magnetization rods and ingots as IE Railgun ammunition.
- Add appropriate IE machines, coils, capacitors, connectors, and wires to eddy-conductor roles.
- Add a declared optional dependency and isolated compatibility tests.

The Railgun-ammunition investigation intentionally stops short of registration. IE exposes a global, non-reloadable projectile list, which cannot cleanly honor this integration's live configuration controls and would impose an intrusive balance change. Railgun Shot field reaction remains supported.

## Priority 3: Alex's Caves

Alex's Caves is a strong semantic match because of its magnetic-biome machinery.

- Convert Scarlet and Azure Magnets into polarity-correct field emitters.
- Register Alex's Caves magnets and Neodymium as machine magnets with appropriate potency.
- Add shared recipes between Neodymium, Permanent Magnets, and magnetic components.
- Add a Ferrofluid route involving Ferrouslime or Neodymium.
- Integrate magnetic roles with levitation rails, magnetic quarries, and movable magnetic blocks.
- Suppress duplicate forces where both mods apply magnetic movement.
- Add configuration controls and dedicated tests.

## Priority 4: Create Crafts & Additions

- Powered Tesla Coils emit configurable fields based on charge/FE state.
- Add `createaddition:tesla_coil` damage to the lightning-source tag.
- Add Permanent Magnet alternatives for Electric Motor and Alternator recipes.
- Add the Modular Accumulator, capacitor, connectors, motor, and alternator to appropriate eddy-conductor roles.
- Support the generic plant-oil Ferrofluid recipe through common fluid tags.

## Secondary integrations

### Create Big Cannons

- Add missing cast-iron, steel, and Nethersteel components to magnetic and eddy roles.
- Make compatible solid shot and autocannon projectiles respond consistently to fields.
- Long-term: allow the Magnetization Railgun to launch selected CBC ammunition.
- Avoid applying magnetic forces to cannon carriages or unrelated explosive entities.

### Create Diesel Generators

- Reuse one common `c:plantoil` Ferrofluid recipe instead of adding mod-specific duplicates.
- Let a Chemical Sprayer or Chemical Turret loaded with Ferrofluid apply Magnetized or a magnetic impulse.
- Add metal engine and turret components to eddy-conductor coverage while keeping tanks and fluid machinery excluded.
- Do not make the Micro Thruster burn diesel; its Ferrofluid identity remains distinct.

### Create Ender Transmission

- Test transmission of Ferrofluid, fusion gases, and data-component-bearing magnetic items.
- Verify that magnetized polarity and Imprint Module data survive item transmission.
- Provide an experimental, disabled-by-default field relay through linked Energy Transmitters.
- Keep remote field projection off by default because it is intrusive and version-sensitive.

## Existing integrations already deep enough

These primarily need regression coverage rather than new progression systems:

- Steam 'n' Rails
- Create: Interactive/Tracks
- Simulated Coasters
- Copycats
- AeroPortals
- Immersive Aeronautics

Create Enchantment Industry remains mostly tag-based. Printing magnetized items or Imprint Modules introduces component-preservation and duplication concerns without a natural progression bridge.

## Shared implementation foundation

- Use external-emitter adapters that derive polarity and strength from another mod's live block or block entity.
- Expand `machine_magnets` with native strength mappings.
- Add the `c:plantoil` Ferrofluid recipe once, with conditions preventing duplicates.
- Keep behavior hooks configurable; ordinary material-tag parity does not require toggles.
- Give each substantial integration a present profile, absent-mod profile, and focused GameTests.
- Ensure external magnet blocks produce actual fields; `magnetic_emitter` alone only affects ship susceptibility.
- Give every optional integration a master config switch so server and modpack owners can tune it.
