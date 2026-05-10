# Magnetization

A NeoForge 1.21.1 addon for **[Create: Aeronautics](https://modrinth.com/mod/create-aeronautics)** that adds magnetic forces, anchors, and propulsion for Sable-driven contraptions.

## Requirements

- Minecraft **1.21.1**
- NeoForge **21.1.219+**
- [Create](https://modrinth.com/mod/create) **6.0.9+**
- [Sable](https://modrinth.com/mod/sable) **1.1.1+**
- [Create: Aeronautics](https://modrinth.com/mod/create-aeronautics) **1.1.0+**
- Simulated **1.1.0+**

Optional integrations (auto-detected when installed):
- [Jade](https://modrinth.com/mod/jade), [WTHIT](https://modrinth.com/mod/wthit), or [The One Probe](https://modrinth.com/mod/the-one-probe) for HUD info on emitters.
- [JEI](https://modrinth.com/mod/jei) or [REI](https://modrinth.com/mod/rei) for an in-browser info page on the ferromagnetic-item tag.

## How it works

Every block in this addon either *emits a magnetic field* or *responds to one*. Fields have a polarity (NORTH or SOUTH), a strength tier (WEAK→EXTREME), and a shape (omnidirectional, directional, conical). Like polarities repel, opposite polarities attract. Forces are applied to:

- **Sable sub-levels** (Create: Aeronautics ships) intersecting the field's range — pushes/pulls the contraption.
- **Magnetizable entities** within the field — iron golems, undead mobs, ferromagnetic item drops, players in metal armor.

Onboard-only configurations (every magnet on the same ship) produce zero net thrust — internal forces cancel, just like real physics. To propel a ship, you build *between* the world and the ship.

## Block reference

| Block | Power | Field shape | Use |
|-------|-------|-------------|-----|
| **Electromagnet** | redstone | omnidirectional, MEDIUM | Generic pull. Nice for item collectors and "tractor docking" stations. |
| **Kinetic Electromagnet** | Create rotation | omnidirectional, scales with RPM | Variable strength via gearbox/clutch; fits Create-pack progression. |
| **Magnetic Anchor** | redstone | omnidirectional, STRONG | Locks a ship in place. The first ship in range becomes the bound target; binding persists across reload. |
| **Repulsor Coil** | redstone | conical (placement-aligned), MEDIUM | Pushes ships in the direction the coil faces. Place upward for a hover pad; place sideways/downward to line a tunnel and shove ships through it. |
| **Tractor Beam Emitter** | redstone | directional, STRONG | Pulls ships in front of it toward the emitter. Wrench-rotatable. |
| **Magnetic Excavator** | redstone | directional, MEDIUM | Rips ferromagnetic ores out of the column along its facing — pulls the entire column above each ore as a single Sable sub-level toward the emitter, dismantles on arrival, and drops the contents (Fortune/Silk Touch capable via the in-GUI tool slot). Wrench-rotatable. |
| **Permanent Magnet** | none (always-on) | omnidirectional, WEAK | Right-click to flip polarity. Pairs of opposing-polarity permanent magnets between world and ship build static propulsion tracks. |
| **Polarity Inverter** | passive | — | Adjacent emitters get their field polarity flipped. Two inverters cancel. |
| **Magnetic Switch** | passive | — | Emits redstone signal proportional to nearest ship's distance (8-block range). Pair with anchor + comparator for auto-docking. |
| **Lodestone Core** | — | — | Crafting component for every emitter. |

## Items

- **Ferromagnetic Ingot** — `8 iron + 1 lodestone → 8 ingots`. Base material for everything else.
- **Magnetic Plate** — `3 ferromagnetic ingot → 3 plates`. Cladding for emitter recipes.
- **Field Compass** — `4 plates + 1 vanilla compass → 1 compass`. Right-click for the strongest active field nearby; sneak right-click lists every active field in range.
- **Magnetic Grapple** — `2 plates + 1 lodestone core + 1 string → 1 grapple`. Right-click an attractive field source within 24 blocks to be yanked toward it.
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
- **`/magnetization debug field <pos>`** prints the field state at a block. Useful for debugging.

## Magnetizing armor & tools

Right-click the **Electromagnet** or **Kinetic Electromagnet** with an empty hand to open its GUI. Insert a piece of metal armor (anything in `#magnetization:metal_armor`) or a metal tool (anything in `#magnetization:metal_tools`) into the slot, then click **N** / **S** / **Clear** to stamp the polarity:

- **Magnetized armor**: while worn, gives the player +0.6 susceptibility per piece (on top of the base +0.4 for tagged metal armor) and overrides the wearer's effective polarity. A player in magnetized-NORTH plate is repelled by NORTH emitters and pulled hard by SOUTH ones.
- **Magnetized tools**: while held or worn, attract dropped ferromagnetic items in a 4-block radius per magnetized tool the player carries. The same Magnetized-stamp logic gives a sword (or pickaxe / axe / shovel / hoe) a personal item-magnet effect — drop iron near a held magnetized sword and it flies to you.
- **Combat synergy**: hitting any LivingEntity with a magnetized weapon stamps the target with the **Magnetized** mob effect for 3 seconds. Opposite-pole weapon-on-armor hit upgrades to amp 2 — the target is *pinned* (60% horizontal velocity damp per tick) under the magnetic field, simulating iron grabbing iron.

## Magnetic Excavator

The Excavator is a redstone-powered ferromagnetic mining block. Right-click (empty hand) for its GUI: strength tier (W/M/S/E), range slider (in blocks), and a persistent tool slot for an enchanted item or book. Wrench-rotate to change the active face direction; defaults to FACING=DOWN (place against a ceiling, mines the floor below).

Each pull cycle:

1. The Excavator scans the column along its FACING for the nearest block in `#magnetization:ferromagnetic_blocks` (covers `c:ores/iron|gold|copper|netherite_scrap`, `c:storage_blocks/*` for those metals, plus addon magnetite blocks).
2. The whole column from the cell adjacent to the emitter through the ferromagnetic ore is removed from the world and assembled into a single Sable sub-level via `SubLevelAssemblyHelper.assembleBlocks`.
3. The standard FieldApplicator pulls Sable ships, so the column-ship gets dragged toward the emitter as a fully-simulated rigid body — players see the entire column rip out of the ground and float toward the magnet.
4. When the ship arrives at the emitter, it dismantles and drops the original blocks' loot. The tool slot's enchantments thread through `LootContextParams.TOOL` — Fortune III multiplies ore drops, Silk Touch silk-mines, etc. Damageable tools take 1 durability per cycle; books are immune.

Cycle interval scales with strength tier (40/20/10/5 ticks for WEAK/MEDIUM/STRONG/EXTREME). Pull stops at any block entity (chests, beacons, other emitters), unbreakable block (bedrock-class), or anything tagged `#magnetization:excavator_immune` — append to that tag to opt out of pulls (claim-mod boundaries, valuable spawners, etc.).

The drop cell pushes directly into adjacent IItemHandlers (hopper, chest, barrel) before falling back to ItemEntity, so excavator → hopper → chest builds work without the one-tick latency.

## Cooperative Anchors

Two or more powered Magnetic Anchors that share a bound ship apply a once-per-second angular damp (30%) on top of their normal pull, so a multi-anchor dock keeps the airship level instead of letting it spin from accumulated torques. Single-anchor docks behave exactly as before. Use it for stable airship docking pads.

## Goggles / HUD

Wearing Create's Engineer's Goggles shows a tier/polarity/range readout above any emitter. Without goggles, you still get a one-line "TIER POLARITY" hint when crosshairing. Jade, WTHIT, and TheOneProbe all surface the same info if installed.

While goggles are worn, additional world overlays appear:

- **Range rings / arrows**: every active emitter draws a polarity-tinted ring (cool blue = attract, warm red = repel) at its origin's y-plane sized to its effective range. DIRECTIONAL and CONICAL emitters draw an axis arrow instead — a tractor beam aimed sideways doesn't paint a misleading horizontal disc.
- **Anchor tethers**: powered anchors with a bound ship draw a line from the anchor center to the ship's pose. In coop arrangements you can see at a glance which anchor holds which contraption.
- **Inverter connectors**: each Polarity Inverter face-adjacent to an active emitter draws a gold connector segment between them. Two inverters showing two segments confirm cancel-out.
- **Excavator column preview**: the cells that *would* be pulled next cycle are outlined in a polarity-tinted wireframe along the emitter's FACING.

## Configuration

`config/magnetization-server.toml`:

| Key | Default | Range | Description |
|-----|---------|-------|-------------|
| `physics.strengthMultiplier` | 1.0 | 0.0–100.0 | Global multiplier on every emitter's force. |
| `physics.entityVelocityScale` | 0.05 | 0.0–1.0 | Tick conversion factor for vanilla entity velocity. |
| `physics.conicalHalfAngleCos` | 0.7071 | 0.0–0.999 | Cosine of the half-angle of conical emitters. |
| `physics.maxAccelPerTick` | 0.5 | 0.0–100.0 | Per-ship acceleration cap (blocks/tick²) after Sable's mass scaling. Prevents trivial griefing of tiny ships. 0 to disable. |
| `worldgen.magneticPeaksEnabled` | false | true/false | If true, denser magnetite veins generate in `#minecraft:is_mountain` biomes. |
| `worldgen.anomalyBiomeEnabled` | false | true/false | Stub for the magnetic anomaly biome (codec gate is wired; biome injection is not yet implemented). |

The base magnetite ore vein generates in every overworld biome regardless of these flags — the `worldgen` toggles only control optional flavor passes.

## Building

```sh
./gradlew build       # produces build/libs/magnetization-<version>.jar
./gradlew test        # 13 unit tests on field math
```

`./gradlew runClient` for an in-dev test run; `runData` is unreliable in the moddev plugin's classpath assembly here, so providers run only from a working IDE setup.

## License

MIT.
