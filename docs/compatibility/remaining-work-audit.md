# Remaining compatibility verification — 1.21.1 NeoForge

This extends the [follow-up audit](followup-audit.md), preserving its artifact pins and earlier verified behavior. New checks use the same published artifacts, Minecraft 1.21.1, NeoForge 21.1.252, Java 21 and Sable 2.0.3. The requested verification is complete within the boundaries below. The Create startup cause remains unresolved, and a demonstrated Supplementaries pulley failure is separated into proposed adapter work.

## Create startup investigation

Recovered the original full debug log, which identifies Create's Registrate `onRegister` failure **before** the later `DefaultAttributes` / `swim_speed` rollback exception. The latter is secondary and does not establish a defect in Magnetization's entity attributes. The [first-failure excerpt](evidence/remaining-work/create-original-callback-failure.txt) records that order.

A diagnostic Java agent observed callback-map size and entries at Registrate `onRegister`, without changing registration behavior. Nineteen launches with Magnetization and ten without it reached the client connection screen, with no recurrence. The first nine additionally traced Create class initialization. All observed Create callback maps were empty when registration began. [Per-launch results and log hashes](evidence/remaining-work/startup-launch-results.json).

Source/bytecode inspection finds Registrate's shared callback multimap is an unsynchronized Guava `HashMultimap`. Initialization traces show Aeronautics entering Create's `AllBlocks` initializer on a parallel mod-construction worker while Create initializes its own registration classes. Concurrent registration is a plausible investigation lead, **not a demonstrated root cause**: the failing run did not capture the map state, and instrumentation can alter timing. No speculative production synchronization patch is included. Remaining action is to capture callback state and competing registrations during an actual recurrence. This is an unresolved startup problem, not an established Magnetization integration failure.

## Storage process restart

`scripts/run-storage-restart-audit.sh` starts two separate dedicated-server JVMs. The first constructs a native Sable ship, populates storage and cleanly saves/stops; the second opens that same world and ship UUID, operates the contents, and cleanly saves/stops again.

- Seven Sophisticated Storage variants: iron/gold/netherite chests and barrels plus the wooden chest control. Names, locks, contents and stack upgrades survive. A stack of 96 diamonds proves increased capacity; extraction after restart returns seven and leaves 89.
- Native smelting upgrade in the netherite chest continues cooking through ordinary block-entity ticks after restart. Output increases from zero to one through 500 subsequent ticks of ship movement; this tests a working upgrade rather than merely retaining an upgrade item.
- Six Iron Chests variants retain 31 emeralds; post-restart extraction returns seven and leaves 24. **Only iron, gold and copper are tagged magnetic.** Diamond, crystal and obsidian are inventory-preservation controls, not six claimed magnetic materials.
- Different process IDs and the same ship UUID are asserted by the fixture. [Creation](evidence/remaining-work/storage-restart-create.txt), [restart and operation](evidence/remaining-work/storage-restart-verify.txt).

This closes process-restart, Iron Chests inventory and a functional additional-upgrade gap. It does not claim every Sophisticated Storage upgrade is tested.

## Fresh regression matrix

Forty selected published-artifact profiles passed, including optional absence, Reborn, Modular Golems, Quark, Curios, Alex's Caves, aircraft, material/equipment tags, Twilight Forest and the enabled/absent Magnetics workaround profiles. [Task results, required-test counts and log hashes](evidence/remaining-work/regression-results.json).

Storage, bosses and Supplementaries use the separately expanded workflow fixtures. The default-off Magnetics profile retains its known upstream dedicated-server failure; it is not silently included as a pass. Fresh final checks passed 254 unit tests, 173 core GameTests, two optional-absence tests, two storage tests and two ordinary Supplementaries tests. Build, release-JAR and runtime-coupling checks passed ([final build and unit results](evidence/remaining-work/release-checks.json)). The separate two-test Supplementaries workflow reproducer has one required pulley failure, which remains an explicit gap. [Final task outcomes](evidence/remaining-work/final-gametest-results.json). Client-UI checks are recorded below.

## Native attacks and moving machinery

- Native lightning producer profile passed 13/13: nine registered Iron's Spells casts (Lightning Bolt, Chain Lightning, Lightning Lance, Ball Lightning, Electrocute, Ascension, Shockwave, Thunderstorm and Volt Strike), Scylla's native storm, spear goal and electric whip, plus the original registry/damage sweep. Actual world ticks deliver damage; assertions require the intended damage ID, the exact causing caster and an equipment LIRM stamp. [Producer evidence](evidence/remaining-work/native-lightning.txt). GameTests dispatch the normal datapack-sync lifecycle event used upstream to initialize spell schools. Thunderstorm receives an explicitly hostile target because its native area targeting treats the initial iron-golem control as friendly. The packaged but unregistered `ThunderStepSpell` class is not claimed as an available spell. Further spells/attacks outside these named producers are not covered.
- **Cannon verified:** native fuse/ticks produce a real cannonball near the moving/rotating ship's world position, projectile travel exceeds three blocks, and exactly one cannonball plus one gunpowder are consumed. Magnetic block count remains correct.
- **Continuous pulley workflow incomplete/incorrect on moving ships:** native extension consumes chain and creates `supplementaries:moving_pulley_block` states at tick 7. At tick 8, Sable's sublevel count changes from one to two and the payload states disappear from the original ship; the moved chest block entity is absent after 25 ticks. The ordinary-world fixture retains its 17 diamonds at the correct extended position. No magnetic field is applied by this fixture. This localizes the failure to the continuous-movement/ship-split boundary; it does not yet prove the exact upstream method responsible. The operation cannot be advertised as working just because `supplementaries:pulley_block` is a valid magnetic material.
- **Proposed separate work:** a Supplementaries continuous-pulley/Sable split adapter, including preservation of moving block-entity NBT, reattachment and retraction. Keep this substantial new machinery integration out of this release's material-tag claims. `scripts/run-gametest-gate.sh runSupplementariesWorkflowGameTestServer run-supplementaries-workflow-gametest 300` is an explicitly failing reproducer for that gap, with cannon and ground controls. Ordinary Supplementaries registry/placement coverage remains in its existing profile.

