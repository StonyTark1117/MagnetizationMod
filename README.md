# Magnetization

A NeoForge 1.21.1 addon for **[Create: Aeronautics](https://modrinth.com/mod/create-aeronautics)** that adds magnetic forces, anchors, and propulsion for Sable-driven contraptions.

## Requirements

- Minecraft **1.21.1**
- NeoForge **21.1.247+**
- [Create](https://modrinth.com/mod/create) **6.0.11+**
- [Sable](https://modrinth.com/mod/sable) **2.0.3+**
- [Create: Aeronautics](https://modrinth.com/mod/create-aeronautics) **1.3.0+**
- Simulated **1.3.0+**
- [TerraBlender](https://modrinth.com/mod/terrablender) **4.1.0.8+**

Optional integrations (auto-detected when installed):
- [Jade](https://modrinth.com/mod/jade), [WTHIT](https://modrinth.com/mod/wthit), or [The One Probe](https://modrinth.com/mod/the-one-probe) for HUD info on emitters.
- [JEI](https://modrinth.com/mod/jei), [REI](https://modrinth.com/mod/rei), or [EMI](https://modrinth.com/mod/emi) for an in-browser info page on the ferromagnetic-item tag.
- [Curios](https://modrinth.com/mod/curios) — adds three slots to the player: **charm** (Field Compass), **back** (Magnetic Grapple), **hands** (Repulsor Gun). The compass is purely passive — its needle and HUD overlay both read the slot directly. The grapple and gun are active and fire via configurable keybinds under *Options → Controls → Key Binds → Magnetization*. Default keys are unbound so they never collide with vanilla on first launch.
- [Patchouli](https://modrinth.com/mod/patchouli) — adds a craftable in-game guide book (Book + Raw Magnetite) covering basics, emitters, ship polarity, and advanced systems.
- [Alex's Caves](https://modrinth.com/mod/alexs-caves) — Magnetron + Ferrouslime are magnetizable; Azure/Scarlet magnets + neodymium ores/blocks are ferromagnetic. Config `compat.alexsCavesPotionMode` (BOTH / OURS_ONLY / THEIRS_ONLY) controls how our Magnetized effect coexists with AC's Magnetizing effect when both mods are present.
- [Create: Coasters Simulated](https://www.curseforge.com/minecraft/mc-mods/create-coasters-simulated) — coaster cars react to magnetic fields like other Sable craft. The Structural Inducer recognizes rail-engaged coaster trains (including linked cars and riveted bodies) as one existing structure, while loose carts are ignored. The two behaviors have independent toggles under `compat.simulatedCoastersFieldReaction` and `compat.simulatedCoastersStructuralInducer`.
- [Create: Big Cannons](https://www.curseforge.com/minecraft/mc-mods/create-big-cannons) — launched big-cannon shells, autocannon rounds, and their projectile bursts react to magnetic fields. Cannon carriages, assembled cannon blocks, gas clouds, and primed propellant are intentionally excluded. The behavior is optional and independently controlled by `compat.createBigCannonsProjectileReaction` and `compat.createBigCannonsProjectileSusceptibility`.
- [Create: New Age](https://www.curseforge.com/minecraft/mc-mods/create-new-age) — its current magnet family contributes to a Sable craft's magnetic susceptibility, while Generator Coils and wire blocks participate in ferromagnetic/conductive multiblock interactions. Compatibility is verified against the published 1.2.0 NeoForge runtime.
- [Magnetizing](https://www.curseforge.com/minecraft/mc-mods/magnetizing) — its magnetite ingots are fungible with ours via `c:ingots/magnetite`; its block/item magnets and colored magnetite blocks count as ferromagnetic to our emitters; we honour its `magnetizing:unmoveable_by_magnets` entity tag so admins only need to curate one list.
- [Create: Magnetics](https://www.curseforge.com/minecraft/mc-mods/create-magnetics) — ingot/sheet/block all fungible via the `c:` tags; its Kinetic Magnet counts as a magnet for ship susceptibility; magnetized crystals are ferromagnetic items.
- **Simulated** — its Redstone Magnet block is recognized as a magnet emitter, so any contraption carrying one gains susceptibility from it and naturally responds to our fields.
- **Broader metal coverage** — common `c:` metal tags (steel, nickel, cobalt, zinc, brass, tin, lead, silver, osmium, uranium, aluminum, neodymium, electrum, invar, constantan, bronze) are all included in our ferromagnetic tags, so any tech/Create addon that populates them gets free integration: those ores/ingots/blocks act as ferromagnetic targets and count toward ship susceptibility when carried. Verified hits: Mekanism (osmium/lead/tin/uranium/refined alloys), Create: Crafts & Additions (electrum, wires, rods), Create: New Age (its authoritative magnet tag, Generator Coil, and conductive wire blocks), AlexsCaves (azure/scarlet neodymium ores/ingots/blocks + their magnets), Twilight Forest (knightmetal, steeleaf, ironwood, fiery armor sets).
- **AlexsCaves** — Magnetron mobs (literally made of metal) and Ferrouslime are in our `magnetizable` entity tag. Their armor sets are recognized as magnetizable gear.
- **Twilight Forest** — knightmetal/steeleaf/ironwood/fiery armor sets are in `metal_armor` and feed our magnetized-armor system.
- **Iron Chests / Sophisticated Storage** — metal chest/barrel variants act as ferromagnetic blocks.
- **Immersive Engineering** — steel/aluminum/electrum/constantan ingots + iron/steel plates + copper/electrum/steel wirecoils + railgun are tagged ferromagnetic.
- **Modular Golems / Extra Golems / Cataclysm / Bosses of Mass Destruction** — metallic golem entities and metal-themed bosses (Ignis, Netherite Monstrosity, Gauntlet) are magnetizable.
- **The Aether** — Gravitite item and block tagged ferromagnetic (their "anti-gravity" stays — our pull is just additive).
- **Quark** — Iron and Copper Oretoises are magnetizable.
- **Cross-mod lightning** — Iron's Spells (Chain Lightning, Lightning Lance, Thunderstorm, Ascension), Cataclysm Scylla (Lightning Spear / Electric Shock), Alex's Caves Tesla + Magnetron arcs, IE Tesla Coil, Twilight Forest lightning all trigger LIRM stamping and log petrification on hit — same effect as a vanilla bolt. Driven by the `#magnetization:lightning_sources` damage-type tag; datapacks can add more sources without code changes.
- **FE/RF power** — the 5 redstone-powered emitters expose an `IEnergyStorage` capability. Any FE-providing mod (Create: C&A, Mekanism, Thermal, IE generators, AE2, etc.) can drive them. Internal 50 000 FE buffer, 10 FE/tick drain, 200 FE/tick max input. Admin config `compat.allowRedstonePower` / `compat.allowEnergyPower` toggle which sources are valid — set redstone false to force players to use FE/RF as a non-trivial power source.
- **Analog redstone throttling** — off by default. Enable `compat.analogRedstone*` for a block and its field force follows the redstone *level* instead of being on/off: signal 1 = 200 N (Weak's force), signal 15 = 8000 N (Extreme's), ramped geometrically. See [Configuration → compat](#compat--power-sources).

## New in 1.3.0 — Fusion, Railgun & the fuel overhaul

- **Fusion Thruster** — an expandable flat-panel multiblock thruster for airships: a `TOKAMAK_COIL` perimeter around a fill of fusion cells. A bigger panel means exponentially more thrust; it burns the fusion fluids below (Helium-3 is the strongest and longest-running).
- **Railgun** — a paired-rail accelerator: two parallel powered rails form an arc that grabs a ship or magnetic entity and launches it down the channel with force that grows exponentially with rail length, smashing obstructing blocks. Runs automatically, or pair the **Railgun Remote** for a manual hold-then-fire workflow (trap a ship on the rail, board it, then fire the remote from hand).
- **Electrolyzer** — a cauldron-style powered machine that splits water + FE into **Hydrogen**, the entry point of the fusion-fuel ladder: **Water → Hydrogen → Deuterium → (+ Lithium) Tritium → Helium-3**. Tritium is bred from Deuterium + Lithium; Helium-3 is enriched or found in rare deepslate/End geodes.
- **New worldgen** — a minor **Lithium ore** (overworld + deepslate) and a rare **Helium-3 geode**.
- **Machine rebalance** — the Tokamak now burns three fuel-cell tiers; the MHD Jet needs a conductive working fluid (gallium/mixed gallium/liquid lithium); the Micro-Thruster accepts magnetized ferrofluid; and magnet-slot machines now consume their magnet over a strength-scaled burn time (toggleable). Every fuel-burning machine accepts hopper/pipe fuel intake.

## How it works

Every block in this addon either *emits a magnetic field* or *responds to one*. Fields have a polarity (NORTH or SOUTH), a strength tier (WEAK→EXTREME), and a shape (omnidirectional, directional, conical). Like polarities repel, opposite polarities attract. Forces are applied to:

- **Sable sub-levels** (Create: Aeronautics ships) intersecting the field's range — pushes/pulls the contraption.
- **Magnetizable entities** within the field — iron golems, ferromagnetic item drops, and any mob or player wearing metal armor. (Ordinary undead are *not* intrinsically pulled; equip them with metal armor to make them susceptible.)

Onboard-only configurations (every magnet on the same ship) produce zero net thrust — internal forces cancel, just like real physics. To propel a ship, you build *between* the world and the ship.

External fields integrate over the ship's volume: a coarse 3×3×3 sample grid (configurable) means a long ship feels stronger pull at the end closest to a magnet than the far end, which naturally produces torque so the ship rotates into alignment instead of just sliding. Off-center impulses also feed back as angular velocity via the contraption's true inverse inertia tensor, so a tractor beam clipping the nose yaws the ship around its center of mass. A small per-tick linear drag (default 2%) is applied to any ship being pulled by a magnet so constant-force tugs reach a terminal velocity instead of accelerating forever, and the per-tick acceleration cap is now shared across every emitter touching the same ship — three STRONG anchors stacked can't stack 3× the cap.

### Ship polarity & susceptibility

Every Sable sub-level carries a derived magnetic state, scanned periodically from its blocks:

- **Polarity** — NORTH by default. Place a **Polarity Inverter** anywhere on the contraption to flip the ship to SOUTH; place a second to cancel back to NORTH (parity). Inverters never affect adjacent emitters' polarity when used this way; they're whole-ship modifiers.
- **Susceptibility** — `baseline + 0.05 × ferrous_blocks + 0.15 × magnet_blocks`, capped (default 20×). A heavier ferromagnetic loadout makes the ship visibly more responsive to external fields. Magnet blocks aboard count toward susceptibility only — their own pole has no effect on the ship's pole, so flipping an Electromagnet's polarity for ship-to-ship combat doesn't accidentally invert your own ship and tear the contraption apart.

Magnets mounted on a ship still never pull their own ship directly (the carrying sub-level is excluded from each emitter's force pass). But a static magnet on the ground will pull a ship with onboard magnets normally — composition affects susceptibility, presence doesn't gate it.

## Block reference

| Block | Power | Field shape | Use |
|-------|-------|-------------|-----|
| **Electromagnet** | redstone | omnidirectional, MEDIUM | Generic pull. Nice for item collectors and "tractor docking" stations. |
| **Kinetic Electromagnet** | Create rotation | omnidirectional, scales with RPM | Variable strength via gearbox/clutch; fits Create-pack progression. |
| **Magnetic Anchor** | redstone | omnidirectional, STRONG | Locks a ship in place. The first ship in range becomes the bound target; binding persists across reload. |
| **Repulsor Coil** | redstone | conical (placement-aligned), MEDIUM | Pushes ships in the direction the coil faces. Place upward for a hover pad; place sideways/downward to line a tunnel and shove ships through it. |
| **Tractor Beam Emitter** | redstone | directional, STRONG | Pulls ships in front of it toward the emitter. Wrench-rotatable. |
| **Magnetic Excavator** | redstone *or* internal redstone slot | continuous cone scan, default STRONG | Continuously projects a widening cone along its facing and pulls every ferromagnetic block in range — each as its own Sable sub-level that tunnels through obstructions toward the emitter, drops on arrival, and feeds adjacent inventories. GUI has strength/range knobs, an in-flight cap slider, an enchanted-tool slot (Fortune/Silk Touch), and an internal redstone-fuel slot so an external redstone signal isn't required. Wrench-rotatable. |
| **Permanent Magnet** | none (always-on) | omnidirectional, WEAK | Right-click to flip polarity. Pairs of opposing-polarity permanent magnets between world and ship build static propulsion tracks. |
| **Polarity Inverter** | passive | — | Adjacent emitters get their field polarity flipped. Two inverters cancel. |
| **Magnetic Switch** | passive | — | Emits redstone signal proportional to nearest ship's distance (8-block range). Pair with anchor + comparator for auto-docking. |
| **Lodestone Core** | — | — | Crafting component for every emitter. |

## Items

- **Ferromagnetic Ingot** — `8 iron + 1 lodestone → 8 ingots`. Base material for everything else.
- **Magnetic Plate** — `3 ferromagnetic ingot → 3 plates`. Cladding for emitter recipes.
- **Field Compass** — `4 plates + 1 vanilla compass → 1 compass`. Right-click for the strongest active field nearby; sneak right-click lists every active field in range.
- **Magnetic Grapple** — `2 plates + 1 lodestone core + 1 string → 1 grapple`. Right-click an attractive field source within 24 blocks to be yanked toward it.
- **Repulsor Gun** — `1 plate + 1 lodestone core + 1 ferromagnetic ingot + 3 iron ingots → 1 gun`. Inverse of the Grapple. Right-click fires a one-tick conical pulse along your look direction: ships in the cone get pushed (impulse divided by ship mass, so small ships fly farther), magnetized entities get knockback, dropped items + falling blocks get nudged out. If the shot lands on a magnetic emitter block you get self-recoiled along the inverse of your aim — closer = harder kickback, opening up traversal tricks ("aim down at a lodestone to launch backward"). Cooldown 1 s, no fuel.
- **Magnetite Ore / Deepslate Magnetite Ore** — naturally occurring Fe₃O₄. Drops raw magnetite (Fortune ore_drops formula). Stone-tier pickaxe.
- **Raw Magnetite / Magnetite Ingot** — smelt or blast 1:1. Both are in `#magnetization:ferromagnetic` so emitters yank them straight off the ground.
- **Block of Magnetite / Block of Raw Magnetite** — 9× compact storage. Iron-tier for the smelted block, stone-tier for the raw block. Both also ferromagnetic in bulk.

## Propulsion track example

Line a tunnel with Repulsor Coils all facing the same horizontal direction:

```
[ coil → ][ coil → ][ coil → ][ coil → ]
```

Power the coils with redstone; any Sable contraption that enters the tunnel gets pushed along the cone of each coil it overlaps, accumulating thrust through the run.

For *static* propulsion using only Permanent Magnets:

```
World floor:     [ N ][ N ][ N ][ N ]
Ship underside:  [ N ][ N ][ N ][ N ]    ← like-pole repulsion, ship hovers
```

Or for forward push:

```
World wall:      [ N ][ N ][ N ]
Ship rear:       [ N ][ N ][ N ]    ← repels ship away from wall
```

## Player interactions

- **Iron / chainmail / gold / netherite / magnetite / ferromagnetic armor** makes you magnetizable. Each piece worn adds susceptibility — full plate set is yanked hard by anchors. Mobs wearing tagged armor are pulled the same way.
- **Magnetic Grapple** turns infrastructure into traversal. Right-click pointing at any space within 24 blocks of an active attractive field; the closest qualifying emitter pulls you toward it. Cooldown: 1 second.
- **`/magnetization help`** lists every subcommand available to the caller's permission level with a one-line description.
- **`/magnetization version`** prints mod + Create + Sable + Aeronautics + NeoForge versions. Handy for bug reports.
- **`/magnetization stats`** counts loaded emitters, live Sable ships, and entities carrying the Magnetized effect in the player's level.
- **`/magnetization config show [filter]`** dumps live `MagConfig` values to chat. Optional substring filter (e.g. `show energy` for the FE-related knobs).
- **`/magnetization debug field <pos>`** prints the field state at an emitter.
- **`/magnetization debug forceAt <emitter> <target>`** prints the world-space force vector the emitter would exert on a unit-mass test particle at `target`.
- **`/magnetization debug rotate <deg> [yaw|pitch|roll]`** teleport-rotates the nearest sub-level by an absolute angle in world frame — setup-free way to confirm a mounted emitter's field axis follows the contraption.
- **`/magnetization debug energy <pos> [fill|drain|set <n>]`** reads or writes an emitter's FE buffer directly — skips needing a cabled-up FE source to validate the energy path.
- **`/magnetization debug curios`** lists every item in the player's Curios slots; confirms slot membership when validating HUD/keybind behaviour.
- **`/magnetization spawn_test_ship [size] [material]`** drops an N×N×N test ship in the air 4 blocks ahead. Size defaults to 1, material defaults to magnetite (also accepts iron / raw_iron / gold / copper).
- **`/magnetization spawn_test_anchor`** drops a powered Magnetic Anchor 4 blocks ahead.
- **`/magnetization lirm strike [pos]`** summons a lightning bolt on the player (or at `pos`) so LIRM + log petrification fire on demand. `/magnetization lirm stamp [north|south]` manually LIRM-stamps the held metal item (testing decay without waiting on a storm). `/magnetization lirm inspect` lists every metal armor/tool the player carries with current polarity + decay remaining. `/magnetization lirm clear` strips the LIRM stamp from the held item. `/magnetization lirm fields` prints the number of active transient magnetic fields seeded by recent lightning in the current level.
- **`/magnetization tp anomaly [player]`** and **`/magnetization tp petrified_forest [player]`** scan up to 6 400 blocks for the closest matching biome and teleport the player (or a named target) to its surface — same scan radius vanilla `/locate biome` uses.

## Magnetizing armor & tools

Right-click the **Electromagnet** or **Kinetic Electromagnet** with an empty hand to open its GUI. Insert a piece of metal armor (anything in `#magnetization:metal_armor`) or a metal tool (anything in `#magnetization:metal_tools`) into the slot, then click **N** / **S** / **Clear** to stamp the polarity:

- **Magnetized armor**: while worn, gives the player +0.6 susceptibility per piece (on top of the base +0.4 for tagged metal armor) and overrides the wearer's effective polarity. A player in magnetized-NORTH plate is repelled by NORTH emitters and pulled hard by SOUTH ones. Magnetized armor also acts as a personal item-vacuum: nearby dropped ferromagnetic items drift toward the player (2-block radius per magnetized piece, so a full set reaches 8 blocks). Net polarity decides direction — SOUTH-net attracts, NORTH-net repels (useful for sweeping junk away while mining). A full magnetized set widens the filter from "ferromagnetic only" to every dropped item.
- **Magnetized tools**: each tool type has a signature ability once stamped — sword-yank, pickaxe ore-rip while sneaking, axe pulse on log chop, shovel metal-pan trace drops, hoe dowsing-ping. See `tools.*` config keys to toggle individual abilities.
- **Combat synergy**: hitting any LivingEntity with a magnetized weapon stamps the target with the **Magnetized** mob effect for 3 seconds. Opposite-pole weapon-on-armor hit upgrades to amp 2 — the target is *pinned* (60% horizontal velocity damp per tick) under the magnetic field, simulating iron grabbing iron.

## Magnetic Excavator

A redstone-powered (or internally-powered) ferromagnetic mining block. Right-click (empty hand) for its GUI: strength tier (W/M/S/E, default STRONG), range slider (default = half the admin ceiling), in-flight cap slider (how many concurrent pulls the emitter allows), an enchanted-tool slot, and an internal redstone-fuel slot. Wrench-rotate to change the active face direction; defaults to FACING=DOWN (place against a ceiling, mines the column below).

The field model is **continuous, not per-cycle**:

1. While powered, the Excavator continuously projects a widening cone along its facing (~14° half-angle, ≥5×5 disc even at shallow depth). Every few ticks it rescans the cone for ferromagnetic blocks in `#magnetization:ferromagnetic_blocks` (covers `c:ores/iron|gold|copper|netherite_scrap`, `c:storage_blocks/*` for those metals, plus addon magnetite blocks and selected Create intermediates).
2. Each newly-found cell gets assembled as its **own** Sable sub-level — not one big column — and added to the in-flight pool. The per-emitter "Pulls" slider caps how many can be in flight simultaneously (default 16, admin ceiling 64).
3. Each in-flight ship gets a radial impulse toward the emitter every tick, and *tunnels* through obstructions: the world cells on its toward-emitter faces are destroyed (drops pop in place) so the ore can drill through dirt/stone toward the magnet. Multiple ores from the same vein each carve their own tunnel.
4. On arrival at the emitter, the ship dismantles and its loot drops at the emitter cell — directly into adjacent inventories first (hopper / chest / barrel via IItemHandler), falling back to a dropped ItemEntity for the entity-pass to pull into the same destination.

The **enchanted-tool slot** at (132, 20) threads its enchantments through `LootContextParams.TOOL` for every ship's drop resolution — Fortune III multiplies ore drops, Silk Touch silk-mines, etc. Damageable tools take 1 durability per scan that pulls new blocks; enchanted books are immune. The **redstone-fuel slot** at (28, 20) accepts any item in `#magnetization:redstone_fuel` (dust, redstone block, torch, lever, observer, daylight detector, target, every vanilla button/pressure-plate variant). Items in the slot are never consumed — it's a presence-based internal power source so your wiring can be safely hidden from the Excavator's own pulls. Either an external redstone signal *or* a redstone-fuel item keeps the block active.

The strength tier no longer controls a cycle interval — it controls pull *force* and *destruction speed* per tunneling ship. Higher tiers = faster tunneling + harder pull. The whole pipeline is throttled per-emitter (destruction budget, in-flight cap) so even at cap=64 the server tick stays healthy. Pulls stop at any block entity (chests, beacons, other emitters), unbreakable block (bedrock-class), or anything tagged `#magnetization:excavator_immune` — append to that tag to opt out of pulls (claim-mod boundaries, valuable spawners, etc.).

## Cooperative Anchors

Two or more powered Magnetic Anchors that share a bound ship apply a once-per-second angular damp (30%) on top of their normal pull, so a multi-anchor dock keeps the airship level instead of letting it spin from accumulated torques. Single-anchor docks behave exactly as before. Use it for stable airship docking pads.

## Goggles / HUD

Wearing Create's Engineer's Goggles shows a tier/polarity/range readout above any emitter. Without goggles, you still get a one-line "TIER POLARITY" hint when crosshairing. Jade, WTHIT, and TheOneProbe all surface the same info if installed.

Emitters mounted on a contraption also report the host ship's polarity and susceptibility — for example *On ship: NORTH ×1.40* with a `12 ferrous, 3 magnets, 1 inverter` breakdown when goggles are on. The snapshot is synced from the server so all four surfaces (goggles, Jade, WTHIT, TOP) read from the same number.

While goggles are worn, additional world overlays appear:

- **Range rings / arrows**: every active emitter draws a polarity-tinted ring (cool blue = attract, warm red = repel) at its origin's y-plane sized to its effective range. DIRECTIONAL and CONICAL emitters draw an axis arrow instead — a tractor beam aimed sideways doesn't paint a misleading horizontal disc.
- **Anchor tethers**: powered anchors with a bound ship draw a line from the anchor center to the ship's pose. In coop arrangements you can see at a glance which anchor holds which contraption.
- **Inverter connectors**: each Polarity Inverter face-adjacent to an active emitter draws a gold connector segment between them. Two inverters showing two segments confirm cancel-out.
- **Excavator column preview**: the cells that *would* be pulled next cycle are outlined in a polarity-tinted wireframe along the emitter's FACING.

## Configuration

The built-in Mods → Magnetization → Config screen and the generated TOML files are
the canonical complete option list. Player-facing and world-generation options live in
`config/magnetization-common.toml`; server-owner limits, diagnostics, and command
permissions live in `config/magnetization-server.toml`.
The tables below highlight selected high-impact settings; use the config screen or generated TOML files for the exhaustive list.

For the complete generated reference, see [docs/configuration.md](docs/configuration.md). Regenerate it with python3 scripts/generate-config-reference.py; use --check to verify it is current.

### compat — power sources
| Key | Default | Description |
|-----|---------|-------------|
| `compat.allowRedstonePower` | true | Redstone counts as a valid power source for the redstone-driven emitters. |
| `compat.allowEnergyPower` | true | FE/RF counts as a valid power source. The capability stays exposed either way. |
| `compat.requireRedstoneAndEnergy` | false | Require BOTH at once instead of either-or. |
| `compat.emitterEnergyCapacity` | 50000 | Internal FE buffer per emitter. |
| `compat.emitterEnergyDrainPerTick` | 10 | FE consumed per tick while energy-driven. Flat — not scaled by the analog signal. |
| `compat.emitterEnergyTransferRate` | 200 | Max FE/tick accepted from external sources. |
| `compat.analogRedstoneElectromagnet` | false | Scale the Electromagnet's force with the analog redstone level (1–15). |
| `compat.analogRedstoneDipole` | false | Same, for the Dipole Electromagnet (both poles scale together). |
| `compat.analogRedstoneAnchor` | false | Same, for the Magnetic Anchor. |
| `compat.analogRedstoneRepulsor` | false | Same, for the Repulsor Coil. |
| `compat.analogRedstoneTractorBeam` | false | Same, for the Tractor Beam. |
| `compat.analogRedstoneExcavator` | false | Same, for the Magnetic Excavator. Its internal redstone-dust fuel counts as a full signal. |
| `compat.analogRedstoneInducer` | false | Same, for the Structural Inducer (scales its reel-in speed — it emits no field of its own). |

With an analog toggle on, **signal 1 = 200 N** (the Weak tier's force) and **signal 15 = 8000 N**
(Extreme's), ramped geometrically so every level is an equal proportional step. The GUI strength
tier is unchanged and still sets the field's **range**; only force is scaled. It applies only while
redstone is the driver — an FE/RF-powered emitter always runs at full configured strength, and a
partial signal still burns no FE. Hematite and Halbach arrays scale the throttled force
proportionally, so both keep working as usual.

### physics
| Key | Default | Range | Description |
|-----|---------|-------|-------------|
| `physics.strengthMultiplier` | 1.0 | 0.0–100.0 | Global multiplier on every emitter's force. |
| `physics.entityVelocityScale` | 0.05 | 0.0–1.0 | Tick conversion factor for vanilla entity velocity. |
| `physics.conicalHalfAngleCos` | 0.7071 | 0.0–0.999 | Cosine of the half-angle of conical emitters. |
| `physics.maxAccelPerTick` | 50.0 | 0.0–1000.0 | Per-ship-per-tick acceleration cap (m/s²) summed across every emitter touching the ship that tick. 0 to disable. |
| `physics.shipSampleSteps` | 3 | 1–7 | Grid size for volume-integrating a field over a ship's AABB. 1 = single closest-point sample (1.0.0 behaviour); 3 = 27 samples that produce realistic torque. Quadratic cost in steps. |
| `physics.shipLinearDrag` | 0.02 | 0.0–1.0 | Linear-velocity damping per tick applied to any ship being pulled by a magnet. 0 disables. |
| `physics.shipAngularDrag` | 0.05 | 0.0–1.0 | Angular-velocity damping per tick — counterpart to `shipLinearDrag`. Without it, the torque from off-center sample forces could keep a ship spinning indefinitely under sustained pull. 0 disables. |
| `physics.shipBaselineSusceptibility` | 1.0 | 0.0–10.0 | Multiplier on external force a ship feels with no ferromagnetic blocks aboard. 1.0 = full strength. |
| `physics.shipPerFerrousSusceptibility` | 0.05 | 0.0–5.0 | Susceptibility added per ferromagnetic block (`#magnetization:ferromagnetic_blocks`) aboard. |
| `physics.shipPerMagnetSusceptibility` | 0.15 | 0.0–5.0 | Susceptibility added per magnet emitter block (`#magnetization:magnetic_emitter`) aboard. Magnets count as ferrous-plus; their pole does NOT shift the ship's pole. |
| `physics.shipMaxSusceptibility` | 20.0 | 1.0–100.0 | Upper cap on a ship's susceptibility multiplier. |
| `physics.shipScanIntervalTicks` | 20 | 1–6000 | How often (ticks) a ship's magnetic state is rescanned. 20 = 1 s. Ships not inside any active field never get scanned regardless. |

### guiLimits — per-emitter GUI ceilings (admin caps)
| Key | Default | Range | Description |
|-----|---------|-------|-------------|
| `guiLimits.electromagnetMaxStrength` | EXTREME | enum | Max strength tier the Electromagnet GUI can select. |
| `guiLimits.electromagnetMaxRange` | 256 | 0–512 | Max range in blocks. |
| `guiLimits.anchorMaxStrength` | EXTREME | enum | Max strength tier the Magnetic Anchor GUI can select. |
| `guiLimits.anchorMaxRange` | 256 | 0–512 | Max range in blocks. |
| `guiLimits.repulsorMaxStrength` | EXTREME | enum | Max strength tier the Repulsor Coil GUI can select. |
| `guiLimits.repulsorMaxRange` | 256 | 0–512 | Max range in blocks. |
| `guiLimits.tractorMaxStrength` | EXTREME | enum | Max strength tier the Tractor Beam GUI can select. |
| `guiLimits.tractorMaxRange` | 256 | 0–512 | Max range in blocks. |
| `guiLimits.excavatorMaxStrength` | EXTREME | enum | Max strength tier the Excavator GUI can select. |
| `guiLimits.excavatorMaxRange` | 256 | 1–384 | Max scan depth for the Excavator. |
| `guiLimits.excavatorMaxBlocksPerCycle` | 256 | 1–512 | Cap on cells the Excavator may consider per cone scan; safety against config typos. |
| `guiLimits.excavatorMaxInFlight` | 16 | 1–64 | Admin ceiling for concurrent in-flight pulls per Excavator. Each in-flight pull is a Sable sub-level, so this also caps physics-simulation cost. |

### content
| Key | Default | Description |
|-----|---------|-------------|
| `content.disabledBlocks` | `[]` | Block paths (e.g. `"repulsor_coil"`) to disable — block emits no field, hidden from creative, GUI skipped. Placed instances stay but go inert. |
| `content.disabledItems` | `[]` | Item paths to disable — hidden from creative, special effects skipped. |

### items
| Key | Default | Range | Description |
|-----|---------|-------|-------------|
| `items.grappleCooldownTicks` | 20 | 0–600 | Cooldown between Magnetic Grapple right-clicks. |
| `items.grappleMaxRange` | 24 | 4–128 | Max scan range for the grapple. |
| `items.compassRange` | 16 | 4–128 | Field Compass scan radius. |

### tools — magnetized tool signature abilities
| Key | Default | Description |
|-----|---------|-------------|
| `tools.swordYankEnabled` | true | Magnetized sword yanks opposite-pole armored targets one step on hit. |
| `tools.pickaxeOreRipEnabled` | true | Sneaking with a magnetized pickaxe rips nearby ferromagnetic ore as items. |
| `tools.pickaxeRipRadius` | 4 | (1–16) Scan radius for the pickaxe ore-rip. |
| `tools.pickaxeRipIntervalTicks` | 20 | (4–200) Ticks between ore-rip pulses. |
| `tools.axePulseEnabled` | true | Chopping a log with a magnetized axe sends a radial pull on nearby items + entities toward the player. |
| `tools.shovelPanEnabled` | true | Digging soil with a magnetized shovel may drop trace iron / raw magnetite. |
| `tools.shovelPanChance` | 0.04 | (0–1) Probability per qualifying block break. |
| `tools.hoeDowseEnabled` | true | Right-clicking a magnetized hoe pings ferromagnetic ore with marker particles. |
| `tools.hoeDowseRadius` | 8 | (2–32) Hoe dowsing ping radius. |
| `tools.hoeDowseCooldownTicks` | 60 | (10–600) Cooldown between dowsing pings. |

### worldgen
| Key | Default | Description |
|-----|---------|-------------|
| `worldgen.magneticPeaksEnabled` | false | If true, denser magnetite veins generate in `#minecraft:is_mountain` biomes. |
| `worldgen.anomalyBiomeEnabled` | false | Default off — opt-in. When true, the Magnetic Anomaly biome registers a TerraBlender region (so it spawns naturally) **and** its runtime effects activate inside it (vanilla-compass spin, field-compass scramble, 1.5× emitter strength, random chaos field). When false the biome JSON still loads, so `/locate biome magnetization:anomaly` and `/magnetization tp anomaly` still work — but it won't spawn on its own and effects are inert. |
| `worldgen.anomalyChaosStrength` | 1.0 | Multiplier on the anomaly's chaos-field impulses (ships, players, items). 0 = compass spin + emitter bonus only, no kinetic chaos. Range 0.0–10.0. |
| `worldgen.petrifiedForestEnabled` | false | Default off — opt-in. When true, the Petrified Forest biome registers a TerraBlender region so it spawns naturally. Turning it off only blocks natural generation; `/locate biome` and `/magnetization tp petrified_forest` still work. |

### lightning
| Key | Default | Description |
|-----|---------|-------------|
| `lightning.lirmEnabled` | true | Lightning-Induced Remnant Magnetism: lightning strikes magnetize one unstamped metal armor/tool piece on the struck entity, and have a high chance of petrifying nearby logs. |

### debug
| Key | Default | Description |
|-----|---------|-------------|
| `debug.debugLogging` | false | FieldApplicator + anchor-binding diagnostic logs. Leave off on busy servers. |

The base magnetite ore vein generates in every overworld biome regardless of these flags — `worldgen.*` only controls optional flavor passes layered on top.

## Building

```sh
./gradlew build                # produces build/libs/magnetization-<version>.jar
./gradlew test                 # unit tests on field math + ship state
./gradlew runGameTestServer    # headless in-world integration tests (emitter lifecycle + energy drain)
```

`./gradlew runClient` for an in-dev test run; `./gradlew runServer` for a hands-on dedicated-server run; `./gradlew runData` to regenerate recipes + loot tables (most other data is hand-authored).

### Prepared 1.3.0 playtest worlds

Two isolated client profiles keep persistent, disposable saves under
`run-playtest-lab/` and `run-playtest-survival/`:

```sh
./gradlew runPlaytestLabClient
./gradlew runPlaytestSurvivalClient
```

On the first launch of each profile, create one ordinary world from Minecraft's
world screen. The first player login automatically stages that preset; subsequent
launches reopen the persistent save without rebuilding over your observations.

- **Test Lab:** Creative mode, a complete Magnetization item chest bank, a focused
  inventory kit, texture/material gallery, Electrolyzer rejection and output-stall
  stations, formed Tokamak and Fusion structures, a powered Railgun, all Dipole
  facings, Gallium/Golem inspection, an automation bench, and marked ship/portal
  lanes with their supplies.
- **Survival Progression:** Survival mode, crafting/smelting infrastructure, empty
  progression machines, formed Tokamak/Fusion structures, and raw inputs for the
  Water → Hydrogen → Deuterium → Tritium → Helium-3 chain. Finished isotope fuels
  are intentionally not supplied, so advancement and recipe progression remain real.

The commands below exist only in these playtest profiles (permission level 2):

```text
/magnetization playtest lab reset
/magnetization playtest lab kit
/magnetization playtest survival reset
/magnetization playtest survival kit
/magnetization playtest goto <station>
/magnetization playtest where
```

Running `setup` instead of `reset` relocates a preset to the player's current
chunk-aligned position. Both operations intentionally replace the preset's marked
64×48 area; use them only inside the disposable playtest saves.

The prepared worlds can also be driven through the host desktop with
`scripts/run-playtest-automation.sh lab` or `scripts/run-playtest-automation.sh survival`.
The manifest in `playtest/automation-matrix.json` controls stations, log checks, video
captures, and visual thresholds. Evidence is written beneath the ignored
`playtest-results/` directory. The first accepted run creates baselines and reports
`BASELINE_CREATED`; subsequent runs compare captures and report `PASS` or `FAIL`.
The lab video actions spawn a moving Sable test ship and invoke an AeroPortals
dimension transfer while recording; detailed ship-data retention remains covered by
the deterministic compatibility GameTests. The matrix also opens the major machine
GUIs, records their interaction states, and spawns both gallium and MR-fluid golems
for visual inspection. Fuel-slot acceptance/rejection and setting changes remain
operator-confirmed steps because their GUI coordinates vary with scale and recipe-viewer layout.
Before traversing the lab, the runner seeds active Electrolyzer, Tokamak, Fusion
Thruster, and Railgun state, saves and exits to the title screen, reopens the same
world, and requires a server-side `PLAYTEST_ASSERT PASS persistence` marker.
Pass `persistence` as the second argument for a focused save/reopen check.
Use the `attach` second argument to exercise an already-running matching profile.

### Release smoke-test profiles

Four separate profiles, so a failure says *which* environment broke rather than "it broke".

```sh
./gradlew smokeServerMinimal   # RELEASE GATE — minimal dedicated server, hard deps only
./gradlew smokeGameTest        # RELEASE GATE — headless GameTest suite
./gradlew smokeClientNormal    # manual — client, hard deps only (no compat pack)
./gradlew smokeClientCompat    # manual — client + Sodium/Iris/JEI/tag-coverage mods
./gradlew releaseGate          # build + both automated gates
./gradlew releaseMatrixGate    # release gate + every isolated published optional-mod suite
```

`smokeServerMinimal` boots a real dedicated server in its own `run-smoke-server/`
directory with **hard dependencies only**, lets it run (default 210 s, override with
`-PmagSmokeSeconds=<n>`), types `stop` on its console, and then **fails the build** if
the log carries a FATAL, a stack trace, a server that never reported `Done`, or any
unexplained `ERROR` line. A short, individually-justified allowlist (`smokeIgnoredErrors`
in `build.gradle`) covers environment noise the mod doesn't own — chiefly Mixin
resolving our hard dependencies' client-only mixin targets on a dedicated server — and
the count of skipped lines is always printed, so the list can't quietly become "ignore
everything". `-PmagSmokeAllowErrors` is the escape hatch for a one-off review. The gate
needs an accepted EULA: it copies the one from `run/eula.txt` rather than writing an
acceptance for you.

The two client profiles differ only in classpath: `smokeClientCompat` is the historical
`runClient` (everything on `localRuntime`), `smokeClientNormal` is the same client
without it, which is how you tell "our bug" from "the compat pack's bug".

## Known issues

- Sable may log `Received a sub-level movement packet for a non-existent sub-level` on the client at low frequency while the Magnetic Excavator is actively pulling. This is a packet-ordering race during the excavator's rapid sub-level assemble→remove cycle for blocks Sable couldn't initialize a body for; it's non-fatal and only affects log noise.

## License

CC0 1.0 Universal
