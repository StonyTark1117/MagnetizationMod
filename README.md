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

- **Iron / chainmail / gold / netherite armor** makes you magnetizable. Each piece worn adds susceptibility — full plate set is yanked hard by anchors.
- **Magnetic Grapple** turns infrastructure into traversal. Right-click pointing at any space within 24 blocks of an active attractive field; the closest qualifying emitter pulls you toward it. Cooldown: 1 second.
- **`/magnetization debug field <pos>`** prints the field state at a block. Useful for debugging.

## Goggles / HUD

Wearing Create's Engineer's Goggles shows a tier/polarity/range readout above any emitter. Without goggles, you still get a one-line "TIER POLARITY" hint when crosshairing. Jade, WTHIT, and TheOneProbe all surface the same info if installed.

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
