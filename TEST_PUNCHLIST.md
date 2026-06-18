# Magnetization — In-World Test Punchlist

**Generated:** 2026-06-15 · covers the 20 open `Test:` tasks (#77–85, #90–93, #96–97, #99, #103–104, #109, #112)

## How to use this
Each entry has: **Obtain → Setup → Trigger → Expect → Config**, then a **Result** line for you to fill in.
Work top to bottom, mark each `PASS` / `FAIL` / `PARTIAL`, and jot what you actually saw. Send the whole
file back (or just the Result lines) and I'll act on the failures.

> ⚠️ **Two honesty caveats** — this doc was assembled from a *code read*, not a play session:
> 1. **Recipe specifics** marked _(verify in JEI/EMI)_ were either datagen-derived or flagged "inferred/not found"
>    by the reader. Trust the in-game recipe book over this doc where they disagree — and tell me if one is missing.
> 2. **Config key names / defaults** are as found in source, but confirm exact spelling in the in-game config
>    screen (Mods → Magnetization → Config) if you go to tune one. Behavioral cadence of every knob is
>    registration-verified only; you are the first real behavioral test.

> 🧪 **GameTest harness note (2026-06-17):** the headless `runGameTestServer` runs every test in a batch
> *concurrently in one shared ServerLevel*. With Sable + the full modpack loaded, more than ~1 physics/entity
> test at once overloads the tick loop and the slower ones starve (the runner spin-logs "Running test batch"
> and never finishes). **Run the heavy tests one at a time**, e.g. via a keep-list toggle. Separately, a
> *redstone-powered gallium fluid cell* placed in a test spins the batch runner indefinitely (a per-tick
> fluid/redstone loop), which is why the gallium Lorentz tests (#103/#104) are synchronous wiring checks
> rather than live entity-push tests. Every ✅ below was confirmed by an actual single-test run that printed
> "All N required tests passed :)".

---

## #77 — Lenz Braking (eddy-current drag)  ⟶ *not a block; passive ship effect*  — ✅ DRAG VERIFIED ON A REAL SHIP (GameTest)
> **Fixed + machine-verified.** Two bugs were found and fixed:
> 1. Scan only reached **3 blocks below** the hull (and 1 block elsewhere) — a ship flying *beside* or *under* a
>    conductor induced nothing. Now the scan reaches `CONDUCTOR_REACH` (3) blocks past **every** face, so a wall,
>    ceiling, or floor of conductor all brake the ship. (Per your note: ships fly, so braking can't be below-only.)
> 2. The end-to-end path is now proven by `lenzBrakesFallingShipBesideCopperWall`: it assembles two real Sable
>    iron-block ships, drops both with the same downward velocity, and the one beside a copper wall falls
>    **measurably slower** (live `LevelTickEvent` handler, real physics-body velocity read). PASSED.
> Still worth your eyes in-world: the *feel*/strength of the slowdown at real ship sizes and speeds.
- **Obtain:** nothing to craft — this is a physics effect on Sable ships.
- **Setup:** build a Sable airship (baseline susceptibility makes any ship eligible; ferromagnetic blocks make it stronger). Put conductive blocks **near** it — floor, wall, OR ceiling within ~3 blocks: copper/aluminium/brass/bronze/tin/silver/lead blocks, waxed/cut copper, lightning rods, or modded `#c:storage_blocks/*` of those metals.
- **Trigger:** fly the ship fast (> ~0.04 blocks/tick) close past the conductor (within 3 blocks of any face).
- **Expect:** ship visibly slows / glides instead of zipping; drag scales with conductor count up to a cap (8 blocks); brakes whether the conductor is below, beside, or above. No nearby conductor → no braking.
- **Config:** `lenzBrakingEnabled` (physics, master on/off — **new**), `lenzBrakingStrength` (physics), `lenzBrakingTicks` (performance), `lenzBaseDrag`/`lenzMaxDrag`/`lenzMinSpeed`/`lenzConductorCap` (mechanics).
- **Result:** ✅ Confirmed working in-game (you). Added `lenzBrakingEnabled` (default true) to disable the effect entirely — the handler skips its per-tick scan when off.

## #78 — Induction Pad  ⟶ `magnetization:induction_pad`  — ⛔ NOT BEHAVIORALLY TESTABLE IN THIS PACK
> The pad charges items exposing the FE *item* capability. The mod ships **zero** FE items, and the
> Create/Sable/Aeronautics stack is kinetic/SU-based, not FE — so there is **no item in this environment
> it can charge.** WTHIT now shows its buffer + range/rate (inspectable), but charging cannot be
> demonstrated without a cross-mod FE item. Skip the behavioral test; treat as code-confirmed only.
- **Obtain:** crafted — copper ingots + magnetic_plate (center) + lodestone_core _(verify in JEI/EMI)_.
- **Setup:** feed it FE from a cable/generator on any side; let its internal buffer fill.
- **Trigger:** carry an FE-storable item (battery / powered tool / charged gadget) in inventory, offhand, armor, or curio slot; stand within range of a buffered pad.
- **Expect:** the held item's FE fills wirelessly while you're in range; stops when you leave or the buffer empties; multiple chargeable items share the budget.
- **Config (machines):** `inductionPadEnabled` (**new — default FALSE**, master on/off), `inductionPadCapacity` (400000), `inductionPadTransferIn` (4000), `inductionPadChargePerTick` (4000), `inductionPadRange` (4.0), `inductionPadInterval` (2).
- **Result:** ✅ Now **disabled by default** via `inductionPadEnabled` (machines). When off: the pad charges nothing (tick early-returns) and is hidden from the creative tab. Flip it on to use/test with a cross-mod FE item. (Recipe is left intact, consistent with the mod's other soft-disabled content — survival-crafting a pad while disabled yields an inert block.)

## #79 — Magnetostrictive Sensor  ⟶ `magnetization:magnetostrictive_sensor`  — ✅ COMPLETE (you confirmed redstone; WTHIT added)
> Redstone output confirmed by you in-world + GameTest (`sensorEmitsRedstoneOnMovement`). WTHIT status line
> now added (live "Redstone output: N · Range: X blocks") with the signal synced to clients so it isn't stale.
- **Obtain:** crafted — iron + magnetic_plate + redstone _(verify in JEI/EMI)_.
- **Setup:** no power; put a comparator/redstone line off it.
- **Trigger:** move a living entity (sprint past, mob walking) within range; faster + closer = stronger.
- **Expect:** analog redstone 0–15 proportional to fastest nearby entity's speed×proximity; `POWERED` blockstate toggles; signal decays a few points per scan when motion stops. WTHIT shows the live output + range.
- **Config (machines):** `sensorRange` (8.0), `sensorMoveThreshold` (0.02), `sensorInterval` (2), `sensorDecayPerStep` (3).
- **Result:** PASS (redstone confirmed in-world; WTHIT line added — glance to confirm it renders)

## #80 — Barkhausen Generator  ⟶ `magnetization:barkhausen_generator`
- **Obtain:** crafted — redstone + iron + magnetic_plate _(verify in JEI/EMI)_.
- **Setup:** place a magnet block (anything in `#magnetization:anvil_dampeners` — magnetite block, lodestone core, magnet variants) touching any of its 6 faces.
- **Trigger:** none — runs passively while a magnet touches it.
- **Expect:** continuously jittering analog redstone (random 1–15, never 0 while magnetized), changing every ~2 ticks; `POWERED` toggles on transitions; remove the magnet → signal drops to 0.
- **Config:** interval/magnitude are hard-coded (not configurable).
- **Result:** ✅ GameTest-verified (`barkhausenJittersWithAdjacentMagnet`): non-zero jitter + POWERED with adjacent magnet; flat 0 without. In-world: confirm visual/comparator.

## #81 — Kinetic Coil  ⟶ `magnetization:kinetic_coil`
- **Obtain:** crafted — copper frame + iron center _(verify in JEI/EMI)_.
- **Setup:** put an FE consumer / cable adjacent to receive generated power; optional comparator for EMF readout.
- **Trigger:** move a magnetic Sable ship through/near the coil at speed (> ~0.05 blocks/tick).
- **Expect:** FE generated into its buffer and pushed to neighbors while the magnet passes; analog redstone pulses high during the pass, drops to 0 when the ship leaves. Faster ship / more magnetic content → bigger pulse.
- **Config:** hard-coded (RANGE 4.0, MIN_SPEED 0.05, FE conversion/output/capacity) — no config knobs.
- **Result:** ✅ GameTest-verified (`kineticCoilGeneratesFromPassingShip`): a magnetic Sable ship driven past the coil charges its FE buffer. In-world: confirm output push to neighbors + comparator pulse.

## #82 — Halbach Array  ⟶ *mechanic, not a block*
- **Obtain:** nothing — emergent from aligning magnet emitters.
- **Setup:** line up 2+ same-polarity magnet blocks face-to-face (Permanent/Temporary/Electromagnet etc.).
- **Trigger:** right-click an emitter to read its strength in the GUI, and observe its field range/cone.
- **Expect:** aligned same-pole magnets raise the effective tier (1–2 aligned → +1 tier, 3–4 → +2, capped at EXTREME). A MEDIUM emitter w/ several aligned neighbors reads STRONG and pushes/pulls noticeably harder. Hematite blocks in the array step the tier *down* instead.
- **Config:** hard-coded (MAX_BONUS_STEPS = 2).
- **Result:** ✅ GameTest-verified (`halbachBoostsAndHematiteDampens`, pure-function): aligned same-pole magnets raise the effective tier; hematite steps it back down. In-world: confirm GUI strength readout + push feel.

## #83 — Diamagnetic Block + Pyrolytic Carbon wafer  ⟶ `magnetization:diamagnetic_block`, `magnetization:pyrolytic_carbon`
- **Obtain:** wafer = 4 charcoal (shapeless); block = 4 wafers (2×2) _(verify in JEI/EMI)_.
- **Setup:** place at least one diamagnetic block on a Sable ship.
- **Trigger:** fly the ship past a magnetic emitter of *either* pole.
- **Expect:** ship is **repelled by both poles equally** (true diamagnetism), unlike normal ferromagnetic ships (attract opposite / repel same). One diamagnetic block flips the whole ship's behavior; a Polarity Inverter on the ship flips repel↔attract.
- **Config:** a diamagnetic repel/attract default flag may exist _(confirm key in config screen)_.
- **Result:** ✅ GameTest-verified (`diamagneticShipRepelledWhileFerrousAttracted`): a diamagnetic ship is repelled where a ferrous ship is attracted, in the same field. In-world: confirm both-pole repulsion + Polarity Inverter flip.

## #84 — Directional Repulsor  ⟶ `magnetization:repulsor_coil` (+ `magnetization:vector_core`)
- **Obtain:** repulsor = magnetic_plates + lodestone_core + copper blocks _(verify in JEI/EMI)_. **Vector Core — obtain method uncertain (loot/creative? not confirmed in recipes).** Flag if you can't find it.
- **Setup:** place the repulsor (on a ship or standalone); right-click with Vector Core to insert it; supply redstone power.
- **Trigger:** power it. Without core → conical repulsion along its facing. With core → it thrusts magnetic ships along the block's facing direction.
- **Expect:** with Vector Core, ships in range ride along the facing axis up to a terminal speed (acts like a magnetic conveyor / track), not just an upward shove. Breaking the block drops the Vector Core back.
- **Config:** repulsor strength/range caps; track tuning largely hard-coded _(confirm keys in config screen)_.
- **Result:** ✅ GameTest-verified (`directionalRepulsorThrustsAlongFacing`): a powered repulsor with a Vector Core thrusts a ship along its facing axis. In-world: confirm conical (coreless) mode + Vector Core drop on break.

## #85 — Magnetic Anvils dampener  ⟶ `magnetite_anvil`, `maghemite_anvil`, `hematite_anvil`, `titanomagnetite_anvil`
- **Obtain:** anvils crafted like vanilla but with the respective magnetic ingots _(verify in JEI/EMI)_.
- **Setup:** place a dampener magnet (anything in `#magnetization:anvil_dampeners`) orthogonally adjacent to or under the anvil.
- **Trigger:** use the anvil repeatedly to rename/repair.
- **Expect:** per-metal break chance applies (magnetite 0.10, maghemite 0.18, hematite 0.15, titanomagnetite 0.0 = never, default 0.12); with an adjacent dampener the break chance is forced to **0 (never degrades)**; anvil-use sound is quieter. Remove the dampener → normal per-metal chance returns.
- **Config (anvils):** `magnetiteBreakChance`, `maghemiteBreakChance`, `hematiteBreakChance`, `titanomagnetiteBreakChance`, `defaultBreakChance`.
- **Result:** ✅ GameTest-verified (`anvilDampenerDetectedWhenMagnetAdjacent`): dampener adjacency detection + per-metal config defaults (titanomagnetite 0). In-world: confirm break-chance feel at the anvil.

## #90 — Deuterium Oxide + Fuel Cell + Tokamak  ⟶ `deuterium_oxide`, `deuterium_cell`, `tokamak_controller`, `tokamak_coil`
- **Obtain:** D₂O bucket from the fluid; Deuterium Cell = iron + redstone + glowstone + deuterium_oxide_bucket _(verify in JEI/EMI)_. **Tokamak controller/coil recipes not confirmed in code — check JEI; flag if creative-only.**
- **Setup:** ring the controller with 8 tokamak_coils on the same Y (full 3×3 perimeter). Right-click the controller with a Deuterium Cell to load fuel. Put an FE machine/cable adjacent to receive output.
- **Trigger:** complete the ring with fuel loaded.
- **Expect:** controller `LIT` turns on; generates ~2000 FE/tick into its buffer and pushes up to 16000 FE/tick to neighbors; fuel slot drains one cell at a time (~4 min/cell); GUI shows burn time, output rate, stored/capacity. Break the ring → stops.
- **Config (machines):** `tokamakFeCapacity` (4000000), `tokamakGenPerTick` (2000), `tokamakOutputRate` (16000), `tokamakBurnTicksPerCell` (4800).
- **Result:** ✅ GameTest-verified (`tokamakGeneratesWithRingAndFuel`): ring-of-8 forms, fuel loads, buffer charges, block LIT. In-world: confirm output push + GUI.

## #91 — MR Fluid hardens in field → walkable bridge  ⟶ `mr_fluid`, `hardened_mr_fluid`
- **Obtain:** MR Fluid bucket = water_bucket + iron_ingot + raw_magnetite (shapeless) _(verify in JEI/EMI)_.
- **Setup:** place MR fluid so it forms a small pool/body; set up a powered emitter beside it.
- **Trigger:** power the emitter so the field covers the fluid.
- **Expect:** the connected MR fluid body snaps to solid grey `hardened_mr_fluid` (walkable, no fall-through); unpower / remove field → reverts to fluid. Hardening floods the connected body.
- **Config (performance):** `mrFluidHardenTicks` (5).
- **Result:** ✅ GameTest-verified (`mrFluidHardensInField`): MR-fluid source beside a powered electromagnet hardens to hardened_mr_fluid. In-world: confirm revert when field removed + walkable.

## #92 — MR Armor field behavior  ⟶ `mr_liquid_helmet/chestplate/leggings/boots`
- **Obtain:** each = iron piece + mr_fluid_bucket (shapeless) _(verify in JEI/EMI)_.
- **Setup:** wear the pieces.
- **Trigger A (in field):** stand in an active magnetic field.
- **Trigger B (out of field):** take a kinetic hit (fall, melee, projectile, explosion) with no field.
- **Expect:** **NOT pulled** by fields (unlike metal armor). In field → texture stays rigid plate, continuous ~big damage mitigation (scales per piece, high cap). Out of field → fluid ripple idle, snaps rigid on a kinetic hit for a short window with strong mitigation. Confirm it does *not* yank you toward emitters.
- **Config (performance/combat):** `mrArmorRefreshTicks` (5), `mrArmorHardenTicks` (30), plus per-piece/cap mitigation keys _(confirm exact names in config screen)_.
- **Result:** ✅ GameTest-verified (`mrArmorMitigatesDamageInField`): an MR-armored zombie loses far less health than a bare one taking the same hit in the same field. In-world: confirm no field-pull + out-of-field on-hit harden.

## #93 — MR Fluid Golem  ⟶ `mr_fluid_golem` (+ `mr_fluid_golem_spawn_egg`)
- **Obtain:** spawn egg = carved_pumpkin + 4 mr_fluid_bucket _(verify in JEI/EMI)_.
- **Setup:** spawn it with the egg.
- **Trigger:** lead it into / out of an active field; attack it in each state.
- **Expect:** in field → hardened texture, very high mitigation, immovable (no knockback); out of field → fluid texture, modest mitigation, normal knockback. Behaves like an iron golem (defends players, attacks hostiles) but lower max health.
- **Config (performance/combat):** `golemFieldCheckTicks` (5), plus golem mitigation keys _(confirm names in config screen)_.
- **Result:** ✅ GameTest-verified (`mrGolemHardensInField`): a golem next to a magnet reads hardened after a field-check interval. In-world: confirm knockback-immunity + iron-golem-style behavior.

## #96 — MR Fluid tools  ⟶ `mr_fluid_sword/pickaxe/axe/shovel/hoe`
- **Obtain:** each = iron tool + mr_fluid_bucket (shapeless) _(verify in JEI/EMI)_.
- **Setup:** hold one.
- **Trigger:** attack a mob or break blocks, then watch the icon; also grind durability.
- **Expect:** idle icon shows rippling fluid animation; on use it snaps to a rigid-plate icon for a short window (~14 ticks) then relaxes; durability barely drops with heavy use (iron-tier but "barely wears").
- **Config (tools):** `mrToolHardenTicks` (14).
- **Result:** ✅ GameTest-verified (`mrToolBarelyWearsAndHardensOnUse`): pickaxe max-durability ≫ iron, one mine costs ≤1 durability and stamps HARDENED_UNTIL. In-world: confirm idle/rigid icon swap.

## #97 — MR Fluid horse armor  ⟶ `mr_fluid_horse_armor`
- **Obtain:** iron_horse_armor + mr_fluid_bucket (shapeless) _(verify in JEI/EMI)_.
- **Setup:** equip on a horse (right-click horse).
- **Trigger:** ride into a field / let the horse take a kinetic hit.
- **Expect:** flowing-fluid look on the horse idle; swaps to rigid plate in field or on hit (matches player MR armor look + mitigation); rendered via the mod's custom horse layer (vanilla layer suppressed).
- **Config:** same MR-armor keys as #92.
- **Result:** ✅ GameTest-verified (`mrHorseArmorIsValidBardingOnTheMitigationPath`): it's an MrFluidHorseArmorItem + AnimalArmorItem the horse accepts as body barding (same mitigation handler as #92). The custom render layer is client-only → in-world visual check.

## #99 — Fluids carry redstone signal  ⟶ ferrofluid / magnetized_ferrofluid / mr_fluid / hardened_mr_fluid / gallium / mixed_gallium
- **Obtain:** ferrofluid = water_bucket + 2 raw_magnetite; MR = water + iron + raw_magnetite; magnetized ferrofluid = magnetize a ferrofluid bucket via electromagnet GUI; gallium/mixed gallium buckets _(verify sources in JEI/EMI)_.
- **Setup:** lay a fluid "wire" from a redstone source (lever/torch/block) to a lamp/door.
- **Trigger:** toggle the source.
- **Expect:** all **six** fluids above conduct redstone like liquid dust (carry, don't react — except gallium's separate Lorentz effect); MR signal attenuates one level per cell. Powered fluid emits small **redstone dust particles** as a visual cue. Fluids flow **around** torches/redstone dust/repeaters/comparators/levers without washing them away. (Deuterium oxide does **not** conduct — good negative control.)
- **Config (performance):** `ferrofluidMagTicks` (4), `ferrofluidPlainTicks` (8), `magnetizedFerrofluidTicks` (3) — affect creep/field, not conduction itself.
- **Result:** ✅ GameTest-verified (`conductiveFluidsCarryRedstone`): gallium carries signal 2 cells (attenuated); deuterium oxide does NOT (control). In-world: confirm all six fluids + particles + flow-around.

## #103 — Gallium Lorentz current  ⟶ `gallium`
- **Obtain:** gallium ingot by smelting raw gallium / zinc / aluminium; gallium fluid bucket _(verify in JEI/EMI; bucket source uncertain)_.
- **Setup:** place a gallium **source** block; carry redstone signal into it; cover it with an active emitter's field.
- **Trigger:** stand (or drop items / push a mob/boat) in the powered, field-covered gallium.
- **Expect:** **entities** floating in it get pushed — radially **outward** under a NORTH field, **inward** under a SOUTH field; strength scales with signal level. Blocks are **not** moved. Unpowered gallium or gallium outside a field → no push. Boats float on it.
- **Config (performance):** `galliumCurrentTicks` (2), `galliumCurrentSpeed` (0.09).
- **Result:** ✅ GameTest-verified (`galliumLorentzPushesEntity`, wiring): a placed gallium cell registers as a tracked Lorentz source and sits in a magnetic field — the two conditions (besides a redstone current) the push handler reads. ⚠️ The entity push magnitude itself is **in-world-only**: a powered (redstone-fed) gallium fluid cell spins the headless GameTest batch runner indefinitely, and fluid drag makes the in-fluid velocity sample unreliable — so the test asserts the wiring and the live push is verified in-world.

## #104 — Mixed gallium (ferrofluid-like creep + dual ability)  ⟶ `mixed_gallium`
- **Obtain:** mixed gallium bucket from gallium + magnetite/iron _(verify recipe in JEI/EMI — reader flagged this unconfirmed)_.
- **Setup:** place a mixed-gallium source; put a magnet nearby; also wire redstone into it and cover with a field.
- **Trigger:** observe creep toward the magnet **and** entity push when powered+in-field.
- **Expect:** it creeps tendrils toward the magnet like ferrofluid **and** applies the gallium Lorentz entity-push simultaneously (both abilities at once). Steel-blue tint, visually distinct from plain gallium.
- **Config (performance):** `ferrofluidPlainTicks` (8) for creep, `galliumCurrentTicks` (2) for Lorentz.
- **Result:** ✅ GameTest-verified (`mixedGalliumLorentzPushesEntity`, wiring): mixed gallium registers as a tracked Lorentz source in a field (same wiring + same caveat as #103). The creep ability + the live entity push are **in-world-only**.

## #109 — Gallium gear + dyes + freeze/melt  ⟶ `gallium_*` tools/armor, `solid_gallium`, dye recipes
- **Obtain:** gallium tools/armor from gallium_ingot; solid_gallium = 9 gallium_ingot; dyes shapeless from buckets _(verify in JEI/EMI)_.
- **Setup / Triggers:**
  - **Gear:** craft & use a gallium tool/armor set.
  - **Dyes:** craft ferrofluid_bucket → 4× black_dye; mr_fluid_bucket → 4× gray_dye; gallium_bucket → 4× light_gray_dye.
  - **Freeze:** put ice/snow/packed-ice next to a gallium **source**; wait ~2 s.
  - **Melt:** remove all cooling from a solid_gallium block; wait ~6 s.
- **Expect:** gallium gear works like gold but softer/weaker (low durability, fast mining, no attack bonus); the three dye recipes yield the listed dyes; gallium freezes to `solid_gallium` near cooling and melts back to fluid when cooling is gone. (Buckets are returned/handled per recipe — note if a bucket is wrongly consumed.)
- **Config:** freeze/melt delay knobs may exist _(confirm key names in config screen)_.
- **Result:** ✅ GameTest-verified (`galliumFreezesNearIceAndMeltsWhenRemoved`): gallium next to ice freezes to solid_gallium, then melts back to fluid once the ice is removed. The gear stats + dye recipes are item/recipe data → verify in JEI/EMI (not a behaviour test).

## #112 — Gallium golem + mixed-gallium dual ability  ⟶ `gallium_golem`
- **Obtain:** build the iron-golem-style multiblock using `solid_gallium` for the body + a carved pumpkin / jack-o'-lantern head _(confirm exact shape in-game; reader described a T/iron-golem pattern)_.
- **Setup / Triggers:**
  - **Spawn:** assemble the multiblock and place the head last.
  - **Melt:** leave the golem in a warm (non-cold) biome ~60 s.
  - **Shatter:** kill it by damage (ideally in a cold biome).
  - **Dual ability:** re-confirm #104 (this task pairs the golem with the mixed-gallium both-abilities check).
- **Expect:** golem spawns (structure consumed with particles); it's weaker than an iron golem (lower health, easily knocked, not magnetic); in a warm biome it takes extra damage and eventually **melts into a gallium fluid source with no drops**; killed otherwise it **shatters into 1–3 solid_gallium**. In a cold biome it persists.
- **Config:** golem melt-time / warm-damage multiplier may exist _(confirm key names in config screen)_.
- **Result:** ✅ GameTest-verified — golem (`galliumGolemIsAWeakerIronGolem`): it's an IronGolem subclass with max health < 100 and zero knockback resistance (weaker/soft). Dual ability (`mixedGalliumRegistersForBothCreepAndLorentz`): mixed gallium registers in BOTH FerrofluidSourceRegistry (creep) and GalliumRegistry (Lorentz). ⚠️ In-world only: multiblock spawn/particles, warm-biome **melt → fluid source (no drops)**, **shatter → 1–3 solid_gallium** loot (biome-temperature + loot-table dependent, not headless-deterministic).

---

### Quick negative controls worth checking while you're in there
- Deuterium oxide should **not** conduct redstone (#99 control).
- MR armor should **not** be pulled toward emitters (#92).
- Gallium should move **entities only**, never blocks (#103).
- A magnetic anvil with **no** adjacent dampener should still degrade at its per-metal rate (#85).
