# Magnetization 1.4.6

For Minecraft 1.21.1 and NeoForge. Install on both client and server with the required Create, Sable, Create: Aeronautics, Create Propulsion: Simulated, and TerraBlender dependencies. The complete stack needs NeoForge 21.1.219 or newer, below 22.0.

## Highlights

- Add three magnetic shaft tiers that relay an independently driven source's signed RPM and original Create stress capacity. Each tier has its own configurable range, with drive handoff and conflict handling.
- Add anchor-linked Magnetic Switch outputs for target presence, settling, loss, and analog distance. Create goggles now show server-calculated ship forces, turning torque, sources, and docking reasons.
- Expand the Ponder catalog to 32 scenes and 125 instructions, including magnetic basics, the Excavator, repulsor transport, MR Fluid bridges, field control, equipment, docking, and magnetic shafts.
- Improve field, train, machine, and conductive-fluid performance. Fix Cosmic Compass tracking of active AE2 meteorites and piloted Immersive Aircraft magnetic force delivery.
- Add material-aware compatibility for Extra Golems Reborn, Modular Golems, and Quark, along with broad corrections and native compatibility coverage for optional mods. The Create: Magnetics 0.0.4-alpha dedicated-server workaround is opt-in and defaults off.

## Verification and known limits

The release audit passed 293 unit tests, 176 core and 13 regression GameTests, seven magnetic-engineering GameTests, the minimal dedicated-server smoke test, release-JAR verification, and CI source/JAR checks. The full optional-mod matrix passed 40 profiles; its Ponder client profile intermittently hit a Create/Registrate development startup exception. Concurrent upstream Registrate access was observed with Magnetization absent, but the exact corrupt mutation was not captured. An isolated Ponder recheck passed. This is tracked as a nonblocking dependency issue, not a claimed production fix.

The full-pack stress study still records a conductive-fluid hitch, and the Supplementaries continuous-pulley payload workflow remains unresolved. See the [release audit](https://github.com/StonyTark1117/MagnetizationMod/blob/v1.4.6/docs/release-evidence/1.4.6/README.md), [compatibility audit](https://github.com/StonyTark1117/MagnetizationMod/blob/v1.4.6/docs/compatibility/remaining-work-audit.md), and [full changelog](https://github.com/StonyTark1117/MagnetizationMod/blob/v1.4.6/CHANGELOG.md) for scope and evidence.
