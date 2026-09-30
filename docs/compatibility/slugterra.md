# Slugterra local-port integration

Target: `slugterra-1.3.4+1.21.1-unofficial.0.jar`, NeoForge 21.1.252,
GeckoLib 4.8.4. SHA-256:
`bda9677991003fb9dbbdfef9a5ceb32f6b60eeb5f5f7a27261287458e51755f9`.
The private artifact is supplied by the developer, never downloaded, bundled or
published by Magnetization. Its only declared mod ID is `slugterra`; bundled
addon registries also use `bajoterrafn`, `slugterra_dark`, and `eatslugslos`.

## Cavern ores

All 15 stone variants of each of iron, copper and gold participate in Ore
Dowsing Compass scanning/tuning, Magnetic Excavator extraction, and dropped-item
attraction. This follows Magnetization's existing vanilla ore gameplay policy,
including copper and gold. Nonmetallic cavern ores are excluded.

`compat.slugterraCompatEnabled` is the master switch.
`compat.slugterraOresEnabled` controls ore behavior independently. Both default
to true. Reload data after changing these common settings; runtime material
checks also honor the switches, including already-tuned compasses.

## Capsules and blasters

All 69 filled capsules from `slugterra:capsule` (46 base, 23 Dark Expansion),
empty capsules, the Slug Energy Core, Overpass Shooter AVG-1 and Doctor Black
Blaster respond to fields as dropped items. Capsule contents are never unpacked
or rewritten by this integration. Living protoform slugs are not tagged.

Both blasters fit the Electromagnet magnetization slot. South polarity attracts
nearby metal drops when held in either hand; north polarity follows the existing
tool behavior and repels. The original item components, including loaded slug
data, remain intact. `compat.slugterraEquipmentEnabled` defaults to true and
controls this package under the master switch. Reload data after changing it.
Bandoliers are not part of this equipment package.

Equipment GameTests exercise every listed item through both field polarities,
item serialization and player pickup, retaining custom names and nested slug
data. They also use the real emitter menu to stamp both blasters, test main-hand
and off-hand attraction, exclude nonmetal drops, and verify disabled behavior.

## Reproducing verification

```sh
bash scripts/run-gametest-gate.sh runSlugterraGameTestServer run-slugterra-gametest 360 \
  -PslugterraJar=/absolute/path/to/slugterra-1.3.4+1.21.1-unofficial.0.jar
bash scripts/run-gametest-gate.sh runSlugterraAbsentGameTestServer run-slugterra-absent-gametest 300
```

The present suite resolves all 45 ore IDs through the upstream tags, posts anvil
updates, scans placed ores with the compass scanner, checks the excavator cone,
checks both disabled switches, and powers an excavator to extract representative
iron/copper/gold blocks while leaving a nonmetal block outside the pull path.
The absent suite loads the mod and optional data without Slugterra and verifies
vanilla material support. Neither profile adds Slugterra to normal runtime or
published dependencies. The tests do not establish client rendering behavior.
