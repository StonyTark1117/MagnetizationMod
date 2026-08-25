# Magnetization 1.4.2

Magnetization 1.4.2 expands optional interoperability across the Create: Coasters and Create: Aeronautics addon ecosystems. Every bridge targets the individual mod, so it works in custom packs and does not depend on either modpack that motivated this audit.

## Coasters

- **Coasters: Magnetized 1.1.0**: live Magnetization fields can power magnetic anchors as an alternative to redstone. The addon's mode, force, speed limit, native redstone behavior, and powered models remain authoritative. Redstone plus field power applies acceleration only once.
- Field-powered visual state is synchronized from the server and refreshed after login or dimension changes.
- **Coasters: Engineered 1.0.1**: added a condition-gated Linear Motor recipe using Magnetic Alloy Plates. The addon's native bidirectional NeoForge FE contract remains unchanged.
- **Create: Coasters Extras 1.2**: verified that functional and decorative tracks preserve the parent Coasters Simulated cart contract, including magnetic field response and Structural Inducer recognition.

## Missiles

- **CBC Aeronautics Missiles 0.1.0**: EMP Flux Charge pulses now permanently disable guidance computers on intersecting Sable missiles.
- EMP integration deliberately leaves propulsion, payloads, fuzes, and ordinary Sable physics untouched.

## Configuration and verification

- Added default-on master controls for Coasters: Magnetized, Coasters: Engineered, and CBC Aeronautics Missiles, plus independent field-power, recipe, and EMP-guidance switches.
- Added optional dependency metadata for all four addons.
- Added Field Manual, config-screen, README, and release documentation.
- Added isolated published-mod GameTest profiles, a Coasters: Magnetized client-mixin smoke profile, absent-mod coverage, pinned development audit fixtures, and a release-JAR guard that prevents modpack-specific runtime coupling.
