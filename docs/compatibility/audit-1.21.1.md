# Compatibility audit — Minecraft 1.21.1 NeoForge / 1.4.6

This audit starts at `0a28d3105690ebce53a93809adbafb3661d884b2` and incorporates the later fixes through `bc28565` plus the 1.4.6 version bump `2c5b519`. It distinguishes a working interaction from an optional reference that is silently ignored. A successful launch, matching asset filename, or `required:false` is **not** compatibility verification.

The explicitly requested follow-up repairs and deeper native behavior tests are recorded in [followup-audit.md](followup-audit.md). That record supersedes the deferred Modular Golems/Quark work and the Tesla, Twilight, storage and attachment gaps below as its rows are verified.

## Evidence and classifications

[Matrix details](matrix-details.json) records the exact inspected artifact filenames, descriptor mod IDs/versions, SHA-256 hashes, original sweep references, and current registry/tag references for each namespace. [Pinned downloads](additional-artifacts.json) and [additional pins](sweep-artifacts.json) retain provider version IDs and URLs. [Final tag inventory](final-tag-inventory.json) is reproducible with `tools/audit_optional_tags.py`; its resource matches are static evidence only. The initial inventory remains as a historical baseline.

- **Verified** means the specific interaction described in the row passed. It does not imply every feature of the upstream mod was tested.
- **Incomplete/incorrect** identifies a demonstrated defect, including upstream startup failures. Rows say whether it is corrected and retested.
- **Wrong game version or loader** applies to an identified artifact/project, not to all similarly named ports.
- **Unverified** means the described behavior lacks runtime evidence. Useful optional entries can remain without being advertised as verified.

Runtime evidence is retained in [test-evidence.txt](test-evidence.txt) and [evidence](evidence/). The Reborn suite passed 12/12 tests, including Magnetization construction, solid/fluid repair and owner/team regression checks. Seven loaded sweep profiles resolved 234 explicit registry/tag references with no unresolved references before exercising their scoped interactions. The final profiles use Java 21, Minecraft 1.21.1, NeoForge 21.1.252 and the repository's required Create/Sable stack. Earlier supporting runs used the previous 21.1.248 development pin. The workspace's separate version bump changed that pin during the audit.

## Original sweep: material, mob, aircraft, armor and lightning matrix

IDs below omit repeated namespaces where clear; the JSON matrix has the complete lists and hashes. All artifacts in this table target NeoForge 1.21.1 unless explicitly stated otherwise.

