# Magnetization 1.4.6 final release audit — 2026-10-01

**Status at audit: release candidate accepted subject to green CI on the audit corrections.**
The current release candidate builds on source commit
`f9406084087e7ab16c73f6aae74e312d8ce0cc60`. The published CI run for
that base commit failed because the generated configuration reference was stale;
the regenerated reference passes its local `--check`. The full release matrix
also reproduced an intermittent Create/Registrate startup exception in a
development client profile. Existing traces show the unsafe Create/Registrate
concurrency path with Magnetization absent, and this exception is thrown by
Registrate in development mode. The exact corrupt mutation in this failing run
was not captured. This is a documented, nonblocking dependency issue for 1.4.6;
it is not evidence that the release JAR crashes on startup. See the
[first-failure excerpt](../../compatibility/evidence/remaining-work/registrate-recurrence-2026-10-01.txt)
and [investigation](../../compatibility/create-registrate-investigation.md).

## Checks on this candidate

| Check | Result |
|---|---|
| Unit tests and build | 293 passed; build passed |
| Core and regression GameTests | 176 and 13 required tests passed |
| Magnetic engineering GameTests | 7 required tests passed |
| Minimal dedicated server | Default 210-second observation, clean stop and log |
| Release JAR verifier | Passed; required metadata present, no build-only files or pack-specific runtime coupling |
| Configuration reference and whitespace | Generator `--check` and `git diff --check` passed after local correction |
| Release matrix, first run | 40 profiles passed; Ponder client stopped on a stale 16-core-scene assertion. The client had registered 30 core and two optional scenes. The assertion is corrected, and isolated Ponder plus JEI/JER rechecks passed. |
| Release matrix, full rerun | 40 profiles passed; Ponder client then crashed during Create registry startup before the scene assertion. JEI/JER did not run in this aggregate attempt but passed independently. |

The new JAR omits the clock-dependent `Implementation-Timestamp` manifest field.
Two forced rebuilds produced the same SHA-256,
`f605d758eab8d082115dc26eeb9daed272ccbf6063ba0f658166cc6e51bf616c`.
This is the audited local JAR hash; the earlier performance study identifies
its own distinct build hashes. The version metadata inside the JAR reports
1.4.6.

## Release boundaries

The [full-pack performance study](../../performance-evidence/server-fixes-2026-10-01/report.md)
records a 9.33-second bulk conductive-fluid hitch in its tested workload. The
[compatibility audit](../../compatibility/remaining-work-audit.md) also retains
the failing Supplementaries continuous-pulley payload workflow. Those are
documented limits, not passing claims for those interactions.

Before tagging, land the audit corrections and obtain green CI. The
Create/Registrate recurrence remains tracked as an upstream dependency issue;
a successful retry alone does not establish a fix. No `v1.4.6` tag exists at
this audit.

## Publication artifact update

The audit corrections and public release notes landed on `main`, and the
[release-notes commit's CI run](https://github.com/StonyTark1117/MagnetizationMod/actions/runs/36943581576)
passed. Its uploaded JAR has SHA-256
`44b5c03026234a2f5468b525f9a5ba1e2d7066de3d76792361ade660bf2a0c45`.
This is the canonical file selected for GitHub, Modrinth, and CurseForge.

The local OpenJDK 21 build retains the earlier `f605d758...` hash after a clean
rebuild. An entry-by-entry comparison found identical archive entries and
metadata; only three client golem-renderer classes differ, where the CI
Temurin 21 compiler emitted additional synthetic bridge methods. The local
rebuild check therefore establishes local repeatability, not an identical
cross-environment artifact.
