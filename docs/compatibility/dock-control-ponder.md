# Docking and emitter-control Ponder tutorials

These four tutorials explain the existing controls on Minecraft 1.21.1 / NeoForge.

| Scene | Open from | Demonstration |
| --- | --- | --- |
| `docking_signals` | Magnetic Anchor or Magnetic Switch | Powered capture, empty-hand sneak-click linking, present/settled/lost/distance modes, direct redstone output and cooperative anchors. Replaces the one-caption overview. |
| `railgun_remote` | Railgun Remote or Railgun Emitter | GUI pairing, taking the remote out while retaining manual mode, holding and boarding a payload, remote launch and sneak-use unpairing. Complements the existing rail-construction scene. |
| `imprint_module` | Imprint Module, Electromagnet or Repulsor Coil | Capture configured strength/polarity/range, apply across emitter types, clamp to destination limits without consuming the preset, and clear in air without changing the destination. |
| `tractor_beam` | Tractor Beam or Polarity Inverter | Wrench aiming, powering a directional field, pulling an ordinary NORTH ship opposite the SOUTH emitter's facing axis, rotating the axis and reversing force with an inverter. |

The client schematic uses real block entities, modes, slot contents, bindings and item data components. Ship capture, movement, boarding and launch are illustrations: Ponder world sections are not live Sable ships. Dock output samples use the shipping `DockingState` state machine and are loaded into the native client switch; they do not claim that the schematic measures a real ship. The tutorial example uses an eight-block range, one-block tolerance and 40-tick dwell; live server settings determine the actual thresholds.

The Imprint clamp example explicitly assumes destination limits of MEDIUM/16. The scene sets those illustrative destination values without changing the viewer's server configuration. Server GameTests separately exercise the shipping interaction subscriber with those actual configuration limits. Capture copies configured strength instead of temporary redstone throttling. Projection preserves the preset and polarity; clearing removes only the item's preset.

Railgun sneak-use always clears the carried binding. Returning the pair to automatic operation requires the bound breech to be reachable in the correct dimension. Powering an anchor off retains its binding. Cooperative damping requires multiple powered anchors bound to the same ship.

## Native behavior checks

`DockControlPonderGameTests` invokes the actual Imprint right-click event subscriber and item-use handler, checks cross-emitter clamping and clear behavior, tests all six native Tractor Beam field axes and inverse forces, and assembles a real Sable ship for automatic anchor capture, cooperative angular damping, damping throttle, switch output and binding retention. The existing engineering suite checks moving-dock relative translation/spin, link persistence and target loss. The core suite includes the full real Railgun GUI-container pairing, held payload, remote item use, launch and unpair flow.

The opt-in Ponder auditor verifies every instruction in these four scenes. It requires native slot, binding, preset, power, facing, mode and signal state, plus first/final states, uninterrupted complete playback and exactly one matching scene compiled from every advertised target. The formerly missing `magnetic_shaft` checks are also covered at both GUI scales by the concurrent thermal/power audit work.

## Acceptance

Focused GUI-scale-2 playback passed all five scenes, 25 instructions and 12 target registrations, with 38 Ponder captures saved and all instruction frames reviewed. The final build and release-JAR checks passed, together with 292 unit tests, 173 core GameTests, 13 regression GameTests and seven engineering GameTests.

The complete GUI-scale-3 sweep passed all 32 scenes, 125 instructions and 51 target registrations. These five scenes again passed 25 instructions and 12 registrations, with all instruction frames reviewed. The final Remote camera framing is checked separately at both scales alongside four existing layout repairs; see [the complete catalog report](ponder-complete.md). Results are stored in [the evidence directory](evidence/dock-control-ponder/) and [the complete sweep directory](evidence/ponder-complete/).

Reproduce using Java 21:

```sh
./gradlew build verifyReleaseJar smokeGameTest smokeRegressionGameTest
bash scripts/run-gametest-gate.sh runMagneticEngineeringGameTestServer run-magnetic-engineering-gametest 180
MAGNETIZATION_AUDIT_SCENE=docking_signals,railgun_remote,imprint_module,tractor_beam,magnetic_shaft \
  MAGNETIZATION_AUDIT_GUI_SCALE=2 bash scripts/run-lifecycle-presentation-audit.sh
```

Archive `build/validation-audit` before another native run. A focused Ponder run also exercises the connected Field Manual reload checks; it does not replace the previously recorded complete book/restart and coaster validation.
