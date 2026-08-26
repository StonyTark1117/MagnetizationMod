# Magnetization 1.4.3

Magnetization 1.4.3 is a compatibility and mounted-Railgun hotfix for Minecraft 1.21.1. It keeps every integration tied to the individual addon, never a modpack.

## Fixed

- Mounted Railguns now explicitly cover both reported failure modes: they fire external staged payloads while installed on Sable ships, and they ignore ferromagnetic blocks belonging to their own host craft instead of launching themselves.
- Optional dependency metadata now accepts the behaviorally verified releases used by supported packs: AeroPortals 1.1.2, Create: Coasters Simulated 0.1, and JEI 19.27.0.336.
- AeroPortals 1.1.2 retains safe magnetic-state cache invalidation and ship reconstruction without resolving APIs that only exist in newer releases. Railgun Remote and Simulated swivel remapping remain enabled automatically on AeroPortals versions that expose the required plot geometry.

## Compatibility verification

- Added isolated behavioral profiles for both the pack-pinned and current AeroPortals and Coasters Simulated releases.
- Expanded real-addon coverage for Coasters: Magnetized, Coasters: Engineered, Create: Coasters Extras, and CBC Aeronautics Missiles.
- Added isolated private-X-server client boots for Engineered with Jade, Extras, the complete missile stack, and JEI/JER. The JER gate requires all 28 synchronized resource charts to register exactly once.
- Added exact recommended-pack launch and configuration-migration fixtures while enforcing that pack names, slugs, and project IDs cannot enter the shipped runtime JAR.

## Configuration

All 1.4.2 compatibility switches and defaults are unchanged. Existing configurations migrate by preserving every prior key and adding only missing defaults.