| Integration / exact artifact | Mod ID; registry/API and intended behavior | Classification and test evidence |
|---|---|---|
| Extra Golems, original project 233300, 20.1.0.2 (1.20.1 Forge) | Historical `extragolems:{iron,gold,copper,netherite,nether_brick}_golem` guesses | **Wrong game version or loader**; historical IDs were never established as working. Remove all five. This project is distinct from Reborn. |
| Extra Golems Reborn, unofficial port, CF 1456863/file 7922386, `Extra-Golems-Reborn-1.21.1-21.1.0.1.jar` | `golems`; shared `golems:golem`, `GolemBase.getGolemId()`, `EGRegistry.Keys.GOLEM`; body response via allowlist | **Verified**, scoped to 21.1.0.1: native construction, all defaults, controls, equipment, effects, both polarities, veto, next-tick changes, physical emitter, magnetic golem, serialization, client rendering and save/reopen. See material policy below. |
| Modular Golems `modulargolems-3.1.43.jar` | `modulargolems`; `GolemTypes` registers `metal_golem`, `humanoid_golem`, `dog_golem`, not `iron_golem_entity` / `metal_golem_entity` | **Verified after separately requested repair**: optional material adapter reads `getMaterials()` and `getCraftIngredient(id)` every tick. All three body kinds, mixed/nonmetal materials, armor, controls, transitions and serialization passed. See follow-up. |
| Quark `Quark-4.1-485.jar` | `quark`; `ToretoiseModule` registers `toretoise`; `getOreType()` distinguishes ores | **Verified after separately requested repair**: guarded `getOreType()` adapter recognizes iron/copper, follows native harvest/ore changes and preserves armor/tag/veto behavior. Four loaded tests passed; see follow-up. |
| Cataclysm `L_Ender's Cataclysm 1.21.1-3.33.jar`, MR `PsPYpoCC` | `cataclysm`; entities `ignis`, `netherite_monstrosity`, `the_harbinger`; five `ignitium_*` armor items; damage `lightning` | **Verified after corrections**, boss profile: registry resolution, both field polarities, worn armor response, damage-event LIRM. Removed nonexistent five-tool `ignitium_*` set. Replaced `lightning_spear` / `electric_shock` with `lightning`. This tests delivery of the registered damage type, not every Scylla attack animation/AI path. |
| Bosses of Mass Destruction **Forge/NeoForge port**, `BOMD-NeoForge-1.21-1.3.3.jar`, MR `snhDYBxP` | `bosses_of_mass_destruction:gauntlet`; `BMDEntities` | **Verified** registry and both field impulses in boss profile. Distinct from Barribob's original Fabric/Quilt project; original-loader support is not claimed. Full boss combat is not tested. |
| Immersive Aircraft `immersive_aircraft-1.5.2+1.21.1-neoforge.jar`, MR `ZZTlNkV9` | `immersive_aircraft`; seven aircraft and 18 metal components, listed in JSON | **Verified** all 25 references and attraction/repulsion of every aircraft/component in aircraft profile. The follow-up verifies piloted behavior and two-client synchronization, with 180 ticks per tagged vehicle; fixes additive client impulse delivery. Crafting/assembly and survival fuel remain outside that test. |
| Aviator Dreams **Reloaded**, `aviator-dreams-reloaded-neoforge-1.2.7+1.21.1.jar`, MR `4Aa8QEq9` | `aviator_dream`; `douglas_dc1`, `douglas_dc2`, `douglas_c47`, `lockheed_l1049g`, `dehavilland_dh106`, `fokker_fviib3m`, `fokker_fviia`, `toyota_stout_k100` | **Verified** all eight registry entries and both field impulses with Immersive Aircraft. The follow-up also verifies real pilot/observer behavior for 180 ticks per type. Reloaded is the tested maintained fork, not a claim for every original Aviator Dreams artifact. |
| Magnetizing `magnetizing-neoforge-1.1.0+mc1.21.1.jar`, MR `az3hmBKl`; Architectury 13.0.8 | `magnetizing`; blue/red item magnets, four block magnets, `c:ingots/magnetite`, `unmoveable_by_magnets` | **Verified** IDs, item impulses, block emitter membership, stable-block ship scanning and ingredient acceptance in `magnetite_block` / `magnetic_excavator`. Veto precedence is exercised by Reborn's explicit tag fixture. |
| Create: Magnetics 0.0.4-alpha, CF 1457075/file 7667440 | `createmagnetics`; `kinetic_magnet`, `magnetized_crystal`, `pure_magnetized_crystal`, `magnetic_induction_coil`; upstream supplies `c:` magnetite ingot/plate/block tags | **Upstream defect** in the unmodified dedicated-server artifact: `CMBlockEntities` loads client `SoundInstance`. **Verified with the built-in opt-in server workaround**: four references, item forces, emitter classification, recipe ingredients and native Kinetic Magnet construction/ticking/removal. Original trial evidence is retained below. Active kinetic field projection is not implemented by Magnetization. Sheet/block interchangeability depends on recipes consuming those common tags. |
| Simulated `simulated-neoforge-1.21.1-1.3.0.jar` | `simulated:redstone_magnet`; emitter membership increases ship susceptibility | **Verified** required stack and material-profile reference/role checks. This is an onboard magnet weight, not an external `ExternalFieldCompat` field adapter. |
| Alex's Caves **Unofficial Port** 2.0.10, MR `pC1MYhqR`; Citadel **Unofficial Port** 2.7.6, MR `mIylVpkN` | `alexscaves`; Magnetron/Ferrouslime, neodymium, armor, active Azure/Scarlet magnets, `magnetizing` effect, five supplemental recipes | **Verified after correction**: five-test loaded profile covers 27 explicit sweep references, entity/item/armor forces, stable-block scanning, magnets/recipes/material roles, all three potion modes. Corrected `Added` event replacement to `Applicable` denial. Original official 1.20.1 Forge artifacts are not this target. |
| Alex's Caves lightning guesses in the same port | No `alexscaves:tesla` or `magnetron_shock` damage types; `ACDamageTypes` and data inspected | **Verified after source-specific repair**: actual Tesla discharge bypassed strike events and failed before repair. Optional wrapper covers both `thunderHit` sites; native discharge stamps exactly one piece and honors switches. Native Magnetron left/right punches and slam remain physical, non-LIRM. Seven-test profile passed; see follow-up. |
| Iron's Spells `irons_spellbooks-1.21.1-3.16.3.jar`, MR `slKLosTb` | Actual mod ID `irons_spellbooks`, damage `lightning_magic`; four `netherite_mage_*` armor items, smithing recipes and `NETHERITE_BATTLEMAGE` | **Verified after corrections** in boss profile: all five references, armor forces and damage-event LIRM. Removed fictional iron-plated armor and three sword names; corrected Java-package namespace `ironsspellbooks`. Individual spell names are not damage types. |
| Immersive Engineering `ImmersiveEngineering-1.21.1-12.4.2-194.jar`, MR `uNRARSH2` | `immersiveengineering`; steel/Faraday `armor_steel_*` / `armor_faraday_*`; `railgun_shot`; architecture, electrical buffers, Tesla damage and external fields | **Verified after corrections**, five-test profile: 78 explicit references, armor/item/projectile forces, stable-block scan, electromagnet/Tesla adapters and recipe roles. Corrected all eight armor IDs. Existing electrical-damage policy (`tesla`, `tesla_primary`, `razor_shock`, `razor_wire`) is preserved; tagging razor wire is a gameplay rule, not a claim that every hit is natural lightning. |
| The Aether `aether-1.21.1-1.5.10-neoforge.jar`, MR `K5X5qMwG`; owo MR `NMCHU6DZ` | `aether:gravitite_ore`, `aether:enchanted_gravitite` items/blocks | **Verified after corrections** in equipment profile: four IDs, item impulses, material classification and native placement/retention over 40 moving ticks. Removed `gravitite` / `gravitite_block` guesses. Raw ore needs a solid ceiling to prevent its native upward floating; no suppression of that upstream behavior is added. See follow-up. |
| Twilight Forest `twilightforest-1.21.1-4.8.3345-universal.jar` | `twilightforest`; knightmetal/steeleaf/ironwood/fiery items and armor, storage blocks; no `lightning` damage type | **Verified after repair**, 38 explicit references and native Ur-Ghast cosmetic lightning, profile 2/2. Corrected `steeleaf_ingot`; cosmetic bolts no longer convert logs, real bolts still do. No fabricated Twilight lightning damage ID. See follow-up. |
| Iron Chests `ironchest-1.21-neoforge-16.0.7.jar`, CF 228756/file 5491156 | `ironchest`; six metal chest blocks | **Verified** registry/material roles in material profile. Storage block entities are not covered by the stable-full-block scanner fixture; general Sable inventory preservation is not inferred here. |
| Sophisticated Storage `sophisticatedstorage-1.21.1-1.5.91.2127.jar` | `sophisticatedstorage`; iron/gold/netherite chest/barrel IDs | **Verified** six metal chest/barrel variants and wood control, profile 2/2. Native inventory/upgrades/names/locks and item access survive moving-ship disk save/close/reopen/load and subsequent ticks. See follow-up for exact boundary. |
| Supplementaries `supplementaries-1.21.1-3.9.9-neoforge.jar`; registry cross-check against pinned MR `ZHJvgW8G` (3.8.9) | `supplementaries`; iron gate/door/trapdoor, mechanical blocks, cannonball | **Verified** registry/material response and native placement/retention of all 14 tagged blocks over 40 moving ticks, including both door halves; native redstone parity. Cannon/pulley production workflows are outside this fixture. See follow-up. |
| Macaw's Doors 1.1.5 (`u7BRX44F`), Fences 1.2.1 (`jVdb0r4W`), Bridges 3.1.2 (`aQ7rY7ng`), Trapdoors 1.1.5 (`StnP0RNi`), Lights 1.1.5 (`5U2kQZIL`), Windows 2.4.2 (`rQUE4LCz`) | `mcwdoors`, `mcwfences`, `mcwbridges`, `mcwtrpdoors`, `mcwlights`, `mcwwindows`; exact decorative metal IDs in JSON | **Verified** explicit registry/material roles in material profile. Stable full blocks exercise the scanner; attachment-specific construction and all decorative shapes aboard moving ships are not established. |
| Mekanism + Mekanism Tools `10.7.19.85`, MR `5KzzycBT` / `v5zlSE9s` | `mekanism` common materials; equipment belongs to **`mekanismtools`**, nine `steel_*` armor/tools | **Verified after correction**, equipment profile: registry, armor impulses and LIRM tool stamping. Common-tag coverage is provider-dependent; do not claim every Mekanism machine or alloy is magnetic. |
| Common `c:` metal tags | iron/gold/copper/netherite plus steel, nickel, cobalt, zinc, brass, tin, lead, silver, osmium, uranium, aluminum, neodymium and alloy families | **Verified mechanism**, not blanket verification of every provider. Preserve useful optional common tags. Empty/unpopulated tags imply no material coverage. Only recipes using the corresponding ingredient tag become interchangeable. |

