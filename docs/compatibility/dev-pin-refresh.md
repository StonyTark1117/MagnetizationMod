# Development and compatibility-test pin refresh — October 1, 2026

This refresh updates development/test artifacts for Minecraft 1.21.1 NeoForge.
Published user dependency ranges remain unchanged. It does not expand the
integration feature set or certify behavior beyond each existing fixture.
Historical audit results retain the versions they actually exercised. Validation
uses an isolated export of `6ecea04` plus this change, excluding concurrent UI
and native-production work in the main checkout.

| Package | Previous development/test pin | New pin |
| --- | --- | --- |
| Create | 6.0.11-295 | 6.0.11-312 |
| Aeronautics / Simulated / Offroad | 1.3.0 | 1.3.2, official bundle `44pLdPGg` |
| Copycats+ | 3.0.4 | 3.0.9, `bPYeUWZx` |
| Dragons Plus | 1.11.7b | 1.11.9, CF file 8900055 |
| AE2 | 19.2.17 | 19.2.18, `KDnFUmMm` |
| GuideME | 21.1.17 | 21.1.19, `hFpGwC6q` |
| Sodium | 0.8.13-beta.2 | 0.8.13 stable |
| JEI | 19.44.0.401 | 19.51.0.418 |
| AeroPortals | 1.3.0 | 1.3.3, CF file 8876165 |
| Coasters Simulated | 0.1.4 | 0.1.5, CF file 8655742 |
| Track Styles | 1.0.0 | published 1.1.0-hotfix1 / runtime 1.1.0, CF file 8672156 |
| Coasters: Engineered | 1.0.1 | 1.1.0, CF file 8761537 |
| CBC Aeronautics Missiles | 0.1.0 | published 0.2.0 / runtime 0.1.0, CF file 8746555 |
| Immersive Aircraft | dev 1.4.6 / test 1.5.2 | both 1.5.2, `ZZTlNkV9` |
| Supplementaries | dev 3.8.9 / test 3.9.9 | both 3.9.9, `WrZWfRjP` |
| Moonlight | dev 3.3.3 / test 3.6.8 | both 3.7.0, `t6iFh4M3` |
| Sophisticated Storage | dev 1.5.85.2077 / test 1.5.91.2127 | both 1.6.0.2136, `GvOv4X8M` |
| Sophisticated Core | dev 1.4.86.2259 / test 1.5.1.2341 | both 1.5.2.2343, `blXGSmAb` |
| Spartan Weaponry Unofficial | 1.2.2 | 1.2.3, `ygaWB8ji` |

TFMG was refreshed separately to **Community Edition 1.3.2a**, with the original
1.2.0 minimum retained and tested. See [its artifact-specific evidence](tfmg-pin-update.md).

## Artifact handling and minimum versions

Aeronautics 1.3.2 is published as a jar-in-jar bundle; standalone Maven 1.3.2
coordinates are unavailable. Gradle verifies the pinned bundle's SHA-256 and
extracts its unmodified Aeronautics, Simulated and Offroad jars for development.
No upstream classes or artifacts are copied into the release JAR. Version
property overrides retain the standalone Maven path for older supported builds.

The Engineered fixture calls the native 1.0.1 and 1.1.0 API shapes through a
small reflective bridge. Production integration code is unchanged. The fixture
continues to require standard FE access, actual EMP draining, energy starvation,
acceleration consumption and regenerative braking.

Published minimums, including Create 6.0.10, Aeronautics/Simulated 1.3.0,
AeroPortals 1.1.2, Coasters 0.1, Engineered 1.0.1 and JEI 19.27.0.336, are retained.
The mod's own NeoForge floor remains 21.1.200; upstream dependencies can impose a
higher effective floor for an installed pack. The Aeronautics bundle requires
NeoForge 21.1.228 and Create 6.0.10. A newer development pin does not itself
justify increasing users' required versions.

## Deliberately retained pins

- NeoForge 21.1.252, Sable 2.0.5, Ponder 1.0.87 and Flywheel 1.0.6 remain current
  selected releases; Registrate stays on the build required by Create.
