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

Revision after first candidate profiles:

- Candidate v1 (`5e1adb0c`, SHA77e57477…) normal-speed profiles show gas
  production3.251→2.077ms/tick, shafts0.946→0.717, ships1.919→0.756.
  Shaft manager itself0.891→0.687; Lenz scan0.438→0.124. Coaster classification
  no longer appears. Dense fields1.357→1.349 is not a demonstrated gain.
- Removed ineffective25-cell signal state cache, retaining shared Directions.
  Added static projectile-type classification to existing per-tick target
  snapshots so ordinary targets avoid repeated registry lookups per field.
  Live deflection config/flight checks remain in the actual adapter.
- Revised production/test changes committed as `0845f4b1`. Build/unit tests pass;
  core176, CNA20, IE10, Alexs7, CreateAddition7, TFMG20 rerun; Slugterra absent1
  and focused installed-port projectile1 pass. Unique passing gates total255.
- Installed Slugterra JAR hashb5e60931… downloaded to ignored build evidence.
  The old broad suite fails7 tests both with and without the new shortcut.
  Normalized failure comparison is `slugterra-control.json`; do not call that
  suite passing. Focused test verifies both actual base projectile protocols,
  same-tick flight-state changes and ordinary item force.
- Preserved revised artifact `build/reports/server-fixes-2026-10-01/candidate-v2.jar`,
  SHA `2e130d1bfd9b02b099d0bff8a5bb968824d3a82c5d2c5fa6fb3768bb42413f53`.
  Manifest `candidate-v2-build.json` pins source and hash.
- First candidate runner remains live (exec session63814). It still owns the
  server. Wait for its CANDIDATE_COMPLETE marker/terminal process before starting
  `/tmp/mag_six_candidate_v2.py` with log`/tmp/mag-six-candidate-v2.log`.
- V2 runner repeats matched10,4steady profiles+bulk, then additional20. It uses
  a separate `candidate-v2` evidence directory and updates final installed-artifact
  manifest. Analysis script `/tmp/mag_six_analyze.py` includes both candidates
  and prefers v2 comparisons once its summary exists.
- Full-pack gameplay runner `/tmp/mag_six_gameplay.py` now requires V2 completion.
  It executes37 same-function fluid assertions; lever cases explicitly trigger
  a neighboring inert-block update (command placement does not invoke player-use
  callbacks), then assert indirect power. Restore runner remains prepared but
  must run only after final measurements/gameplay checks pass.

Latest fluid refinement (supersedes the staged v2 artifact above):

- V1 bulk worst tick11,934.01ms versus baseline15,304.94ms: an improvement, but
  still a severe hitch. Combined graph and external-input discovery in one
  neighbor loop and reused temporary neighbor positions; callbacks, solve/write/
  notification ordering and synchronous completion remain intact.
- Latest source`8217baf4`; latest v2 JAR SHA
  `b81e113f59ca87afd0f0517ef7c2bcd49a036c4acd03b377de601bca52f0c60a`.
  Core176 and build/unit293 passed again after this refinement. The staged
  candidate-v2.jar and candidate-v2-build.json now contain this latest version;
  the earlier unused build is retained as candidate-v2-before-fluid.*.
- V1 moving-ship profile: Lenz count scan0.438→0.124ms/tick; coaster classification
  absent from samples; sampled total production1.919→0.756. Four steady captures
  and the bulk capture decoded and validated (start timestamps within5seconds,
  exact4ms sampling interval, duration/tick counts, zero players, and shaft/ship
  activation counts where applicable).
- V1 runner is finishing additional20 throughput scenarios. No V2 server run or
  full-pack gameplay run has started yet. Do not restore the server until final
  V2 measurements and gameplay assertions have passed.

Updated Slugterra dependency (user steering):

- V1 completed all30 scenarios. The runner stopped with exit143 after144/150
  samples; verified live JAR/world/player count, then resumed the six remaining
  samples after a fresh warmup. Its five captures completed before interruption.
- Private Slugterra releasev1.0 source590f1a02, published September30, supersedes
  unofficial.1. SHA b2388d688b2902a5931a3c8be44a997fdc87789a192c31a64599b2eda591c736
  verified against release checksum. Broad9 and focused1 GameTests pass. Unique
  passing GameTests now264; earlier old-port failures remain documented.
- Updating the disposable server with old Slugterra JAR retained disabled. A new
  baseline-updated run uses the original Magnetization baseline JAR and new port.
  Final candidate-v2 will use the same updated pack. Do not compare these as a
  matched pair with old-port baseline/candidate captures.
- V2 execution split into matched sprints, five captures, then remaining20
  scenarios with completion checks between sessions. Gameplay/restoration follow.

Updated-pack baseline complete:

- All50 targeted sprint samples and five Spark captures completed; captures
  decoded with expected4ms interval, timings, zero players and activation counts.
- Worst bulk tick15,257.96ms. Normal sampled production costs: dense1.617,
  gas3.826, shafts0.654 and ships1.399ms/tick. Profiling attribution varies more
  than whole-workload timing; do not overstate small differences.
- CandidateV2 deployment/matched-sprint stage now running. It verifies the new
  Slugterra hash before deployment and Magnetization hash after upload.
