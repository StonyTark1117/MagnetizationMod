# Material-control Ponder scenes

Three tutorials explain the existing material mechanics on Minecraft 1.21.1 / NeoForge.

| Scene | Open Ponder from | Demonstration |
| --- | --- | --- |
| `mr_fluid_bridge` | MR Fluid bucket | Pour over a temporary floor; harden with redstone; remove the floor; add a magnet; remove redstone while the field keeps the bridge rigid; remove the magnet to restore the source and downward flow. |
| `field_strength_control` | Hematite Block or Permanent Magnet | A MEDIUM Samarium-Cobalt magnet becomes STRONG with one or two aligned neighbors and EXTREME with three. Remove the neighbors, then place two face-adjacent Hematite blocks: MEDIUM → WEAK → NONE. |
| `magnetizing_equipment` | Electromagnet or Kinetic Electromagnet | Insert an Iron Helmet in the magnetizing slot, use N/S/Clear, and illustrate the equipped wearer's response to a fixed NORTH field. Clear removes the stamp and its susceptibility bonus; the metal armor remains susceptible and the unstamped wearer defaults to NORTH. |

The Halbach example assumes its default enabled server setting. The bonus caps at two tiers, and EXTREME is the upper tier. Dampeners touch the emitter itself; a chain touching only other dampeners does not count.

The equipment scene invokes the shipping `EmitterMenu.clickMenuButton` handler with a separate tutorial inventory. Its armor-stand movement and arrows illustrate force direction. They do not execute server field physics inside Ponder or change the viewer's inventory. The scene's Clear action removes the real item component rather than merely changing a label.

The fluid scene scripts the visible source/flow/hardened states. Independent dedicated-server GameTests verify actual fluid propagation, redstone and field activation, walkable collision without supporting blocks, reversion, and absence of duplicated fluid sources. Likewise, the strength test ticks the real permanent-magnet emitter through all six aligned-neighbor counts and stacked suppression. The equipment test uses the real menu buttons and `FieldApplicator` to verify repulsion, attraction, and a weaker but still nonzero response after Clear.

## Validation

The focused catalog suite passes 4 tests. The regression profile passes all 7 required GameTests, including these 3 new cases:

| Test | Behavioral assertions |
| --- | --- |
| `bridgeRequiresBothActivationsRemoved` | Real source propagation; redstone hardening; walkable collision after removing the floor; field remains active after redstone removal; removing both restores the original source and downward flow without extra sources. |
| `nativeEmitterFollowsEveryStrengthStage` | Actual emitter ticks: MEDIUM baseline; one/two neighbors → STRONG; three through six → EXTREME; removal → MEDIUM; one/two Hematite blocks → WEAK/NONE. |
| `nativeButtonsReverseForceAndClearKeepsMetalResponse` | Real slot acceptance and N/S/Clear dispatch; stamped components; actual repulsion/attraction; Clear removes the bonus but retains nonzero metal-armor response. |


Native playback uses the existing pinned lifecycle/presentation profile, at effective GUI scales 2 and 3. All 15 instructions were visually reviewed at both scales (44 captures in total); all five advertised item targets compiled to their expected scene in the scale-2 run. Each instruction checks the played world or equipped item before capturing its rendered frame; first/final stages and complete normal playback are also required. Evidence and final results are recorded in [the evidence directory](evidence/material-control-ponder/).

Reproduce with Java 21:

```sh
./gradlew test --tests '*PonderSceneCatalogTest'
./gradlew smokeRegressionGameTest
MAGNETIZATION_AUDIT_SCENE=mr_fluid_bridge,field_strength_control,magnetizing_equipment \
  MAGNETIZATION_AUDIT_GUI_SCALE=3 bash scripts/run-lifecycle-presentation-audit.sh
# Archive build/validation-audit before repeating at GUI scale 2.
```

The focused native run covers these three scenes and the connected reload checks. It does not rerun the entire manual or every other Ponder scene. Captures demonstrate the default configuration; they are not a promise that optional server settings leave the taught defaults unchanged.