## Runtime adapters, recipes and later fixes retained

The original sweep's tag-only emitter entries do not instantiate external field sources. `ExternalFieldCompat` and `ExternalEmitterTracker` now provide separate loaded-mod adapters; existing switches, force multipliers and upstream entity-movement ownership remain intact. Relevant later history includes `242d71c` (New Age), `a082b42` (C&A), `5a3d1d2` (addon tests), `9a7cbfb` (TFMG), `65b83a7` (master switches), `d40ab24` (emitter tags), `9f5f659` (coasters/missiles), `383a18e` (pack version floors), and `bc28565` (Ironworks/Additions). These must not be reverted to the original sweep's guesses.

| Package and pinned target | API/behavior and evidence retained |
|---|---|
| EMI `5sIPA1To` (1.1.24+1.21.1+neoforge), `emi`; Curios `yohfFbgD` (9.5.1+1.21.1), `curios`; Patchouli `BIogJv2D` (1.21.1-93-neoforge), `patchouli` | `MagEmiPlugin.register`, `MagCurioCompat`, slot data and active keybind packet paths, Patchouli book/conditional recipes. Existing client/release evidence and Curios profile retained. **Unverified by this audit**: new end-to-end UI traversal of all three. Book recipes are now Raw Magnetite/Lodestone recovery alternatives; generic iron is default-off. Master toggles remain. |
| C&A `qPr8V4G2` (1.6.0), `createaddition`; New Age CF 8215216 (1.2.0), `create_new_age` | Authoritative magnet tags, FE capabilities, generator coils/conductors, Tesla damage. `CreateAdditionGameTests`, `CreateNewAgeGameTests` and pinned profiles retained. **Verified again in this audit**: C&A 3/3 and New Age 12/12 scoped tests; no broad all-machines claim. |
| TFMG community build 1.3.1-community, `tfmg` | Dedicated `Tfmg*GameTests`, magnet-slot/material roles, fluids, conditional recipes and opt-in Polarizer field preserved. Community artifact is distinct from other upstream versions. **Verified prior scoped tests**. |
| Tracks CF 7968280 (1.0.1), `tracks`; Steam 'n' Rails **unofficial NeoForge port** CF 8243336 (0.2.1), `railways` | Vehicle components/train force and linked-car deduplication; existing profiles retained. Do not replace tested 0.2.1 with statically inspected 0.3.0-beta.2 merely because it is newer. |
| Diesel Generators CF 8542167 (1.3.15), `createdieselgenerators`; Enchantment Industry CF 8241347 (2.4.2) + Dragons Plus CF 8633162 (1.11.7b), `create_enchantment_industry` | Material exclusions, Ferrofluid spray/recipe and processing adapters; existing GameTests retained. Static inspection of Enchantment Industry 2.5.4 does not extend runtime verification to it. |
| AeroPortals CF 8621990 (1.3.0) / 8433331 (1.1.2), `aeroportals`; Immersive Aeronautics CF 8586854 (package 1.1.4, core 6.0.7) | Transfer/remapping/portal force adapters and old-version fallbacks retained. Prior dedicated evidence remains scoped to pinned versions. |
| Coasters CF 8586851 (0.1.4) / 8542817 (0.1), `simulated_coasters`; Track Styles CF 8593009; Magnetized CF 8668703; Engineered CF 8653100; Extras CF 8718286 | Cart movement, rail-engaged Structural Inducer contracts, field-powered anchors and Linear Motor recipes. Existing per-addon server/client gates retained. |
| Ironworks 4.0.3, `create_ironworks`; Coasters Additions CF 8772429, `coasterfins`; Interiors `gBrfZy6S` | Conditional armor/material and Fin emitter tags plus existing cart bridge. **Verified again in this audit**: combined Ironworks/Coasters Additions profile 5/5. Tag conditions and reload controls preserved. |
| CBC `bOiDu0LS` (5.11.7) + Ritchie's library `hZ6B2Z0x` (2.1.2); missiles CF 8658979 + Radars CF 8227753 | Projectile susceptibility and EMP guidance disable; dedicated profiles retained. No new carriage/gas magnetization. |
| Cosmonautics CF 8612744 (26.08.307 beta), `rocketnautics`; Copycats `kecZ0sl7` (3.0.4); Ender Transmission CF 7232820 (2.1.1); excavation CF 6327284 / 7199305 | Registry/recipe/fuel adapters, copied-material scanning, kinetic/fluid/component transport and resource veins. Existing scoped GameTests/config gates retained. Field relay remains default-off. |

