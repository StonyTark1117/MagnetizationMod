# Full-pack optimization pass — completed

See [the final report](report.md) for measured gains, unchanged results, remaining
limits and exact build/dependency hashes.

- Implemented all six targeted changes on `codex/fullpack-stress-fixes-2026-10-01`.
- Final production source: `8217baf4586d4e594967283cf483373381af63d4`.
- Updated Slugterra from unofficial.1 to the verified private v1.0 release.
  All nine broad compatibility tests and the focused projectile test pass.
- Repeated baseline and candidate measurements on the same updated full pack.
- Final candidate: 30 scenarios, 150 samples, 180,000 measured sprint ticks,
  four 180-second normal-speed captures and a 120-second bulk capture.
- Gas sprint median improves 21.4%; bulk peak improves 38.8% but remains 9.33 s.
  No overall dense-field, Lenz-scan or shaft speedup is demonstrated in the final
  pair. The report preserves higher sampled costs and the 149.24 ms ship-run tick.
- Build/release verification, 293 unit tests, 264 GameTest cases and 37 full-pack
  immediate fluid assertions pass in their recorded scopes.
- Original world/properties/JVM arguments/whitelist/ops restored; fixture disabled.
  Installed Magnetization and Slugterra artifacts verified by server readback.
- No historical-crash causality claim. Player/network, survival and long-soak
  behavior remain outside this controlled stress experiment.

Intermediate candidates, the interrupted/resumed first-candidate run and old-port
failures remain recorded in Git history and the experiment/control manifests.
Private configuration backups and raw binaries remain in the ignored local build
report directory; no credentials are committed.
