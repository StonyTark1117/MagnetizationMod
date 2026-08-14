# Magnetization 1.4.1 release acceptance evidence

Captured on 2026-08-14 from disposable singleplayer clients rendered with Mesa llvmpipe on isolated Xvfb displays. The screenshots are committed so the release audit does not depend on an ignored local playtest directory.

## Visual acceptance

| Surface | Evidence | Acceptance result |
| --- | --- | --- |
| Expanded Tokamak GUI | [tokamak-gui-5x5.png](tokamak-gui-5x5.png) | Formed GUI reports a 5x5 reactor with 16 coils and 9 cores. |
| Expanded Tokamak goggles | [tokamak-goggles-5x5.png](tokamak-goggles-5x5.png) | Preview reports a valid 5x5 ring, solid 3x3 core interior, and 3x performance multiplier. |
| Golem renderer presence | [golem-renderers.png](golem-renderers.png) | Presence-only capture. It exposed the shipped MR Fluid Golem regression: the soft golem rendered as a static gray Iron Golem palette swap. It is not animation or state-transition acceptance evidence. |
| Active oxide lineup | [golems_active.png](../../../playtest/baselines/lab/golems_active.png) | Oxidized Magnetite, heated Pyrrhotite, dampening Hematite, and field-capturing Titanomagnetite render upright with four distinct mineral silhouettes and state palettes. |
| Oxide Create + WTHIT HUD | [Magnetite](../../../playtest/baselines/lab/golem_magnetite_hud.png), [Pyrrhotite](../../../playtest/baselines/lab/golem_pyrrhotite_hud.png), [Hematite](../../../playtest/baselines/lab/golem_hematite_hud.png), [Titanomagnetite](../../../playtest/baselines/lab/golem_titanomagnetite_hud.png) | Each entity is actually targeted in-world. Both overlays show live material state, field behavior, health, and the correct non-iron repair material. |
| Gallium Golem | [Create + WTHIT](../../../playtest/baselines/lab/golem_gallium_hud.png) | The intentionally slumped cast-metal body is visibly distinct from an Iron Golem, and both overlays report thermal state, health, and Gallium Ingot repair. |
| MR Fluid state transition | [soft](../../../playtest/baselines/lab/golem_mr_fluid_soft_hud.png), [hardened](../../../playtest/baselines/lab/golem_mr_fluid_hardened_hud.png), [transition contact sheet](../../../playtest/baselines/lab/golem_mr_fluid_soft_hud-transition.png) | A field-isolated fixture proves the animated soft surface and 30% mitigation, then field hardening proves the rigid surface, 92% mitigation, and knockback immunity. The contact sheet records soft to hard to soft rather than accepting one still. |
| Active Gas Exciter Create + WTHIT HUD | [gas_exciter_hud.png](../../../playtest/baselines/lab/gas_exciter_hud.png) | The shared machine contract reports its actual gas, FE, redstone gate, and active/idle state through both installed surfaces. |
| Jade HUD | [Titanomagnetite](../../../playtest/baselines/jade/golem_titanomagnetite_hud.png), [Gallium](../../../playtest/baselines/jade/golem_gallium_hud.png), [MR soft](../../../playtest/baselines/jade/golem_mr_fluid_soft_hud.png), [MR hardened](../../../playtest/baselines/jade/golem_mr_fluid_hardened_hud.png), [Gas Exciter](../../../playtest/baselines/jade/gas_exciter_hud.png) | Isolated Jade client entered the world and targeted every declared entity state plus the active machine. |
| The One Probe HUD | [Titanomagnetite](../../../playtest/baselines/top/golem_titanomagnetite_hud.png), [Gallium](../../../playtest/baselines/top/golem_gallium_hud.png), [MR soft](../../../playtest/baselines/top/golem_mr_fluid_soft_hud.png), [MR hardened](../../../playtest/baselines/top/golem_mr_fluid_hardened_hud.png), [Gas Exciter](../../../playtest/baselines/top/gas_exciter_hud.png) | Isolated TOP client entered the world and proved the same entity and machine contract. |
| Real machine screens | [Electrolyzer](../../../playtest/baselines/lab/electrolyzer-gui.png), [Tokamak](../../../playtest/baselines/lab/tokamak-gui.png), [Fusion](../../../playtest/baselines/lab/fusion-gui.png), [Railgun](../../../playtest/baselines/lab/railgun-gui.png), [Dipole](../../../playtest/baselines/lab/dipoles-gui.png), [Air Separator](../../../playtest/baselines/lab/air_separator-gui.png) | Automation requires a client screen-open marker after interaction; a world screenshot can no longer stand in for a missing GUI. Active fixtures report actual fuel, coolant, FE, formation, and rail state. |
| Survival Railgun | [railgun.png](../../../playtest/baselines/survival/railgun.png) | The survival fixture contains a powered eight-block rail and proves paired manual mode, auto-assembly, full FE, and a held target. |
| Engine effects | [thruster-exhaust-coolant-mist.png](thruster-exhaust-coolant-mist.png) | The staged Sable craft shows the Micro, MHD, Ion, and Fusion exhaust styles; the cooled Fusion panel emits the pale cloud/splash mist sheath. |
| Gas Detector HUD | [gas-detector-hud.png](gas-detector-hud.png) | Active scan identifies Argon and renders state, heading, range, exposure, dose, and SAFE verdict. |
| Tokamak Ponder | [ponder-tokamak.png](ponder-tokamak.png) | Solid-core 5x5 layout renders with correctly localized master/core scaling guidance. |
| Railgun Ponder | [ponder-railgun.png](ponder-railgun.png) | Paired layout renders with localized minimum-length and auto-assembly guidance. |
| Steam 'n' Rails Ponder | [ponder-steam-rails.png](ponder-steam-rails.png) | Optional scene opens in a client with `railways` and explains linked-train/Structural Inducer behavior. |
| Copycats+ Ponder | [ponder-copycats.png](ponder-copycats.png) | Optional scene opens in a client with `copycats` and explains copied-material susceptibility and goggles output. |

