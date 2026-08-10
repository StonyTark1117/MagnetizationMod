# Magnetization 1.4.0: Excitable Noble Gases

This document is both the approved feature specification and the implementation checklist for the 1.4.0 major feature set. Existing Helium-3 remains a separate fusion fuel with stable IDs and recipes.

## Gas system

- Add ordinary Helium, Neon, Argon, Krypton, Xenon, and Radon as bucketable, nonflammable gases.
- Helium and Neon rise and spread beneath ceilings. Argon, Krypton, Xenon, and Radon sink and spread across floors. Gas blocks have no collision.
- Add a shared `EXCITED` state. Dormant gas is nearly transparent and dark; excited gas emits light level 15 and uses a distinct discharge color:
  - Helium `#FFB38A`
  - Neon `#FF2A16`
  - Argon `#B56CFF`
  - Krypton `#D8FFE6`
  - Xenon `#4FA9FF`
  - Radon `#6657FF`
- Adjacent redstone or a powered Gas Exciter energizes a connected same-gas volume. Scan only loaded chunks, cap a network at 4,096 cells, and elect the lowest-position eligible exciter to pay FE once per tick.
- Buckets never retain excitation.
- Radon immersion accumulates exposure. At defaults, harm begins after 600 ticks, deals 1 damage per 100 ticks, and decays twice as fast in clean air. One config switch disables both gas and exhaust exposure without disabling Radon as fuel.

## Acquisition and machinery

- Add rare ceiling-bound Helium pockets and floor-bound Radon pockets in new Overworld cave chunks, each independently configurable.
- Add the Create-powered Air Separator. It requires at least 64 RPM, scales through 256 RPM, produces Helium/Neon/Argon/Krypton/Xenon simultaneously into five independent 8,000 mB tanks, and stops only an individually full output.
- The rear face is the Create shaft. The other five faces map one-to-one to gas tanks and expose drain-only capabilities. Sneak-use a face to swap its assignment; use a bucket to collect that face's gas.
- Add an Isotope Separation Module. Installing it adds stress and accumulates speed-scaled work, producing the existing Helium-3 Crystal into a separate output slot without adding a sixth fluid tank.

## Ion Thruster

- Add a single-block, ship-only Ion Thruster with an 8,000 mB input tank, 400,000 FE buffer, and 8,000 FE/t receive rate.
- It applies thrust opposite its exhaust-facing side using the established Sable ship transform and speed-cap path.
- Built-in profiles:

| Propellant | Thrust | Max speed | Fluid/t | FE/t |
| --- | ---: | ---: | ---: | ---: |
| Helium | 0.55x | 1.40x | 2 mB | 100 |
| Neon | 0.80x | 1.25x | 2 mB | 90 |
| Argon | 1.00x | 1.00x | 3 mB | 80 |
| Krypton | 1.30x | 1.20x | 1 mB | 110 |
| Xenon | 1.70x | 1.30x | 1 mB | 130 |
| Radon | 1.90x | 1.15x | 1 mB | 150 |

- Radon exhaust adds exposure within four blocks while firing.
- `magnetization:ion_thruster_propellants` is the datapack compatibility hook. External tagged fluids receive the neutral baseline profile.
- The server synchronizes current propellant, tank state, FE, and active status to menus and HUD integrations.

## Content and release requirements

- Register blocks, block entities, fluids, effects, buckets, loot, recipes, creative entries, models, textures, localization, config values, fluid tags, worldgen features, and biome modifiers.
- Document controls and balance values in the configuration guide, README feature summary, recipe-viewer descriptions, and Field Manual.
- Automate validation for directionality, excitation and FE ownership, separator output isolation and persistence, isotope upgrade behavior, all Ion Thruster profiles, unsupported propellant rejection, radon enable/disable behavior, registrations, tags, models, loot, recipes, and JSON validity.
- Manually playtest translucent sorting, shaders/Sodium/Iris, gas lighting, separator port readability, particle color, audio, ship propulsion feel, and worldgen rarity before release.
- Gases should not affect the movement of entities/blocks/dropped items, entities/ships/objects should pass through them like normal air

## Compatibility guarantees

- No storage blocks, tools, armor, cells, or solid forms are added for the six ordinary noble gases.
- Existing Hydrogen, Tritium, Helium-3, and all 1.3.x IDs and processing recipes remain stable. The three existing placed gases adopt the same no-push/no-slow entity behavior as the new gas system.
- New worldgen affects newly generated chunks only; no retrogen is performed.
