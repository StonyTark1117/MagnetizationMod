# Performance audit follow-up October 1 2026

Goal: address all seven items in the October 1 audit, preserve gameplay, run a matched performance comparison, commit and push. Baseline source is `1d479047812b`, audit evidence `df0e2cc7`; work branch is `codex/audit-optimizations-2026-10-01`. The version stays 1.4.6 while unreleased; artifact hashes identify builds.

| Audit item | State and evidence |
|---|---|
| 1 Config override hot path | Server/no-snapshot reads bypass lock and key construction; active snapshots retain locking and cache immutable keys only. Snapshot replacement, local reload override, empty snapshot and disconnect restoration tested in the 173/173 core suite. |
| 2 Optional entity checks | Entity/mod eligibility precedes config reads; live movement/polarity/config reads remain. Slugterra runtime 9/9 including real deflection and mounts passed. |
| 3 External adapter checks | Implemented immutable adapter identity cache and one live enable decision per scheduler candidate. CNA 20/20 passed; wider optional matrix pending. |
| 4 Fluid regions and anchor iteration | Implemented membership-invalidated source counts/chunks/sections and lazy anchors. Tests preserve source/anchor order, writable-map invalidation and negative boundaries. Core 173/173 and CNA 20/20 passed. |
| 5 Native point queries | Implemented occupied-row point lookup; randomized tests match former scans, exact order, bounds and caps. Native-present recipient comparison fixtures added. |
| 6 Gas traversal and stable/churn comparison | Reuse scratch capacity with reentrancy fallback and finally cleanup; no persistent topology/power cache. Core 173/173 passed, including grace/energy/invalidation tests. Stable/churn comparison pending. |
| 7 Shaft/docking scaling | Per-pass spatial source selection, preserving original source order and exact distance test. 292 unit tests, 7 engineering tests (including moving-shaft range exit/reentry) and 4 engineering regressions passed. Added 16/64/256-network and 4/16-moving-ship benchmarks; comparison pending. |
| Matched standard and JFR comparison | Pending; retain baseline fixtures and add targeted native-query/gas/shaft coverage on both revisions. |
| Live full-pack verification and final deployment | Pending |

Gameplay gates must cover force results/order, live configuration and power, chunk/lifecycle changes, gas grace/energy/topology behavior, and shaft conflicts/reversals. Faster timing alone is not acceptance evidence.

The baseline comparison checkout is detached at `1d479047812b`, with only the same opt-in fixture/harness extensions copied in. Both variants will use the same 30-scenario standard run, followed by matched attribution runs. Changes beyond items 1–2 are not yet pushed while validation continues.