- Create retains its existing development-build channel. Other ordinary pins
  prefer stable 1.21.1 NeoForge releases: Rails 0.2.1 and Enchantment Industry
  2.4.2 are retained rather than switching to their newer beta channels.
- Iris retains the existing 1.8.14-beta.1 Sodium-0.8-compatible build; the older
  stable Iris requires Sodium 0.6.13. Its existing development metadata handling
  is unchanged.
- Historical issue-7 reproduction/benchmark pins retain ModernFix 5.27.20 and
  Lithium 0.15.4 to reproduce that environment.
- AeroPortals 1.1.2, Coasters 0.1 and JEI 19.27 remain explicit minimum fixtures.
  Deprecated Magnetics stays on the exact upstream-crash reproduction artifact.

## Client smoke fixture adjustment

Track Styles 1.1.0-hotfix1 contains fourteen invalid resource filenames: six
legacy model files moved under `track_v1`, seven uppercase `arrow_4D` files,
and the existing texture copy. The startup smoke retains an exact, counted
allowance for these upstream files and the older seven-file 1.0.0 set. The
[artifact inventory](evidence/dev-pin-refresh/track-styles-invalid-resources.json)
records the new filenames and jar hash. A partial set or any unexpected
resource error still fails. This does not certify every track's rendered model.

## Verification

Results are recorded under [evidence/dev-pin-refresh](evidence/dev-pin-refresh).
The [validation summary](evidence/dev-pin-refresh/validation-summary.json) records:

- **44/44 server profiles, 321 required test passes**, including the 173-test core.
- **255/255 unit tests**, release-JAR contents and runtime-coupling checks.
- **173/173 core tests** on the retained minimum stack: NeoForge 21.1.228,
  Create 6.0.10-281, Aeronautics/Simulated 1.3.0 and Sable 2.0.3.
- **2/2 Engineered tests** on both 1.0.1 and 1.1.0; **16/16 TFMG tests** on
  original 1.2.0 and Community Edition 1.3.2a. Minimum AeroPortals 1.1.2 and
  Coasters 0.1 profiles also pass.
- Two dedicated-server processes preserve moving-ship storage and verify
  extraction plus continued native smelting on the updated Storage/Core pair.
- Engineered, Track Styles and Missiles client startup passes; JEI 19.27.0.336
  plus JER registers all 28 charts. Track Styles' first stale-allowance failure
  is retained separately from the corrected pass.
- All **40 published dependency relationships** and minimum properties remain
  unchanged; the release JAR contains no bundled upstream dependencies.

The known pulley diagnostic and deprecated Magnetics default-off upstream-crash
reproducer are excluded from passing totals. This pin refresh adds no adapters
for those upstream issues.

The full development client reaches the menu with the updated dependencies
loaded. Its strict error-log gate remains **failed** on eight upstream missing
subtitle translations in Supplementaries, Immersive Engineering and Alex's Caves;
the separately ignored headless narrator error is also retained in the log.
[Final client result](evidence/dev-pin-refresh/full-client-result.json). These
are recorded upstream resource limitations, not a claim of an integration crash
or a clean error log. No upstream subtitle patches are included.

Each result identifies the executed profile, actual runtime log and hash. A
passing registry or recipe fixture is not a claim of native machine production;
this dependency refresh does not close the existing behavior-audit gaps.

## Reproduction of retained minimums

```sh
bash scripts/run-gametest-gate.sh runCoastersEngineeredGameTestServer \
  run-coasters-engineered-gametest 360 -Pcoasters_engineered_test_file=8653100
bash scripts/run-gametest-gate.sh runGameTestServer run-gametest 420 \
  -Pneoforge_version=21.1.228 -Pcreate_version=6.0.10-281 \
  -Paeronautics_version=1.3.0 -Psimulated_version=1.3.0 -Psable_version=2.0.3
```

The fresh minimum-core environment may need a separate initial Gradle preparation
step before the bounded runtime gate when NeoForge artifacts are not cached.
AeroPortals and Coasters minimums use their existing `LegacyGameTestServer`
profiles; `runJerClient` deliberately uses the published JEI minimum.
