# Magnetization 1.4.0: Excitable Noble Gases and Magnetic Plasma

This document is the implementation contract and forward roadmap for the 1.4.0
feature set. The first section records the 1.4.0 foundation already implemented
on the current branch. The later sections describe the recommended additions that
would make the gas system a complete Magnetization-specific progression rather
than another generic fluid-logistics system.

## Implemented 1.4.0 foundation

### Excitable gas system

- Ordinary Helium, Neon, Argon, Krypton, Xenon, and Radon are registered as
  bucketable, nonflammable gases.
- Helium and Neon rise and spread beneath ceilings. Argon, Krypton, Xenon, and
  Radon sink and spread across floors. Gas blocks have no collision and do not
  push entities, ships, blocks, or dropped items.
- Hydrogen, Helium-3, and the six ordinary noble gases share an `EXCITED` state.
  Dormant gas is nearly transparent; excited gas emits light level 15 and uses a
  gas-specific discharge color:

  | Gas | Discharge color |
  | --- | --- |
  | Hydrogen | `#FF6680` |
  | Helium | `#FFB38A` |
  | Neon | `#FF2A16` |
  | Argon | `#B56CFF` |
  | Krypton | `#D8FFE6` |
  | Xenon | `#4FA9FF` |
  | Radon | `#6657FF` |
  | Helium-3 | `#FFB38A` |

- Adjacent redstone or a powered Gas Exciter energizes a connected same-gas
  volume. Scanning is limited to loaded chunks, networks are capped at 4,096
  cells, and one eligible exciter owns the FE cost for a network each tick.
- Buckets never retain excitation state.
- Tritium does not require or accept excitation. Its radioactive decay produces
  steady cyan radioluminescence at light level 6.
- Radon immersion accumulates exposure. At the default threshold, harm begins
  after 600 ticks, deals damage at a configurable interval, and decays faster in
  clean air. A master switch disables both gas and exhaust exposure without
  disabling Radon as a fluid or propellant.

### Acquisition and processing

- Rare ceiling-bound Helium pockets and floor-bound Radon pockets generate in new
  Overworld cave chunks, each independently configurable. No retrogen is applied.
- The Create-powered Air Separator requires at least 64 RPM, scales through 256
  RPM, and produces Helium, Neon, Argon, Krypton, and Xenon into five independent
  8,000 mB tanks.
- The Air Separator stops only an individually full output. Its rear face is the
  Create shaft; the other five faces map one-to-one to gas tanks and expose
  drain-only capabilities.
- Sneak-use swaps a face assignment. Create fluid pipes connected to an assigned
  output face extract only that face's gas; the separator is intentionally not a
  bucket interface because it contains multiple gases.
- The Isotope Separation Module adds stress and speed-scaled work, producing the
  existing Helium-3 Crystal into a separate output slot without adding a sixth
  fluid tank.
- The Air Separator GUI, Create goggles, Jade, WTHIT, and The One Probe expose
  tank levels, rates, RPM/status, port assignments, and isotope progress.

### Ion Thruster

- The single-block Ion Thruster is a ship-only electric Sable-ship drive with an
  8,000 mB input tank, 400,000 FE buffer, and 8,000 FE/t receive rate.
- It applies thrust opposite its exhaust-facing side using the established Sable
  ship transform and speed-cap path.
- Built-in propellant profiles are:

  | Propellant | Thrust | Max speed | Fluid/t | FE/t |
  | --- | ---: | ---: | ---: | ---: |
  | Helium | 0.55x | 1.40x | 2 mB | 100 |
  | Neon | 0.80x | 1.25x | 2 mB | 90 |
  | Argon | 1.00x | 1.00x | 3 mB | 80 |
  | Krypton | 1.30x | 1.20x | 1 mB | 110 |
  | Xenon | 1.70x | 1.30x | 1 mB | 130 |
  | Radon | 1.90x | 1.15x | 1 mB | 150 |

- Radon exhaust adds exposure within the configured radius while firing.
- `magnetization:ion_thruster_propellants` is the datapack compatibility hook.
  External tagged fluids receive the neutral fallback profile.
- Current propellant, tank state, FE, and active status are synchronized to menus
  and HUD integrations.

### Release and compatibility coverage already present

- Registrations, blocks, block entities, fluids, effects, buckets, loot, recipes,
  creative entries, models, textures, localization, config values, tags,
  worldgen, and biome modifiers are present for the foundation above.
- Focused GameTests cover gas direction and collision behavior, excitation
  networks, FE ownership, excitation persistence during flow, separator output
  isolation and synchronization, Ion Thruster profiles, and Radon exposure.
- Resource tests cover noble-gas models, textures, translations, tags, machines,
  recipes, worldgen, and Field Manual entries.
- Existing Hydrogen, Tritium, Helium-3, and all 1.3.x IDs and processing recipes
  remain stable.