## Sustained multiplayer aircraft

All 15 tagged vehicles passed 400 ticks each with a real survival pilot and a real spectator observer. Native crafting consumes the registered recipe ingredients; native boiler inventory consumes coal during flight (the boiler-free gyrodyne is the control). At nominal WEAK strength, **200 N**, attraction and repulsion appear in both clients’ traces, with no server vehicle movement-rejection warning. The TCP **and Sable UDP** relays apply delay phases of 25/75/125/40 ms per direction plus ±5 ms jitter; both UDP sessions authenticated. TCP delivery preserves stream order; UDP jitter can reorder datagrams. [Per-vehicle recipe, fuel and motion results](evidence/remaining-work/aircraft-network-results.json), [server assertions](evidence/remaining-work/aircraft-nominal-pass.txt), [relay statistics](evidence/remaining-work/aircraft-latency.json), [source-log hashes](evidence/remaining-work/aircraft-log-hashes.json).

The first sustained run exposed excessive quadrocopter acceleration and vanilla movement rejection; [before-repair evidence](evidence/remaining-work/aircraft-nominal-failure.txt). That diagnostic run delayed only TCP, so it is not the complete network verification. The final run delays both transports. Earlier 9 N checks were weaker than nominal WEAK, not stronger. The repair limits additional magnetic acceleration using `compat.immersiveAircraftMagneticSpeedLimit`, default **2 blocks/tick**. Existing faster native flight is preserved; braking remains available. The server supplies the current value with each impulse, so config reload affects subsequent impulses. The changed payload uses protocol version 2 to reject incompatible decoders.

This is a five-minute controlled sustained-exposure test, with the source maintained three blocks from each vehicle. It is not a stationary-emitter flight course or a long-duration public-server stress test. Intermediate runs stalled some client entities during world streaming; a fresh temporary-world run recorded advancing entity ticks and loaded chunks for all 15. This does not establish the cause of those intermediate stalls. Unit tests separately cover bunched impulses, braking/reversal and preserving faster native flight.

Reproduce with `scripts/run-aircraft-network-audit.sh --ordinary`. The default invocation retains the earlier 180-tick diagnostic mode.

## Client interfaces

Pinned EMI **1.1.24+1.21.1+neoforge** (`5sIPA1To`, `emi`), Curios **9.5.1+1.21.1** (`yohfFbgD`, `curios`) and Patchouli **1.21.1-93-NEOFORGE** (`BIogJv2D`, `patchouli`) passed fresh native client interactions: manual item opening, actual page-spread navigation, server-backed charm-slot equip, all 18 EMI information-page IDs, search and native crafting display. [Interaction log](evidence/remaining-work/ui-interactions.txt) and rendered screenshots in that directory. Reproduce with `scripts/run-compatibility-ui-audit.sh`.

Visual inspection identified low-contrast EMI information text. Removing our forced gray style restores black text on EMI’s light background. Missing translation names for our supplied and generated material tags were also corrected. These checks exercise the named interfaces, not every screen of all three upstream mods.

## Experimental Magnetron policy

Physical left/right punches and slams remain **non-LIRM by default**. The separately requested experimental server option `compat.alexsCavesMagnetronLirmEnabled = false` can add LIRM after successful physical melee damage without changing its damage type. Native attack tests cover default/off, enabled, master disabled, LIRM disabled, config reload and invulnerable targets, plus another mob as a control. Exactly one eligible equipment piece is stamped when enabled; log petrification shares the existing LIRM rules. This is an optional gameplay policy, not a claim that upstream Magnetron melee is lightning.

## Outstanding work

1. **Create startup investigation:** capture callback state during a recurrence; 29 later successful launches do not establish a fix.
2. **Supplementaries pulley/Sable adapter, separate proposal:** preserve moving payload block entities through splitting, reattachment and retraction. The current reproducer remains a required failure; cannon operation passed.
3. **Other integrations outside this release:** active Create: Magnetics field projection, additional Modular Golems upgrades and broader machine/upgrade workflows remain proposed work, as listed in the original audit. Successful material classification does not verify those operations.
