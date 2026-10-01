# Corrected guide and native presentation evidence

Verified 2026-10-01. The [correction report](../../guide-content-corrections.md) resolves the historical audit findings.

| Check | Accepted result | Evidence |
| --- | --- | --- |
| Native reader | 89 entries, 299 authored pages, 1196 page renders and 692 spread captures; all text remains scale 1.0; 15 actual link clicks | [Reader gate](book-results.json), [native records](book-pages.jsonl), [reader events](book-events.txt) |
| Lifecycle | Five separate client/server process pairs; native recovery crafting, live configuration/reload, disabled startup, deferred gift after re-enable, persistent gift, gift-off | [Native assertions and hashes](results.json) |
| Ponder | All 18 advertised scenes play fully, pass concrete scene-world checks, and render all 49 instructions | [Native results](results.json), [independent visual review](../guide-content-supplement/scene-review.json) |
| Track Styles | All 37 selected geometries inspected in world with independent beam/rail colors | [Native results](results.json), [style captures](captures.md#track-styles) |
| Magnetized | MAGNET and BRAKE each render off → on → off → on under field changes | [Native results](results.json), [field captures](captures.md#field-transitions) |
| Final release | 271 unit tests, 173 required GameTests, build/JAR checks and minimal client main menu | [Release checks](release-checks.json), [release log](release.txt), [GameTests](gametest.txt), [minimal client](client-minimal.txt) |

The complete run preserves 846 original captures in [the capture index](captures.md). Twenty [book contact sheets](book-review/) retain the native page pixels for English GUI-scale-2 visual review. English and German fallback at effective GUI scales 2 and 3 passed the independent reader gate. All 35 crafting/smelting pages resolve real recipes; translations outside this fallback check are not claimed.

The full native run used an archive of `3d1c78b3` plus the already committed `6966e850` production repair and scoped corrections. The unrelated AE2 runtime fixtures added in `96cd1011` were excluded from that snapshot. The final shared-checkout release build includes `96cd1011`. [Full-run provenance](../guide-content-supplement/full-run-provenance.json) resolves the recorded hashes to current sources, historical Git state or [preserved changed-source bytes](full-run-sources/).

Visual review found the original rare-earth second instruction overlapping navigation controls. That original remains in the full-run manifest as historical evidence. A [focused native follow-up](rare-earth-followup/results.json) captures the shortened instruction at effective GUI scale 3, plays 796/796 ticks, renders all three instructions and twelve processing-item controls, and passes the connected reload checks. [The corrected annotation](rare-earth-followup/validation-ponder-rare_earth_magnets-text-2.png) supersedes its original for visual acceptance. The first focused attempt rejected a catalog/resource compilation mismatch; [its diagnostic](rare-earth-followup/first-attempt-diagnostic.txt) is retained.

Only the one Ponder translation changed after the full reader sweep. [Book-source continuity](../guide-content-supplement/final-visual/book-source-continuity.json) proves all reader prose and authored resources unchanged. The follow-up is explicitly separate from the complete five-process run.

The scene checks establish shipped geometry, materials and supplied inputs. Pipes, FE supplies, gas markers, hull movement and coupled car models are tutorial illustrations; they do not constitute real reactor/crafting or ship/train physics execution. Runtime versions, artifact hashes and upstream warnings are preserved in the native results and role excerpts.

Reproduce the full acceptance as documented in the correction report. After archiving/summarizing that full run, the focused repair can be reproduced with:

```sh
mv build/validation-audit build/validation-audit-full
MAGNETIZATION_AUDIT_SCENE=rare_earth_magnets bash scripts/run-lifecycle-presentation-audit.sh
```
