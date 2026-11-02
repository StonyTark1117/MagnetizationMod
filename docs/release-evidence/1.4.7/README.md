# Magnetization 1.4.7 release audit — 2026-11-02

Version 1.4.7 adds one client setting, `visuals.gogglesShiftInspectionEnabled`,
enabled by default. The switch gates the sneak-held magnetic ship inspection
requests, HUD panel, and force arrows. Other Create-goggles information and
magnetic shaft visualization use separate paths.

## Checks

| Check | Result |
|---|---|
| `./gradlew releaseGate --no-daemon --max-workers=1 --console=plain` | Passed |
| Unit tests | 293 passed, no failures or skips |
| Core and regression GameTests | 176 and 13 passed |
| Minimal dedicated server | Reached ready state and stopped cleanly |
| Release JAR verifier | Passed; JAR metadata reports 1.4.7 |
| Client startup with setting `false` | Rendered the main menu with Magnetization, Create, and Sable loaded |
| Configuration reference, language JSON, whitespace | Generator `--check`, JSON parse, and `git diff --check` passed |

The client smoke check establishes startup with the new setting disabled. The
inspection behavior is also supported by code review of the shared
`inspecting()` gate; this run did not exercise an in-world ship. The 1.4.6
compatibility matrix was not rerun for this one-setting update. Its documented
intermittent Create/Registrate development-startup issue remains an upstream
dependency issue and is not a release blocker.

Use the green CI build from the final tagged commit as the publication JAR.
The locally built JAR is not substituted for it because the local OpenJDK and
CI Temurin builds produced different class bytes in 1.4.6.