Exact later registry references and artifact hashes where inspected are also in `matrix-details.json`; dependency coordinates and isolated runtime definitions are in `build.gradle` and `gradle.properties`. These rows preserve previous scoped evidence, and are not a claim to have rerun the entire historical release matrix.

## Reborn policy and optional linkage

The adapter is enabled only when mod ID `golems` reports **21.1.0.1**. All references to upstream classes live in the nested loaded implementation, behind that gate. Nothing is bundled. Material recognition uses `GolemBase.getGolemId()` and checks the current dynamic golem registry, not a permanent entity-type cache or upstream's cached container. Unknown definitions are inert; Reborn itself rejects attempts to construct an unknown definition.

`FieldApplicator` ORs explicit entity tagging with the material adapter for both eligibility and the **single 1.0 intrinsic susceptibility**. Equipment still adds its normal contribution; equipment polarity, Magnetized status, rare-earth modifiers and `affectsArmor` retain their existing handling. `magnetizing:unmoveable_by_magnets` is checked first and vetoes all movement. The shared per-entity/per-tick classification reads material state again on the next server tick. Magnetic golems and physical emitters use that same path.

| Default material IDs (all `golems:`) | Basis in Magnetization policy |
|---|---|
| `raw_iron` | `c:raw_materials/iron` and raw iron storage block. Reborn does not need an invented `golems:iron`; vanilla `minecraft:iron_golem` already has intrinsic response. |
| `raw_gold`, `gold` | Existing gold raw-material/ingot/storage rules; gold is intentionally magnetic in this gameplay system. |
| `raw_copper`, `copper`, `exposed_copper`, `weathered_copper`, `oxidized_copper`, `waxed_copper`, `waxed_exposed_copper`, `waxed_weathered_copper`, `waxed_oxidized_copper` | Existing copper policy extended consistently over Reborn's actual oxidation/waxing definitions. Wax and oxidation do not silently switch off the same copper body. |
| `netherite`, `ancient_debris` | Existing netherite ingot/storage and `c:ores/netherite_scrap` block rules. |
| Excluded: wood, stone, `nether_brick`, unknown/missing definitions | No matching intrinsic metal policy. The legacy nether-brick guess is not evidence. Magnetic equipment can still contribute. |

