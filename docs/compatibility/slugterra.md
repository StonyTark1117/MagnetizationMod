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

## Experimental trajectory deflection

`compat.slugterraDeflectionEnabled` defaults to **true** after the live audit.
Existing explicit `false` settings are preserved. The feature remains experimental. When enabled under
the master switch, these four flying entity types respond to fields:

- `slugterra:armashelt_velocimorph`
- `slugterra_dark:dark_armashelt_velocimorph`
- `slugterra:rammstone_velocimorph`
- `slugterra_dark:dark_rammstone_velocimorph`

South fields bend their heading toward the source; north fields bend it away.
`compat.slugterraDeflectionMaxTurn` defaults to 6 degrees per slug per game tick
(range 0.1–30). All fields share the same turn budget, so stacking machines
cannot multiply it. A field changes direction without changing current speed.
Parallel forces do not invent a turn axis or stop/reverse the slug. Slugterra's
own drag and speed rules continue afterward, including its different vertical
and horizontal living-entity drag. Native abilities can compete with steering.

Armashelt uses vanilla arrow motion. Rammstone reconstructs motion from its
stored `v_x/v_y/v_z` values; the adapter rotates those too, retaining their
magnitude. Stationary, grounded, embedded, transformed, hit-animating and active
Rammstone special-ability states are excluded. The original stored slug data,
owner, damage, combat level and return-to-protoform logic remain upstream-owned.
Other slugs and living protoforms are excluded from this experiment.

GameTests cover all four entities and both poles, stacked-field caps, unchanged
speed at application, native flight ticks, entity save/load, native block-hit
handlers and recovery with the same species, owner and custom name. Negative
checks cover default-on, explicit opt-out, master-off, stationary, embedded,
hit-animation and unselected-slug behavior. Additional GameTests exercise water,
lava, gravity and native impact/return states at levels 1 and 20.

The live audit passed all 64 cases on a dedicated server with two real clients:
four selected entities, both poles, levels 1/10/15/20 and native abilities on/off.
Both clients observed and rendered every case. Armashelt retained its native
swerve/steering; high-level Rammstone fired two fists and recovered its protoform.
The final local position comparison had a maximum difference of 1.64 blocks
within the documented latency/interpolation window. See
[measured results](evidence/slugterra/live-final/analysis.json),
[rendered flight recording](evidence/slugterra/live-final/high-level-flight.mp4)
and the HUD/live harness details below. Other port versions, WAN latency and
arbitrary modpack combinations remain outside this audit.

## WTHIT magnetic status on slugs

WTHIT remains optional. When installed on both the server and client, hovering
an affected living slug shows WTHIT's native Magnetized level plus our remaining
duration and, at level III or above, a horizontal-movement pinning explanation.
The additional lines use WTHIT's server-data channel; vanilla does not send an
observing client every mob's full effect list. Data is tied to the hovered
entity UUID, and expired data is suppressed. Infinite effects display infinity.

Trajectory deflection does not apply a potion effect or leave a timed magnetic
status on the slug. These HUD lines appear only when an actual `magnetized`
effect exists, for example from a potion or another effect source. Normal and
Dark Rammstone were verified on two real clients with finite and infinite
durations, pinning, expiry and explicit removal. The extra status lines vanish
when the effect ends. See [HUD evidence](evidence/slugterra/live-final/).

## Live verification harness

The audit source set is separate from the release JAR. The private Slugterra
artifact and WTHIT/Bad Packets are included only in the explicit audit profile.
Two real clients connect to a loopback dedicated server and render the native
Slugterra models, with automated camera tracking and position logs.

```sh
python3 scripts/run-slugterra-live-audit.py --slugterra-jar /absolute/path/to/slugterra.jar
# In another terminal, after the script reports readiness:
printf flight > build/slugterra-audit/server/control.txt
python3 scripts/analyze-slugterra-live-audit.py build/slugterra-audit
# HUD fixtures: hud, hud_dark, hud_infinite, hud_clear
printf hud > build/slugterra-audit/server/control.txt
# Stop all audit processes and private displays:
touch build/slugterra-audit/stop
```

The server world is disposable and stored under `/tmp`; older runtimes are
preserved. Display numbers are recorded in `build/slugterra-audit/processes.json`.
The fixture tests four flight entities, both poles, combat levels 1/10/15/20,
and native abilities enabled/disabled (64 cases). It checks magnetic speed and
turn limits, high-level Armashelt steering, Rammstone's two fists and subsequent
protoform recovery. The analyzer requires all 64 entities to be observed and
visible on both clients, and reports latency-window position differences.
This is local multiplayer verification, not a WAN latency or every-modpack test.

A failed preliminary network gate is retained in
[autosave-stall evidence](evidence/slugterra/live-autosave-stall/README.txt):
Sable's synchronous save stalled the server for 3.7 seconds while clients kept
predicting arrow motion. Moving the disposable test world to RAM isolates that
storage stall; this integration does not change Sable saving or vanilla behavior
under a stalled server. The experimental label remains appropriate.

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
published dependencies. The headless GameTests are complemented by the separate two-client live audit above.
