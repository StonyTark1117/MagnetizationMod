# Complete Ponder catalog playback

The Minecraft 1.21.1 / NeoForge native client passed all **32 scenes**, **125 instruction captures** and **51 advertised item registrations** at GUI scale 3. The shipping catalog contains 157 English header/instruction keys, checked against the exact scene text by unit tests. Optional Steam 'n' Rails and Copycats+ scenes were enabled in this validation profile.

Every scene ran uninterrupted from start to completion. The auditor compiled exactly one matching scene from each advertised item, checked localized instruction text, and verified the scene's initial/final native world state. Custom workflow auditors additionally check configuration, block-entity and item states at each instruction; this does not imply that every caption in an older generic scene has a separate world-state assertion.

All 125 instruction frames were visually reviewed. That review found five presentation issues: the launched Remote rider, Fusion panel roof and Railgun rail corner reached the title area; the Railgun and rare-earth captions reached the footer; and the Copycats goggles hint covered part of its title. Camera scale, caption pointer position and hint orientation were adjusted. All five affected scenes then passed complete native replays at **GUI scales 2 and 3**, with all 16 instruction frames reviewed at each scale. The focused replays supersede their earlier frames for final visual acceptance. The baseline sweep's source hashes and the final source hashes are recorded separately.

The new docking/control workflows have [focused acceptance and server behavior evidence](dock-control-ponder.md). The concurrent heat/power workflows have [their own acceptance and evidence](thermal-power-ponder.md). The final build and release-JAR check passed with 292 unit tests. Native server suites passed 173 core, 13 regression, seven engineering and 11 Immersive Engineering profile GameTests.

Ponder ship movement, riders and force markers illustrate mechanics using scripted sections/entities. Native server tests separately exercise real ship capture, cooperative damping, remote launching, field force directions, Imprint interaction and equipment charging. Scene playback alone is not a real-physics test.

[Evidence and checksums](evidence/ponder-complete/results.json) include original native PNGs, full client/server logs, durations, instruction times, target registrations and source provenance. The complete sweep's `gui3/` evidence records the baseline; `layout-gui2/` and `layout-gui3/` record the final presentation repairs. Other new scenes' focused GUI-scale-2 captures are in the linked workflow reports. Connected manual gifting, crafting and reload checks also passed during these runs; the focused Ponder mode does not replace the [earlier full lifecycle/coaster validation](lifecycle-presentation-validation.md).

Reproduce with Java 21 and the development validation profile. Supply every scene ID from `PonderSceneCatalog` as a comma-separated `MAGNETIZATION_AUDIT_SCENE` value to run the complete catalog, and set `MAGNETIZATION_AUDIT_GUI_SCALE=3`:

```sh
bash scripts/run-lifecycle-presentation-audit.sh
```

The final layout check uses:

```sh
MAGNETIZATION_AUDIT_SCENE=railgun_remote,fusion_panel,railgun_pair,rare_earth_magnets,copycat_magnetism \
  MAGNETIZATION_AUDIT_GUI_SCALE=3 bash scripts/run-lifecycle-presentation-audit.sh
```

Repeat with scale 2, archiving `build/validation-audit` between runs.
