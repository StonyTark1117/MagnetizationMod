# Changelog

## 1.1.0 — Whole-ship polarity + physics overhaul

### New: ship polarity & susceptibility
- Ships now have a magnetic polarity (NORTH by default; flipped by parity of Polarity Inverters on board — 1 = SOUTH, 2 = NORTH, etc.) and a susceptibility multiplier derived from ferromagnetic blocks and magnet emitters on board.
- Magnets on a ship contribute to susceptibility but **never** to polarity — toggling a mounted electromagnet north↔south will not invert and tear apart the contraption.
- Surfaced on Create goggles, Jade, WTHIT, and TOP overlays: `On ship: NORTH ×1.4 (12 ferrous, 3 magnets, 1 inverter)`.
- Five config knobs: `shipBaselineSusceptibility`, `shipPerFerrousSusceptibility`, `shipPerMagnetSusceptibility`, `shipMaxSusceptibility`, `shipScanIntervalTicks`.

### Physics overhaul
- **Torque from off-center impulses**: extended ships now rotate naturally when forces are applied off the center of mass (`τ = r × F`, ∆ω via the sub-level's inverse inertia tensor).
- **Multi-sample field integration**: each emitter samples a 3×3×3 grid across the target ship's AABB by default (configurable 1–7), so non-uniform fields produce realistic torque on large ships. The per-tick acceleration cap is applied once across the summed force and distributed proportionally — preserving the relative magnitude of each sample.
- **Linear drag**: ships under sustained magnetic pull settle to a terminal velocity instead of accelerating without bound (`shipLinearDrag`, 2 %/tick default).
- **Cumulative per-ship-per-tick budget**: multiple emitters touching the same ship now share the acceleration cap.

### Fixed
- **Rotation bug**: magnets on simulated contraptions no longer stick to the cardinal direction they were placed in — both the field origin and axis now follow the ship's pose. (Root cause: `SableBridge.promoteToWorldSpace` was re-querying the host via a sub-level-local blockpos against the outer world level, silently failing the lookup and leaving the field in local space. Fix: takes `Pose3dc` directly from `host.logicalPose()`.)
- **Wrong-frame impulses**: `addLinearAndAngularVelocity` operates in world frame, but our `dv`/`dω` were being computed in the ship's local frame. For un-rotated ships this looked fine; for rotated ships the impulse direction got rotated by the ship's own orientation, presenting as "spins near a magnet" and "repelled despite opposite poles". Now correctly transformed to world before applying.
- **Magnetizable entity tag too broad**: vanilla zombies, husks, drowned, skeletons, strays, wither_skeletons, vindicators, pillagers, ravager, and falling_block were all pulled by magnets regardless of whether they wore metal. Trimmed to `iron_golem`, `arrow`, `trident` — mobs in metal armor still get pulled via the armor predicate.
- **Slow polarity updates** when an inverter was broken: `shipScanIntervalTicks` default lowered 100 → 20 (5 s → 1 s), AND the inverter block now invalidates the ship state cache on place/break for instant polarity flips (~50 ms).
- **Advancement false-triggering**:
  - `first_emitter` was triggering on any `#magnetization:ferromagnetic` item (picking up an iron ingot completed the chain). Now requires one of the seven actual emitter items.
  - `inverted_quarry` description mentioned wrenching but the trigger was plain block placement. Now uses `item_used_on_block` with `#c:tools/wrench` on `magnetic_excavator`.
  - `dual_magnetized` triggered on any `#magnetization:metal_armor` (including plain vanilla iron). Now requires both a NORTH-stamped and a SOUTH-stamped magnetized armor piece, matching the name.

### Tooling
- `/magnetization debug rotate <deg> [yaw|pitch|roll]` — teleport-rotates the nearest sub-level by absolute world-frame angle, for testing magnet behavior on arbitrarily oriented ships.
- Test count 13 → 50: new coverage on pose-transform math and the ship-state scanner.

## 1.0.1 — Worldgen hotfix
- Fixed silent worldgen breakage (custom biome modifier codec, `minecraft:ore_copper` was the wrong target).

## 1.0.0 — Initial release
- Magnets, anchors, electromagnets, tractor beams, repulsor coils, excavators, magnetic switches, polarity inverters.
- Ferromagnetic and magnetite ore/ingot/blocks.
- Magnetized armor and tools (per-tool signature abilities; lightning induces remnant magnetism).
- Magnetic Anomaly and Petrified Forest biomes (via TerraBlender).
- Field Compass, Magnetic Grapple, mob effect, particles.
- Create goggle, Jade, WTHIT, TOP, JEI, REI, and Ponder integrations.