## Recommended 1.4.0 expansion roadmap

The next additions should deepen the systems already present. They should not
duplicate Create's generic tanks, pipes, or logistics, and they should not reopen
the completed optional-mod compatibility roadmap.

### Priority 1: Magnetic plasma processing

Add a Magnetization-specific Plasma Processor or magnetic-confinement chamber.

- Feed it an excitable gas, FE, and a required magnetic-field state.
- Convert the gas into a controlled plasma process rather than adding another
  ordinary fluid tier.
- Make gas choice affect ignition cost, processing time, plasma stability, and
  output quality.
- Reuse the Gas Exciter, magnetic-field APIs, gas tags, and Air Separator outputs.
- Include configurable field requirements, FE cost, heat/waste, network size, and
  failure/rollback behavior.

### Priority 2: MHD power recovery

Add a directional plasma/MHD generator that turns controlled gas motion or plasma
flow back into FE.

- Output should depend on gas identity, excitation/plasma state, flow rate, field
  strength, and field orientation.
- Require meaningful magnetic geometry so the feature uses Magnetization's field
  simulation instead of behaving like a generic generator.
- Keep the Tokamak as the high-end fusion generator; the MHD route should trade
  fuel availability and lower peak output for a renewable atmospheric-gas loop.
- Add server-authoritative energy output, automation rules, config controls, and
  focused GameTests for orientation, empty input, overload, and persistence.

### Priority 3: Gas safety and containment

Add tools and blocks for understanding and controlling the existing gas hazards.

- Add a Radon dosimeter or gas detector that reports nearby gas identity, density,
  excitation state, exposure, and safe distance.
- Add a Magnetization-specific containment or ventilation field that limits gas
  spread, safely vents a selected gas, and provides a controlled Radon-handling
  route.
- Make containment interact with loaded-chunk boundaries, flowing gas, powered
  state, and block destruction.
- Expose warnings through item tooltips, HUD/probe integrations, particles, and
  readable failure messages.

### Priority 4: Advanced Ion Thruster controls

Deepen the current propellant-specific propulsion instead of adding another
generic engine.

- Add server-authoritative throttle and thrust-vector/nozzle control.
- Add configurable nozzle modes or propulsion upgrades that trade thrust,
  efficiency, maximum speed, and Radon exposure.
- Show live thrust, propellant, FE draw, throttle, exhaust direction, and exposure
  in the menu and existing HUD/probe integrations.
- Preserve the six built-in profiles and the tagged-fluid neutral fallback.

### Priority 5: Magnetic isotope progression

Extend the existing Isotope Separation Module into a staged late-game processing
path without invalidating the current fusion ladder.

- Use Air Separator fractions as inputs to optional isotope enrichment steps.
- Add configurable work, stress, FE, yield, and waste values.
- Preserve Water → Hydrogen → Deuterium → Tritium → Helium-3 as the reliable
  baseline progression; new processing should be an alternative or enhancement.
- Add persistence, automation, recipe-viewer, Field Manual, and GameTest coverage
  for partial work, interrupted processing, full output, and reload behavior.

### Priority 6: Release closure and presentation

- Add survival tests for the complete atmospheric-gas-to-propulsion loop.
- Add visual checks for dormant transparency, plasma brightness, gas sorting,
  Radon warnings, containment behavior, and Ion Thruster exhaust.
- Finish README, configuration, changelog, recipe-viewer, and Field Manual
  coverage for each new block, item, tag, config, and failure mode.
- Add client playtest cases for Sodium/Iris-style rendering conditions, separator
  port readability, gas lighting, and ship propulsion feel.

## Explicit non-goals for 1.4.0

- Do not add another generic gas-pipe, gas-tank, or Create-style storage system
  unless a later plasma or safety design demonstrates that it is necessary.
- Do not add more noble-gas types; the current six-gas set is sufficient.
- Do not reopen broad optional-mod compatibility work in this feature contract;
  that work remains tracked separately in `ROADMAP.md`.
- Do not replace existing fusion recipes, propellant profiles, stable IDs, or
  server-authoritative synchronization behavior.

## Acceptance gates for the expanded roadmap

- Every new machine or item has registrations, recipes, loot, models, textures,
  translations, creative-tab placement, config documentation, and Field Manual
  coverage.
- Every new gas or plasma state has explicit tags, persistence behavior, chunk and
  unloaded-area behavior, automation rules, and client/server synchronization.
- Every new energy or propulsion mechanic has tests for orientation, caps, empty
  input, invalid input, overload, reload, and configuration changes.
- Every hazard has a controllable master switch, readable player feedback, and
  tests proving that disabling the hazard does not remove the underlying gas or
  machine content.
- The existing unit tests, resource tests, GameTests, release gates, and manual
  playtest profiles remain passing before the 1.4.0 proposal is considered
  complete.