The complete [upstream material inventory](reborn-materials.json) records construction blocks and default policy for each definition. Every default material resolves in the actual upstream dynamic registry; tests apply both polarities to every default. Native honeycomb waxing and axe unwaxing/scraping are exercised. A next-tick copper→wood→waxed oxidized copper test detects stale classification. Natural random oxidation timing is not statistically tested; the resulting IDs are covered individually.

## Server configuration

These controls follow existing server-authoritative **COMMON** compatibility settings in `config/magnetization-common.toml` (synchronized to connected clients), not per-world SERVER settings:

- `compat.extraGolemsRebornCompatEnabled = true`.
- `compat.extraGolemsRebornMaterials` defaults to the 14 IDs above. It accepts only explicitly namespaced lowercase resource identifiers; empty namespace/path, whitespace, uppercase and malformed IDs are rejected. Valid datapack-defined IDs may be added. An empty list disables automatic intrinsic recognition.
- After NeoForge reloads the config, recognition updates on the next server tick. `/reload` reloads datapacks; it is not a promise to reread arbitrary config files. Restart is a reliable fallback when an external editor does not trigger the config watcher.
- Disabling compatibility or removing a material does not suppress equipment or explicit entity tags. Tagging shared `golems:golem` opts **all** its materials in, including wood/stone. Matching both tag and allowlist still counts intrinsic response once. The administrative unmoveable tag wins over both.

