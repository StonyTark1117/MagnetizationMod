# Discopanel full-pack optimization validation — 2026-09-30

## Artifact and scope

Tested Magnetization 1.4.6 built from committed source `10ace51061f93b9a4028b0d3e4de394414d1b440`. SHA-256: `58d7a5cba43b582ed7d5312c1c739411a0c8135f174d35942cf61f004e307bd4`. The uploaded JAR was read back and its checksum verified. Later concurrent working-tree changes are outside this artifact.

The full Create Magnetized pack uses Minecraft 1.21.1, NeoForge 21.1.252, Create 6.0.10 and Sable 2.0.5. The existing overworld reported 1,746 block emitters and five Sable ships. No real players were connected during these tests.

This validates workload reduction and deployment. It does not establish the cause of the previously reported crash, nor does it claim that a fresh-world profile demonstrated an earlier unimplemented fix.

## Deployment adjustments

The original world, configuration, startup arguments, Sable native cache and Magnetization 1.4.5 JAR were backed up under `/codex-optimization-backup-20260930` on the server. The copied world `/codex-optimization-test-20260930` hosted the disposable fixtures in a separate `magnetization_stress:lab` dimension. The original world was not used for fixture placement or clearing.

The pack's `create-magnetized-golem-compat-0.1.1.jar` required exactly Magnetization 1.4.5 and blocked startup. It was moved to the rollback backup. Magnetization 1.4.6 already contains the tested native Extra Golems Reborn adapter. The old mixin also targets the previous intrinsic-response implementation and should not be retained by simply broadening its version range. Clients upgrading to 1.4.6 must remove that obsolete add-on too. The separate Create Magnetics server workaround remains installed.

Panel copies did not preserve writable ownership for the server's UID. A temporary entrypoint corrected ownership of the test-world directory only; the original Docker overrides were restored afterward. Clearing the panel's `level` value did not undo the persisted `level-name`, so the final deployment explicitly sets `level=world`. Diagnostic JVM flags and the temporary entrypoint were removed.

## Full-pack workload checks

All **58 assertions across 18 scenarios** passed. Each recorded sample spans 200–201 real-time ticks, following five seconds of setup/settling. These are activation and operation-count checks, not a statistically stable per-scenario timing benchmark.

- Unequipped synthetic player: 40 MR checks skipped; zero MR field searches.
- MR chest armor, main-hand tool and off-hand tool: field searches remain active. Cycling equipment exercises both skip and search paths.
- Ordinary cows: 3,200 entity inspections, zero eligible targets and zero external field applications.
- External magnets without targets: zero field applications.
- Plain ferrofluid: 512 sources across four occupied chunks; zero native/external chunk-search attempts when those emitter categories are absent.
- Ferrofluid near a native emitter: **16,900 native insertion attempts per pass**, compared with the old loop's arithmetic of 512 × 4,225 = **2,163,200**. This is 128× fewer attempts, not a claim of 128× faster total ticks.
- Ferrofluid near external magnets: 100 external insertion attempts per pass; 512 discovered fluid entries become four target regions per tick. Fields still apply.
- Dense external magnets: **256 applications per tick**, preserving the existing budget. This does not establish fairness beyond the unchanged 2,048-candidate cap.
- Active native emitters, railguns, air separators, gas churn and mixed machinery completed as runtime smoke scenarios. Their counters do not independently prove every machine's gameplay behavior or throughput.
- Empty ending fixture returns to zero field work. No repeated reflective adapter lookup after warmup in any scenario.

See [work-counts.json](work-counts.json) and [assertions.json](assertions.json). No new runtime errors appeared during the fixture interval. The pack emits unrelated warnings/errors during startup; startup is not claimed to be warning-free.

NeoForge disables GameTests in production mode, so `/test` was unavailable even with the enable flag. The previously completed local GameTest results remain local evidence. These server fixtures invoke the real MR handler through a synthetic player, but do not exercise network traffic, human play, login, exploration or multiplayer behavior.

## Original-world Spark comparison

[1.4.5 baseline](https://spark.lucko.me/0jE8vM6aTA) and [1.4.6 candidate](https://spark.lucko.me/fdiW87vGp2) each captured approximately 90 seconds / 1,800 ticks with diagnostics disabled, zero connected players and 846 loaded chunks. The candidate warmed for two minutes after the original world restarted. Both maintained 20 TPS. Entity counts differed slightly (47 before, 49 after), so this is an operational comparison, not an identical frozen-state experiment.

| Metric | 1.4.5 | 1.4.6 first | 1.4.6 repeat |
|---|---:|---:|---:|
| Magnetization inclusive sampled time per tick | 1.296 ms | 1.304 ms | 0.813 ms |
| ExternalEmitterTracker inclusive sampled time per tick | 1.193 ms | 1.273 ms | 0.671 ms |
| Captured-window MSPT medians | 8.473–8.487 ms | 8.227–8.257 ms | 8.198–8.208 ms |
| Last-minute mean MSPT at capture end | 8.682 ms | 8.491 ms | 8.521 ms |

The [repeat capture](https://spark.lucko.me/pj9PsIAzoK) also spans 90 seconds / 1,800 ticks, with the same 49 entities, 846 chunks and 20 TPS. Its mod samples are substantially lower, while whole-server median tick time remains similar. It includes an autosave and a 226.8 ms maximum tick.

**These captures do not establish a stable magnitude of reduction in total idle Magnetization time.** The first candidate was effectively unchanged; the repeat was lower. Do not select only the favorable repeat or attribute whole-server timing changes solely to this mod. The targeted empty-category, equipment and fluid deduplication fixes pass, but the remaining active external-field work dominates here. The instrumented copy showed approximately 256 field applications each tick in the overworld, with five existing ships keeping discovery active. No ferrofluid sources were present in that overworld sample.

The candidate's tracker samples concentrate in `applyBudgeted`, particularly Create: New Age field construction/redstone reads and `FieldApplicator.apply`. This identifies remaining pressure; it does not prove which evaluations could safely be skipped. A follow-up optimization should measure candidates rejected by actual three-dimensional field bounds before expensive live redstone evaluation, including ship bounds and all other eligible target types. Preserve relay behavior, rotating budgets, same-tick redstone/config/block changes and chunk-unload behavior. Do not lower gameplay budgets or add a blanket per-tick field cache just to improve the timing.

Inclusive mod time sums only the outermost Magnetization frames, avoiding nested-frame double counting, and divides sampled milliseconds by the profiler's captured tick count. It is a sampling estimate, not exact CPU accounting. Capture-window statistics are distinguished from Spark's rolling 1/5-minute summaries; the candidate's five-minute summary includes startup and is unsuitable for this comparison. This upgrade also includes the committed compatibility integration, so it does not isolate each optimization's causal effect.


The deployment remains on 1.4.6 in the original world, with backups retained and no diagnostic flags. Evidence above supports the targeted fixes and continued full-pack testing; a real-player gameplay session and a more controlled attribution benchmark remain separate follow-ups.
