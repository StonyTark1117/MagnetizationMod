# Performance audit follow-up October 1 2026

Goal: address all seven items in the October 1 audit, preserve gameplay, run a matched performance comparison, commit and push. Baseline source is `1d479047812b`, audit evidence `df0e2cc7`; work branch is `codex/audit-optimizations-2026-10-01`. The version stays 1.4.6 while unreleased; artifact hashes identify builds.

| Audit item | State and evidence |
|---|---|
| 1 Config override hot path | Server/no-snapshot reads bypass lock and key construction; active snapshots retain locking and cache immutable keys only. Snapshot replacement, local reload override, empty snapshot and disconnect restoration tested in the 173/173 core suite. |
| 2 Optional entity checks | Entity/mod eligibility precedes config reads; live movement/polarity/config reads remain. Slugterra runtime 9/9 including real deflection and mounts passed. |
| 3 External adapter checks | Implementation in progress; cache immutable adapter identity, retain one live enable decision per candidate. |
| 4 Fluid regions and anchor iteration | Pending |
| 5 Native point queries | Pending |
| 6 Gas traversal and stable/churn comparison | Pending |
| 7 Shaft/docking scaling | Pending |
| Matched standard and JFR comparison | Pending; retain baseline fixtures and add targeted native-query/gas/shaft coverage on both revisions. |
| Live full-pack verification and final deployment | Pending |

Gameplay gates must cover force results/order, live configuration and power, chunk/lifecycle changes, gas grace/energy/topology behavior, and shaft conflicts/reversals. Faster timing alone is not acceptance evidence.