## Concrete correction list / release scope

Completed in this release:

1. Remove all `extragolems:*` and invalid Modular Golems/Quark entity entries.
2. Add native, optional Reborn material integration, controls and behavioral tests.
3. Correct Aether item/block IDs; Iron's Spells namespace, netherite armor and damage type; IE steel/Faraday armor names; Mekanism Tools namespace; Cataclysm damage ID.
4. Remove unsupported Cataclysm tools, Iron's Spells guessed tools/armor, Supplementaries rocket and nonexistent AC/Twilight damage types.
5. Fix AC potion replacement event timing, with coexistence/replacement tests.
6. Replace blanket README/manual support claims with this scoped evidence; identify original projects versus ports. Preserve common tags and later conditional/adapted integrations.
7. Add pinned aircraft/material/equipment/boss/Magnetics profiles and reproducible evidence. The supplied local Magnetics workaround remains supporting test evidence. Its server sound isolation is now available as a built-in, default-off option; no additional production dependency is added.

The explicitly requested follow-up now includes material-aware Modular Golems/Quark adapters, actual Tesla/Magnetron and Twilight lightning checks, storage persistence, piloted multiplayer aircraft and attached-block fixtures. See [follow-up results](followup-audit.md).

Separate proposed work, **not silently added to 1.4.6**:

- Create: Magnetics active kinetic-field projection; the current integration classifies onboard material/emitter susceptibility only.
- Additional Modular Golems upgrades, comprehensive machine production workflows and multiblock recipes beyond the specifically tested interactions.

## Opt-in Create: Magnetics dedicated-server workaround

Create: Magnetics 0.0.4-alpha has an **upstream startup defect**, not a Magnetization integration failure: `CMBlockEntities` resolves client sound classes on the dedicated server. The supplied trial established the workaround; Magnetization now includes equivalent server-only sound isolation behind `compat.createMagneticsServerCrashWorkaround`, default **false**.

Set this in the server's `config/magnetization-common.toml` before startup (merge into an existing `[compat]` section):

```toml
[compat]
createMagneticsServerCrashWorkaround = true
```

