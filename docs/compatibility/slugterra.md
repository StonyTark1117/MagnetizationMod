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

## Mechanical mounts

`bajoterrafn:burro_mecha`, `bajoterrafn:perro_mecha` and `bajoterrafn:toro_mecha`
respond intrinsically to magnetic fields, including tractor beams, anchors and
repulsors. Ridden mounts use the same force as empty mounts. The server changes
the mount's velocity and vanilla tracking broadcasts it; no extra rider-force
forwarding or client prediction is introduced. An unbound anchor also emits while a supported mount is in range, allowing
mount docking without a Sable ship. Existing ship bindings keep priority; mounts
receive the entity field, without introducing a persistent entity binding.

- `compat.slugterraMountsEnabled`: true by default, under the master switch.
- `compat.slugterraMountSusceptibility`: 1.0 by default; 0 disables intrinsic response.
- `compat.slugterraMountMaxImpulse`: 0.25 blocks/tick per field, bounding strong impulses.

The mount GameTest covers all three machines, all three mounts, ridden and
unridden native travel, armored passenger attachment and outgoing server motion
packets. This verifies server behavior and packet production, not rendered
motion on two live clients. Ordinary equipment/status contributions and the
shared unmoveable-by-magnets exclusion remain part of the standard field rules.

## Tazerling remnant magnetism

A fresh `slugterra:electric_shock` application stamps one eligible equipped metal
armor/tool item using existing LIRM rules. Base Tazerling, its bolt, protoform
shock and Dark Tazerling share this effect. Effect refreshes and Dark Tazerling's
amplification do not stamp more gear. Removing and immediately reapplying the
effect is subject to a per-target cooldown that persists with the entity.
Generic damage and unrelated effects do not trigger the bridge. Commands that
apply the same electric-shock effect intentionally follow the same rules.

`compat.slugterraElectricEnabled` defaults to true, under the master switch and
`lightning.lirmEnabled`. `compat.slugterraElectricCooldown` defaults to 100 ticks.
Stamps use the existing 24,000-tick (20-minute) LIRM decay, preserve permanent
magnetization, and do not petrify nearby logs. Players retain normal inventory
expiration; affected nonplayer equipment is checked every 100 entity ticks,
including when compatibility has been disabled after the strike.

GameTests invoke native hit handlers for the bolt, normal Tazerling and Dark
Tazerling, verify one temporary stamp, refresh/cooldown suppression, equipment
expiration and unchanged nearby logs. Separate checks cover both integration
switches, the global LIRM switch, generic damage and unrelated effects.

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
