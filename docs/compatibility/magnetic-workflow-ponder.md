# Magnetic workflow Ponder scenes

Three tutorials explain the existing mechanics on Minecraft 1.21.1 / NeoForge.

| Scene | Open Ponder from | Demonstration |
| --- | --- | --- |
| `magnetic_basics` | Permanent Magnet, Electromagnet, Kinetic Electromagnet or Polarity Inverter | NORTH/NORTH repulsion; SOUTH/NORTH attraction; one and two face-adjacent inverters on a fixed emitter; one and two onboard inverters; flipping a mounted magnet without changing ship polarity. Blue/red glass explicitly marks NORTH/SOUTH; these markers are teaching aids. |
| `magnetic_excavator` | Magnetic Excavator | A downward mining cone over two ore blocks; strength/range/concurrent-pull configuration; non-consumed internal redstone power; separate moving ore blocks, consumed intervening stone and arrival drops in an adjacent barrel. |
| `repulsor_transport` | Repulsor Coil, Vector Core or Copper Block | Three upward powered world coils; actual Vector Core installation and EAST conveyor setting perpendicular to UP; lateral travel; decreasing motion over a copper braking pad. |

The distinction between emitter and ship polarity is deliberate: fixed emitters check face-adjacent inverter parity, whereas ships count all onboard inverters. Mounted magnets increase susceptibility; their local polarity does not choose the ship's polarity. The Excavator's taught limits remain bounded by server configuration. The repulsor example assumes the default NORTH polarity of an ordinary ship and default enabled conveyor and Lenz behavior.

Ponder uses the real block states, block entities, fuel slot, Vector Core slot and direction setters. Its moving sections, excavation and barrel contents illustrate outcomes; the client schematic does not run Sable assembly/mining or server force integration. The copper sequence uses smaller displacements over equal durations to illustrate braking. Its motion is an explanation rather than a measurement of configured acceleration or stopping distance. Existing server GameTests independently exercise the real repulsor and Lenz mechanics.

## Validation

Accepted: all three scenes completed at effective GUI scales 2 and 3, all 17 instructions and eight targets passed on each run, all 34 instruction frames were reviewed, and 54 native captures were saved. The full build, release-JAR verification, 292 unit tests and 173 required core GameTests passed. The shared material-control change additionally passed its seven required regression GameTests.

Each of the 17 instructions checks the played native world before its rendered frame is captured. First/final states, uninterrupted complete playback, and exactly one matching scene from each of the eight advertised targets are also required. Focused runs at effective GUI scales 2 and 3 check text and item controls. Results, source hashes, logs and reviewed images are stored in [the evidence directory](evidence/magnetic-workflow-ponder/).

The catalog test checks all 26 scenes and their 86 instructions / 112 localization entries. Ship scanner unit tests cover onboard inverter parity and susceptibility. The core server GameTest gate covers existing mechanics, including directional repulsor travel and copper Lenz braking. Tutorial state assertions do not substitute for server mechanics tests.

Reproduce with Java 21:

```sh
./gradlew test --tests '*PonderSceneCatalogTest' --tests '*ShipMagneticScannerTest'
./gradlew smokeGameTest
MAGNETIZATION_AUDIT_SCENE=magnetic_basics,magnetic_excavator,repulsor_transport \
  MAGNETIZATION_AUDIT_GUI_SCALE=3 bash scripts/run-lifecycle-presentation-audit.sh
# Archive build/validation-audit before repeating at GUI scale 2.
```

These focused runs cover the three new scenes and connected reload checks. They do not repeat the complete Field Manual, coaster visual sweep, or native playback of every pre-existing scene.
