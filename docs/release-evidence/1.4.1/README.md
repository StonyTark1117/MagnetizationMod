# Magnetization 1.4.1 release acceptance evidence

Captured on 2026-08-14 from disposable singleplayer clients rendered with Mesa llvmpipe on isolated Xvfb displays. The screenshots are committed so the release audit does not depend on an ignored local playtest directory.

## Visual acceptance

| Surface | Evidence | Acceptance result |
| --- | --- | --- |
| Expanded Tokamak GUI | [tokamak-gui-5x5.png](tokamak-gui-5x5.png) | Formed GUI reports a 5x5 reactor with 16 coils and 9 cores. |
| Expanded Tokamak goggles | [tokamak-goggles-5x5.png](tokamak-goggles-5x5.png) | Preview reports a valid 5x5 ring, solid 3x3 core interior, and 3x performance multiplier. |
| Golem renderers | [golem-renderers.png](golem-renderers.png) | Gallium, MR Fluid, Magnetite, Pyrrhotite, Hematite, and Titanomagnetite render together with distinct models/materials. |
| Engine effects | [thruster-exhaust-coolant-mist.png](thruster-exhaust-coolant-mist.png) | The staged Sable craft shows the Micro, MHD, Ion, and Fusion exhaust styles; the cooled Fusion panel emits the pale cloud/splash mist sheath. |
| Gas Detector HUD | [gas-detector-hud.png](gas-detector-hud.png) | Active scan identifies Argon and renders state, heading, range, exposure, dose, and SAFE verdict. |
| Tokamak Ponder | [ponder-tokamak.png](ponder-tokamak.png) | Solid-core 5x5 layout renders with correctly localized master/core scaling guidance. |
| Railgun Ponder | [ponder-railgun.png](ponder-railgun.png) | Paired layout renders with localized minimum-length and auto-assembly guidance. |
| Steam 'n' Rails Ponder | [ponder-steam-rails.png](ponder-steam-rails.png) | Optional scene opens in a client with `railways` and explains linked-train/Structural Inducer behavior. |
| Copycats+ Ponder | [ponder-copycats.png](ponder-copycats.png) | Optional scene opens in a client with `copycats` and explains copied-material susceptibility and goggles output. |

The Railgun visual pass initially exposed `magnetization.ponder.railgun_pair.text_2` as a raw key. All 33 Ponder instruction resources were corrected to Ponder's one-based numbering, the synchronization test now enforces that convention, and the four committed Ponder captures were taken after the fix/reload.

## Automated acceptance

- `releaseMatrixGate -PmagSmokeSeconds=5`: passed all 22 isolated profiles, including the minimal release profile, absent-mod behavior, Coasters Simulated: Track Styles, Steam 'n' Rails, Copycats+, TFMG, both Ore Excavation integrations, and Curios.
- Fresh minimal server: generated isolated `eula.txt` and deterministic `server.properties`, reached `Done`, accepted `stop`, passed strict log review, and left no matching JVM alive.
- Core GameTests: 160/160 passed in five consecutive stress reruns plus the final release matrix run. The high-speed Fusion Thruster and Railgun fixtures now observe bodies before legitimate GameTest-region removal while still rejecting premature cleanup.
- Optional clients: isolated Jade, The One Probe, REI, EMI, and combined Ponder compatibility clients rendered their main menus with exact required-mod checks. The combined Ponder client registered 16 core and 2 optional scenes; both optional scenes were then opened in singleplayer for the captures above.
- Release artifact: `verifyReleaseJar` confirmed required metadata and rejected build-cache/OS-file leakage.

SHA-256 hashes are recorded in [SHA256SUMS](SHA256SUMS).