The mixin reads this TOML value before normal config registration, so a **full server process restart** is required; `/reload`, config reload and world changes cannot apply or undo it. The regular COMMON spec documents the setting and marks it as requiring a game restart. Missing, malformed or non-boolean values do not opt in.

The patch applies only on dedicated servers with `createmagnetics` version `0.0.4-alpha`. It removes the client sound field and makes the two sound lifecycle methods no-ops before JVM verification. Other upstream logic and all physical-client sound behavior remain unchanged. It is independent of magnetic gameplay switches and inactive when Magnetics is absent. If the standalone trial is present, it owns the transformation to avoid applying it twice. Future versions require separate validation.

Pinned built-in profiles: `runMagneticsWorkaroundGameTestServer` and `runMagneticsWorkaroundAbsentGameTestServer`. Both seed the real TOML option in disposable run directories. `runSweepMagneticsGameTestServer` keeps the default-off control, and `runMagneticsWorkaroundClient` checks that an enabled file does not patch physical clients. Additional results are recorded in `magnetics-workaround-evidence.txt`.

## Reproduction and limits

Use `scripts/run-gametest-gate.sh <task> <run-directory> 240`; it archives old disposable worlds and rejects a process that merely exits without passing assertions. Profiles: `runExtraGolemsRebornGameTestServer`, `runOptionalCompatibilityAbsentGameTestServer`, `runSweepAircraftGameTestServer`, `runSweepMaterialsGameTestServer`, `runSweepEquipmentGameTestServer`, `runSweepBossGameTestServer`, `runAlexsCavesGameTestServer`, `runImmersiveEngineeringGameTestServer`.

For the user-supplied Magnetics trial, run `runSweepMagneticsGameTestServer` with `-PmagneticsCompatJar=/path/to/create-magnetized-magnetics-compat-0.1.0-trial.jar` as an extra harness argument. The build validates SHA-256 `2bbb042c7ebcb11647b87adfecd5629aba216f9eccc4bdc0495956457dfc0194` and stages it to a path without classpath separators. Its target upstream SHA-256 is `12a875cdc14200c3ea6e9d9ab14f754fbe4b5ad35521e6ac7daccc8b777e9b28`. Sound members alone are isolated; results are conditional on this fixture.

`SweepCompatibilityGameTests` checks installed references before exercising body/item/armor impulses, tool stamping and registered damage-event LIRM. Stable full blocks exercise `ShipMagneticScanner` immediately after assembly; this does not claim all special upstream blocks remain in place after their native tick logic. Existing external-emitter profile tests exercise the powered machines separately. Reborn's physical emitter test measures position over actual ticks.

Client evidence used the isolated `runExtraGolemsRebornClient` profile on an Xvfb display: spawn raw iron, copper, waxed oxidized copper, gold and netherite; inspect textures/models; Save and Quit; exit the process; restart with `-PrebornWorld=RebornAudit`; query every saved `Golem` ID and inspect again. Logs confirm all dimensions saved and all five IDs survived. Screenshots are supporting visual evidence, not a substitute for force assertions.

## Primary upstream sources

- [Original Extra Golems](https://www.curseforge.com/minecraft/mc-mods/extra-golems) and [Reborn 21.1.0.1](https://www.curseforge.com/minecraft/mc-mods/extra-golems-reborn/files/7922386), [Reborn source](https://github.com/bigenergy/extra-golems-reborn).
- [BOMD NeoForge port](https://modrinth.com/mod/bosses-of-mass-destruction-forge/version/snhDYBxP), [Aviator Dreams Reloaded](https://modrinth.com/mod/aviator-dreams-reloaded/version/4Aa8QEq9), [Immersive Aircraft](https://modrinth.com/mod/immersive-aircraft/version/ZZTlNkV9).
- [Cataclysm target](https://modrinth.com/mod/l_enders-cataclysm/version/PsPYpoCC), [Iron's Spells target](https://modrinth.com/mod/irons-spells-n-spellbooks/version/slKLosTb), [Aether target](https://modrinth.com/mod/aether/version/K5X5qMwG).
