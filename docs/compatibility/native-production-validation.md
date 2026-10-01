# Native integration production validation — October 1, 2026

These dedicated NeoForge 21.1.252 / Minecraft 1.21.1 GameTest profiles exercise
the upstream machines and damage producers, rather than only checking that our
recipes, tags, and damage IDs load. Each linked log includes the runtime mod list,
test names, and `All N required tests passed` summary. SHA-256 hashes and summary
times are in [results.json](evidence/native-production/results.json).

| Profile | Required tests | New behavioral evidence |
|---|---:|---|
| [Immersive Engineering 12.4.2-194](evidence/native-production/immersive-engineering.log) | 10/10 | Formed native Mixer consumes magnetite, plant oil, and FE to output ferrofluid; native Metal Press consumes the supplemental ingots and outputs their plates; powered native Tesla and both razor-wire damage modes stamp equipment with the expected lightning source. |
| [TFMG Community Edition 1.3.2a](evidence/native-production/tfmg-community-1.3.2a.log) | 20/20 | Native casting basins produce Gallium and Lithium from full buckets, with a native concrete/cinderblock control; formed industrial blast furnaces consume ore, flux, coke fuel, and hot air and produce steel, slag, and gas; native deploying and winding complete motor and generator assembly. |
| [Original TFMG 1.2.0](evidence/native-production/tfmg-original-1.2.0.log) | 20/20 | The same casting, blasting, and sequenced-assembly checks pass at the optional dependency's supported minimum. |
| [Create: New Age 1.2.0](evidence/native-production/create-new-age.log) | 15/15 | Powered native mechanical crafting produces generator coils; crafting-table and Energiser paths consume their inputs and produce the supplemental motor and permanent magnet. |
| [Create Crafts & Additions 1.6.0](evidence/native-production/createaddition.log) | 7/7 | Native mechanical crafting produces motors and alternators, Create mixing produces ferrofluid from seed oil, and a powered native Tesla attack consumes FE and stamps equipment. |
| [Create Ore Excavation 1.6.8](evidence/native-production/create-ore-excavation.log) | 4/4 | A native drill produces each of our ten solid vein resources and a native extractor produces Helium-3; both debit native vein data. Four actual server resource reloads exercise per-vein and master disable, restoration, and the original configuration. |
| [Cosmonautics 26.08.307](evidence/native-production/cosmonautics.log) | 6/6 | On an assembled Sable ship, the native rocket thruster accepts and consumes source and flowing Hydrogen in separate tests and accelerates the rigid body. |
| [Optional mods absent](evidence/native-production/compat-absent.log) | 2/2 | The integration hooks do not require the optional runtimes to start. |

The COE fixture selects each native vein and supplies rotation, then lets the
actual drill or extractor tick and produce its output. It does not claim to test
random vein discovery. The TFMG tests revealed that upstream casting tanks hold
90 or 144 mB while our Gallium and Lithium recipes cost 1000 mB. A narrow
optional adapter now permits a full bucket for those two fluids and retains the
upstream capacity for other casts. The sequenced-assembly recipes start with
distinct operations so upstream sequences do not intercept them. The
Cosmonautics guard skips the published orbit callback when Deep Space is absent;
local ship physics and the thruster continue ticking.

Reproduce with `bash scripts/run-gametest-gate.sh <Gradle task> <run directory> 300`.
The profile pairs are `runImmersiveEngineeringGameTestServer` /
`run-immersive-engineering-gametest`, `runTfmgGameTestServer` /
`run-tfmg-gametest`, `runCreateNewAgeGameTestServer` /
`run-create-new-age-gametest`, `runCreateAdditionGameTestServer` /
`run-createaddition-gametest`, `runCreateOreExcavationGameTestServer` /
`run-create-ore-excavation-gametest`, `runCosmonauticsGameTestServer` /
`run-cosmonautics-gametest`, and `runOptionalCompatibilityAbsentGameTestServer` /
`run-compat-absent-gametest`. For original TFMG, append
`-Ptfmg_test_dependency=maven.modrinth:create-tfmg:uDi14nbt`
`-Ptfmg_test_mod_version=1.2.0` to its gate command. The default TFMG profile
uses Community Edition 1.3.2a.

`./gradlew test build --no-daemon` also passed; see the [build log](evidence/native-production/build.log).
