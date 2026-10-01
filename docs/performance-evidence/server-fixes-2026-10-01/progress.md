# Full-pack stress findings — implementation in progress

Scope: all six candidates from `../server-stress-2026-10-01/report.md`, gameplay
regression checks, server performance comparison, commit and push.

Branch: `codex/fullpack-stress-fixes-2026-10-01`, pushed through `5e1adb0c`, isolated from concurrent
Ponder/client edits in the original checkout. Base `00176d02`.

Initial implementation (not yet performance-validated):

1. Fluid redstone: primitive indexed breadth-first graph, queued attenuating
   fixed-point solver, per-recompute block-state reads, same cap/discovery/write
   order and synchronous notifications. No cross-tick topology/result cache.
2. Gas: native block-state-first lookup avoids unnecessary BE access; per-pass
   gas identity memoization, independently allocated on reentrancy and cleared
   before reuse. Existing component invalidation/energy/grace logic retained.
3. External fields: loaded signal query caches repeated block-state reads within
   one query and reuses direction constants. No budget/cursor changes.
4. Coasters: enabled field reaction short-circuits ship classification.
5. Lenz: block-state classification reused only within one scan; each position,
   loaded check and original 2,048-position cap/order retained.
6. Shafts: remove repeated lookup of registered shaft BEs during collection;
   preserve sorted ordering, unloaded entries, and live discovery/update cadence.

Verification so far:

- Final unit suite: 293 passed. `build verifyReleaseJar` passed. Candidate code
  committed through `5e1adb0c`; candidate JAR SHA-256
  `77e574774c78c047353c2a181e797d1e2dcae45f64aafaacb921a7c76b4633e6`.
- Differential signal solver test: 400 generated bounded graphs with cycles,
  multiple inputs and discarded cap edges match legacy relaxation; power removal
  starts fresh.
- Latest core GameTests: all 176 passed, including new immediate fluid source
  changes/split/rejoin, indirect strong power, and Lenz legacy-scan comparison.
- Initial direct run failed an unrelated separator visual test in the generated
  terrain world. Repository supervisor with its fresh flat fixture passed twice.
- Engineering 7, MR/engineering regressions 4, Simulated Coasters 2, Create New
  Age 20, Immersive Engineering 10, Alexs Caves 7, CreateAddition 7, and TFMG 20
  passed (253 GameTests total). Core176 and CNA20 were rerun after buffer/cache
  refinements. Full-pack baseline/candidate performance tests are in progress.

Live operations:

- Server original settings/world backed up in
  `build/reports/server-fixes-2026-10-01/` (local raw evidence, may contain private
  configuration; do not commit it).
- Server world archive `/codex-before-fullpack-fixes-20261001.zip`.
- Baseline JAR SHA-256
  `978c9673c6c775d5d41a33c462eb65f7ebad82b4387910b97b674afc3b3ad9e1`.
- Temporary world `codex-fullpack-stress-20261001`, fixture enabled, whitelist on.
- Baseline runner `/tmp/mag_six_baseline.py`; log `/tmp/mag-six-baseline.log`.
- Existing server helper scripts under `/tmp/mag_server_*.py` can be imported,
  but their `OUT`/`d.OUT` globals must be directed to this worktree's evidence.
- Prepared restore runner `/tmp/mag_six_restore.py` uses this goal’s backups and
  verifies the candidate artifact before restoring the original world/settings.
- Baseline targeted10 sprint scenarios and all five Spark captures completed;
  SHA/timestamps/durations/tick counts/player counts validated. Bulk worst tick
  15,304.94ms. Raw Spark URLs and hashes are in `profile-manifest.json`.
- Candidate runner now active (`/tmp/mag-six-candidate.log`); deploys preserved
  `build/reports/server-fixes-2026-10-01/candidate.jar` and checks server readback.
- Candidate runner matches those10 scenarios in the same order, then the same
  profiles and bulk capture, then20 additional regression-throughput scenarios.
- Prepared full-pack gameplay function checks37 immediate fluid states, without
  yielding between source edits and assertions; run only after profiling.

Do not report completion until paired measurements prove useful improvements,
all necessary gameplay gates pass, the original server world/settings are
restored with the final verified JAR, and commits are verified on the remote.

Latest measured progress:

- Matched50 candidate sprint samples completed. Gas median5.42→3.91ms/tick
  (−27.9%); ships7.44→6.99 (−6.0%). Dense2.99→2.97 and shafts2.68→2.64
  are too small to claim reliable whole-workload improvements.
- Candidate normal-speed profiles are running. Bulk/setup improvement, full-pack
  fluid gameplay assertions, additional20 regression scenarios, and restoration
  remain unverified. All six findings stay in scope; investigate weak/regressed
  paths after profile attribution is available.
