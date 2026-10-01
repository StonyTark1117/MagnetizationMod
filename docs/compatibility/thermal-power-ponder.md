# Heat, stabilization and charging tutorials

Three tutorials describe the shipping mechanics on Minecraft 1.21.1 NeoForge:

- **Pyrrhotite heat and catalysts:** cold/WEAK/STRONG/EXTREME heat states, direct heat, basic/enhanced/cosmic relay distances of 3/5/7, non-chaining catalysts, hottest-source selection and removal on the next heat scan. The separate 9×5×9 schematic shows the actual distances. Blue cells are an explicitly described strength diagram. The stale catalyst JavaDoc claiming chained transmission is corrected; gameplay is unchanged.
- **Gyrostabilizer:** an independently moving ship section rotates unpowered, holds its angle under redstone or FE power, continues translating, and resumes rotation when power is removed. FE costs 20 per tick; redstone takes priority without consuming FE. A ground-mounted device has no ship to stabilize.
- **Induction Pad:** the first instruction explains the default-off server switch. A gold supply marker, carried equipment token and green charge gauge illustrate the FE transfer. The carrier leaves the default charging area. Only equipment exposing a receiving FE capability can charge; ordinary tools cannot. Inventory, offhand, armor and enabled Curios slots are described, with configurable range, interval and rate.

The `magnetic_shaft` playback auditor now checks source/receiver axes, motor facing and removal, and both additional material variants at every instruction as well as the initial/final frames. Its existing mechanical transmission tests remain separate from this scene's static schematic. The concurrent docking tutorial work owns `docking_signals` playback checks and the combined catalog sweep.

## Behavior evidence

The new regression tests exercise the actual heat resolver at each Blaze Burner heat level, catalyst axis and diagonal range boundaries, a no-chain counterexample and hottest-source priority. A separate native block-tick test waits for the configured cold and residual scan intervals and requires the real field to appear and disappear.

The Gyrostabilizer test assembles a real Sable ship, sets its rigid body's linear and angular velocity, and invokes the shipping ship-actor callback. Unpowered rotation remains; redstone and FE each cancel rotation without changing translation. It checks exact FE consumption, redstone priority and the off-ship control. The Ponder ship transforms are illustrative and are not a physics simulation.

The charging test uses pinned Immersive Engineering 12.4.2-194: a Railgun in inventory and offhand, and a worn Powerpack with an LV Capacitor installed through its native item handler. The test requires real receiving FE capabilities and verifies positive transfer, energy conservation, disabled/out-of-range/empty-pad controls, ordinary-tool rejection and buffer serialization. A bare Powerpack has no FE capability. The fixture reads a fresh capability after transfer because the upstream Powerpack wrapper copies its nested capacitor stack. This does not establish every third-party Curios equipment combination.

## Validation status

The isolated regression profile passes **10/10**, including the three new heat/gyro tests. The pinned IE profile passes **11/11**, including real carried-equipment charging. The concurrent combined regression run extends this to 13/13 with the other tutorial tests.

Final GUI scale 2 passes all **19 instruction checks**, all **six target registrations** and complete playback of the three scenes, followed by connected reload checks and clean dedicated-server shutdown. All 19 instruction captures were visually reviewed; 25 original captures (instructions plus first/final frames) and full logs are retained in [GUI scale 2 evidence](evidence/thermal-power-ponder/gui2/).

Final GUI scale 3 also passes all **19 instruction checks**, all **six target registrations** and full playback, as part of the completed 32-scene catalog sweep. The client and dedicated server report success and finish a clean full save. All 19 instruction frames were visually reviewed; [GUI scale 3 evidence](evidence/thermal-power-ponder/gui3/) retains the 25 original captures and full logs. Together the two scales retain **50 captures**, including **38 reviewed instruction frames**.

The [machine-readable results](evidence/thermal-power-ponder/results.json) record artifact pins, evidence checksums and the exact compiled source hashes. Later camera, caption and hint changes in five other scenes do not alter these three tutorials; their focused replays and the broader catalog results are recorded in the [complete catalog report](ponder-complete.md).

Reproduce the scoped playback with `MAGNETIZATION_AUDIT_SCENE=pyrrhotite_heat,gyrostabilizer,induction_pad MAGNETIZATION_AUDIT_GUI_SCALE=2 bash scripts/run-lifecycle-presentation-audit.sh` (use 3 for the other scale). Server checks use `./gradlew smokeRegressionGameTest` and `bash scripts/run-gametest-gate.sh runImmersiveEngineeringGameTestServer run-immersive-engineering-gametest 300`, with Java 21.