The Railgun visual pass initially exposed `magnetization.ponder.railgun_pair.text_2` as a raw key. All 33 Ponder instruction resources were corrected to Ponder's one-based numbering, the synchronization test now enforces that convention, and the four committed Ponder captures were taken after the fix/reload.

### MR Fluid Golem acceptance correction

The original review incorrectly accepted one static group screenshot as proof that the MR Fluid Golem retained its material identity. The `v1.4.0` release JAR contains only the static soft/hardened texture swap shown in that capture; it does not contain a moving fluid surface. The replacement evidence above now satisfies the corrected criterion: controlled field-free soft-state frames, a field-hardening transition, and a field-removal/softening transition. A single still image or a successful client launch remains insufficient.

## Automated acceptance

- `releaseMatrixGate -PmagSmokeSeconds=5`: passed all 22 isolated profiles on 2026-08-14 in 12m10s, including the minimal release profile, absent-mod behavior, Coasters Simulated: Track Styles, Steam 'n' Rails, Copycats+, TFMG, both Ore Excavation integrations, and Curios.
- Fresh minimal server: generated isolated `eula.txt` and deterministic `server.properties`, reached `Done`, accepted `stop`, passed strict log review, and left no matching JVM alive.
- Core GameTests: the final candidate passed 166/166 in 12.59s from a fresh tmpfs-backed world, including the mounted-Railgun payload launch, hardened-MR-fluid chunk-reload, and magnetized-horse-armor regressions. The focused restored-feature regression namespace passed 4/4 in 364.0ms. No GameTest JVM or standard server port remained after the gate.
- Optional clients: isolated Jade and The One Probe profiles entered singleplayer and produced the ten committed HUD captures above. The full lab client also reached the world with Sodium and the pinned Iris build; the development wrapper corrects that Iris jar's exclusive `1.21.1` metadata upper bound without shipping Iris or modifying the release JAR.
- Immutable visual gate: all 51 declared PNGs are present and match `playtest/baselines/SHA256SUMS`; an ordinary survival Railgun comparison passed at RMSE `0.00949255` against the `0.08` limit without recording or replacing the baseline.
- Release artifact: `verifyReleaseJar` confirmed required metadata and rejected build-cache/OS-file leakage.

SHA-256 hashes are recorded in [SHA256SUMS](SHA256SUMS).
