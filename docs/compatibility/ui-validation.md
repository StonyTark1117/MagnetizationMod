# Native compatibility UI validation

This closes the client interaction gaps left by the earlier registration and equip checks. Reproduce each profile with `scripts/run-compatibility-ui-audit.sh MODE`, where MODE is `curios`, `jade`, `wthit`, `top`, `goggles`, `emi`, `jei`, `rei`, or `jer`. Runs use a fresh dedicated server, a connected native client and an isolated Xvfb display. They do not share a player world.

The opt-in fixture operates native inventory menus, registered key mappings, upstream recipe screens and upstream overlay controls. A render observer records text after transformation and scissor visibility checks; it also observes native item icons. It does not supply hit results, replace providers, or draw the expected overlay. Assertions and framebuffer captures are retained in [evidence/ui-validation](evidence/ui-validation/). Validate the saved coverage and hashes with:

```sh
python3 scripts/analyze-compatibility-ui-audit.py docs/compatibility/evidence/ui-validation \
  --output docs/compatibility/evidence/ui-validation/results.json
```

| Profile | Native behavior checked |
| --- | --- |
| Curios | Equip Field Compass, Magnetic Grapple and Repulsor Gun through actual charm/back/hands slots. With both hands empty, press/release the registered F9/F10 key mappings and observe the production client subscriber, network activation, equipped stack stamps, server-side loose-item motion and grapple player motion. The equipped compass produces an east-facing HUD reading, its registered needle property changes after yaw rotation, and F1 suppresses its HUD. |
| Jade | Aim at nine real blocks on an assembled moving/rotating Sable sublevel. Each shared field/machine line renders exactly once. Disable/restore Jade's actual provider controls, then disable/restore the Magnetization master switch, checking disappearance and restoration. |
| WTHIT | The same nine moving-ship readouts and line multiplicity. Toggle the native overlay key and the Magnetization master switch off/on. |
| TOP | The same nine moving-ship readouts and line multiplicity. Use TOP's native visibility-toggle key and toggle the Magnetization master switch off/on. |
| Create goggles | The same nine moving-ship readouts and line multiplicity. Remove/restore goggles through the native inventory slot and check the lines disappear/return. |
| EMI, JEI, REI | Open all 18 registered information topics in each upstream viewer's real recipe screen. Reach all description lines through native page buttons or scrolling; capture each segment. REI navigates overlapping usage results through its native recipe selector. |
| JER | Open all 28 catalog sources in JER's actual JEI World Gen category, including separate Overworld and End Helium-3 geodes. Inspect positive distributions, native displayed drop icons and registered drop minimum/maximum/chance values. Hover all 31 drop entries and verify/capture the actual native tooltip text. |

The moving-ship cases are Permanent Magnet, Kinetic Electromagnet, Gas Exciter, Gas Vent, Air Separator, Magnetostrictive Sensor, Barkhausen Generator, Gyrostabilizer and Induction Pad. Controlled Sable transforms and a following camera keep native targets reachable. These cases establish targeting/readout/switch behavior; they do not establish every machine production workflow or arbitrary ship dynamics. Earlier golem captures and Curios restriction/logout GameTests remain complementary evidence.

Supporting gates passed: 255 unit tests, all three isolated Curios GameTests (usable slots, master/cooldown enforcement, active-grapple logout cleanup), both optional-mod absence GameTests, `verifyReleaseJar`, and `verifyNoRuntimeModpackCoupling`. Logs and the unit result count are saved under [evidence/ui-validation/checks](evidence/ui-validation/checks/). The disposable UI worlds use peaceful difficulty so mobs cannot interrupt long viewer sessions. Fresh server gates use `scripts/run-gametest-gate.sh`, which preserves the passing summary and terminates only its own disposable runtime process group.

Presentation and switch defects found during these checks were repaired: compass HUD bearing calculation reversed east/west, and Jade/WTHIT/TOP master switches were checked only at registration. Their providers now honor disabled master switches while rendering. WTHIT's independent main-level block traversal also missed Sable blocks; an optional adapter now passes real ship-aware hits into its native nearest-hit selection. Air Separator goggles previously inherited only Create kinetic statistics; its concrete goggles method now appends the shared machine readout. JEI/REI descriptions now use the native viewer text color rather than low-contrast forced gray. The renamed `jadeRegistersEachSharedBlockProviderOnce` unit test is explicitly a registration guard; moving-ship deduplication is established by the native render assertions.

Pins: Minecraft 1.21.1, NeoForge 21.1.252, Java 21, Sable 2.0.5, Create development artifact 6.0.11-312 (earlier diagnostic runs used 6.0.11-295); Curios 9.5.1+1.21.1, Patchouli 1.21.1-93-NEOFORGE, EMI 1.1.24+1.21.1+neoforge, JEI 19.27.0.336, REI 16.0.799, Jade 15.10.6+neoforge, WTHIT neo-12.10.2 with badpackets neo-0.8.2, TOP 1.21_neo-12.0.8 and JER Modrinth version `TgNFki8j`. Exact loaded-mod descriptors remain in the logs; profile dependency definitions are in `build.gradle`.
