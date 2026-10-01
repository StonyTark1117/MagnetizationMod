# TFMG artifact attribution and development pin — October 1, 2026

The development and compatibility-test dependency is now **TFMG Community
Edition 1.3.2a**, a separate community fork of original TFMG. Both distributions
use mod ID `tfmg`; the profiles load exactly one. The optional user requirement
remains **`[1.2.0,)`**. No production API change or minimum-version bump was
needed, and the release JAR does not bundle either distribution.

## Corrected historical attribution

The September 30 Sable 2.0.5 profile passed 15 required tests against **original
TFMG 1.2.0**, not Community Edition 1.3.1. The preserved
[runtime log](evidence/tfmg-pin-update/historical-original-1.2.0.txt) declares
`Create: The Factory Must Grow 1.2.0 (tfmg)` and has SHA-256
`3f87a7d77a58f7c127f88a2023e932ad144a6089fd47b0aa9483237a99cb1aab`, exactly
matching the existing final regression matrix. That matrix's result/count/hash
are unchanged; explicit artifact attribution has been added.

Community Edition 1.3.1 was also present in the earlier static artifact inventory.
Its presence did not prove that it ran in the dedicated profile. Historical
inventory records retain their original artifact identities; they are not
retroactively relabeled as the new version or claimed as behavior passes.

## Pins and results

Environment: Minecraft 1.21.1, NeoForge 21.1.252, Java 21, Create 6.0.11-295,
Sable 2.0.5. Validation used an isolated snapshot of commit `9d6d6fe` plus this
pin/fixture change, excluding concurrent unrelated worktree edits.

| Distribution | Exact coordinate / published filename | Dedicated-server result |
|---|---|---|
| [Original TFMG 1.2.0](https://modrinth.com/mod/create-tfmg/version/uDi14nbt), retained minimum | `maven.modrinth:create-tfmg:uDi14nbt` / `tfmg-1.2.0.jar` | **16/16**, [log](evidence/tfmg-pin-update/original-1.2.0.txt) |
| [Community Edition 1.3.2a](https://www.curseforge.com/minecraft/mc-mods/tfmg-community-edition/files/9013465), new default development/test pin | `curse.maven:tfmg-community-edition-1653026:9013465` / `tfmg-1.21.1-1.3.2a-community.jar`; internal version `1.3.2a-community` | **16/16**, [log](evidence/tfmg-pin-update/community-1.3.2a.txt) |

Artifact SHA-256:

- Original 1.2.0: `fc824a91cfe22e137c9014ed608ee9bf35f16490d2162b687666332c6eb3a555`
- CE 1.3.2a: `e9e84f7507b0b482d176c7f04eb924ed1396637f480d596a8b2d266fa51640d0`

The [machine-readable record](evidence/tfmg-pin-update/results.json) includes
descriptors, upstream dependencies, runtime log hashes, the historical correction,
the initial failed fixture, and release checks. **255 unit tests**, release-JAR
validation and the runtime-coupling check pass. The packaged dependency remains
optional with range `[1.2.0,)`. The separate optional-mod-absent profile passes
**2/2** ([log](evidence/tfmg-pin-update/optional-absent.txt)).

## Fixture correction and coverage boundaries

The first CE run passed 15 checks and failed one because it required
`tfmg:converter`, which CE removed. The corrected material fixture requires the
Converter and its magnetic/conductive tags on original 1.2.0 and explicitly
requires its absence on CE. Its optional production tag entries remain useful
for original users. Missing IDs are not silently treated as successful coverage.
No new CE machines or materials are added to the integration by this pin update.

The additional sixteenth check asserts the loaded `tfmg` version equals the
profile's expected version. The existing checks cover magnetic materials and
magnet slots, Hydrogen/Lithium interoperability, cooling controls, all eleven
virtual-gas profiles and vent identity/recovery, gas excitation, molten-steel
MHD response, the opt-in Polarizer field, recipes and configuration conditions.
Recipe checks establish registration/ingredients or the specific direct recipe
operation asserted by the fixture; they do **not** establish native casting,
industrial blasting or sequenced-assembly production. These runs do not add
client-rendering or full-modpack certification claims.

## Reproduction

Default Community Edition profile:

```sh
bash scripts/run-gametest-gate.sh runTfmgGameTestServer run-tfmg-gametest 300
```

Original 1.2.0 minimum profile (override both artifact and expected version):

```sh
bash scripts/run-gametest-gate.sh runTfmgGameTestServer run-tfmg-gametest 300 \
  -Ptfmg_test_dependency=maven.modrinth:create-tfmg:uDi14nbt \
  -Ptfmg_test_mod_version=1.2.0
```

The `tfmg_test_dependency` and `tfmg_test_mod_version` properties are development
settings. They do not change the installed user's minimum dependency range.
