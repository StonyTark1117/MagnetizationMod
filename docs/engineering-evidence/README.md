# Magnetic engineering verification — 2026-09-30

Verified on Java 21, Minecraft 1.21.1, NeoForge 21.1.252, Create 6.0.11-295 and Sable 2.0.5.

| Requirement | Evidence |
| --- | --- |
| Server-calculated ship force, torque and force-cap diagnostics | `inspectionReportsAppliedForceAndCap` compares reported force to actual rigid-body velocity change; the real client receives two contributions and reports limiting. See `engineering-fields.png` and `client-audit.log`. |
| Dock presence, settled/lost/distance outputs, hysteresis and continuous dwell | Five `DockingStateTest` cases cover all modes, threshold chatter, target replacement and persistence. `dockUsesRelativeMotionAndPersistsItsLink` exercises actual moving Sable bodies, the switch signal, link and mode serialization. See `engineering-dock.png`. |
| Signed RPM, reversal, stopping, role handoff and conflicts | `shaftsShareLoadReverseStopConflictAndSwapRoles` exercises real Create networks and motors. |
| Shared capacity, combined receiver loads, no retransmission | The first two shaft GameTests verify one original network/source, stress and overload recovery; a third shaft outside the source radius stays stopped. |
| Local-drive priority, saved receivers and range changes | `multipleReceiversShareCapacityAndLocalDriveTakesOver` verifies local-drive takeover, receiver NBT reload, stopping, re-entry and compatible/conflicting drives. |
| Source availability without forced chunk loading | `sourceChunkUnloadStopsReceiverAndReloadReconnects` removes a real chunk ticket, waits for actual source unload, verifies receiver stop and reload reconnection. |
| Three distinct material variants and independent server ranges | `materialsUseIndependentConfiguredRangesAndSharedDriveCapacity` checks material strengths, custom ranges, shared capacity and no attraction source. Screenshots show distinct original geometry; all three model, blockstate, recipe and loot resources were checked in the built JAR. |
| Goggles shaft status, field arrows and inspection release | Real dedicated-server/client audit checks synchronized receiver RPM and server field payload, captures the rendered views, and confirms release clears inspection. Screenshots were visually reviewed. |
| Documentation | README, generated configuration reference, localized Field Manual entries, Ponder scenes and central item-use tooltips updated. Ponder catalog and localization validation run with the unit suite. |

## Automated commands

Run from the repository root:

```sh
./gradlew -PmagMinimalUnitTests build --console=plain
python3 scripts/generate-config-reference.py --check
bash scripts/run-gametest-gate.sh runMagneticEngineeringGameTestServer run-magnetic-engineering-gametest 300 -PmagMinimalUnitTests
bash scripts/run-gametest-gate.sh runMagneticEngineeringCoreGameTestServer run-magnetic-engineering-core-gametest 300 -PmagMinimalUnitTests
bash scripts/run-gametest-gate.sh runMagneticEngineeringRegressionGameTestServer run-magnetic-engineering-regression-gametest 300 -PmagMinimalUnitTests
```

All six engineering, 173 core and four regression GameTests passed. `unit-tests.txt` records 259 passing unit tests. The gate uses a fresh disposable world and terminates only its own process group after Minecraft's passing summary because Sable may leave a physics thread alive during shutdown. The dedicated profiles avoid the older generic run task's global Java cleanup hook.

## Rendered audit reproduction

Use fresh disposable `run-magnetic-engineering-audit-server` and `run-magnetic-engineering-audit-client` directories. Accept the Minecraft EULA in the server directory. Set its `server.properties` to port `25593`, IP `127.0.0.1`, `online-mode=false`, `allow-flight=true`, `level-type=minecraft:flat`, `view-distance=5`, `simulation-distance=4` and `max-tick-time=-1`. Run `./gradlew runMagneticEngineeringAuditServer -PmagMinimalUnitTests`, wait for startup, then run `./gradlew runMagneticEngineeringAuditClient -PmagMinimalUnitTests` on a display (the captured run used an isolated Xvfb display).

The opt-in fixture equips EngineeringAudit with goggles, builds three material rotors and real driven shafts, links a dock to a real Sable ship, and applies two known forces through the production field applicator. It moves the camera through shaft, dock, force and material views. Captures are saved under the client directory's `screenshots`. Wait for `ENGINEERING_UI_PASS`, `ENGINEERING_INSPECTION_RELEASE_PASS`, `ENGINEERING_MATERIAL_RENDER_PASS` and all four `ENGINEERING_CAPTURE` lines, then stop these audit processes. Stopping an interactive Gradle client/server task externally produces a nonzero task exit; the audit assertions and independent build result are recorded separately.

The render audit uses controlled forces and a stabilized ship to keep the view reproducible. It is not a prolonged gameplay or performance soak. Patchouli and Ponder content is validated as resources/catalog entries; the screenshots cover the actual goggles and block rendering.

## Issues found during verification

The initial combined core/regression profile omitted Curios, causing two capability tests to fail. The core profile now explicitly includes its Curios runtime. A subsequent combined run encountered `Body has been removed` in the existing rotated fusion-thruster test; the final separate core run passed that test. Separate profiles match the existing suite boundaries. The core suite also caught missing central use-tooltip entries for the new shafts; those entries were added before the final 173-test pass.
