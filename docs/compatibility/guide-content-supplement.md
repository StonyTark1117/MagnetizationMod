# Independent guide review supplement

This review extends the [content accuracy audit](guide-content-audit.md),
preserving findings identified against `3d1c78b3` and the developing corrections.
The final review accepts the corrected instructions and the scoped native
presentation results in the [correction report](guide-content-corrections.md).
Recipe and gameplay rules remain the source of truth; documentation was corrected
to match them. Historical diagnostic failures are retained separately from
accepted results.

| ID | Affected content | Finding and required correction | Executable evidence |
| --- | --- | --- | --- |
| A01 | `repulsor_gun.curio.text` | The Repulsor Gun uses the Curios **hands** slot, not charm. Correct the repeated activation Javadoc too. | [`hands.json`](../../src/main/resources/data/curios/tags/item/hands.json), [`RepulsorGunItem.tryActivate`](../../src/main/java/com/stonytark/magnetization/content/item/RepulsorGunItem.java) |
| A02 | `repulsor_gun.effects.text` | Repeat of F28: the Magnetized effect alone does not give an ordinary living body susceptibility. The gun emits NORTH for its shared living-entity path: NORTH targets repel and SOUTH targets attract. Its separate ship/loose-object paths push away. | [`RepulsorGunItem.fire`](../../src/main/java/com/stonytark/magnetization/content/item/RepulsorGunItem.java), [`FieldApplicator`](../../src/main/java/com/stonytark/magnetization/physics/FieldApplicator.java) |
| A03 | `compatibility.fuels.text` | Common tags permit substitution only when populated and consumed by the recipe. They do not make arbitrary metals, plates or lithium items interchangeable. Preserve the useful tags and qualify the claim. | [`common alloy ingots`](../../src/main/resources/data/c/tags/item/ingots/magnetic_alloy.json), [`plate recipe`](../../src/generated/resources/data/magnetization/recipe/magnetic_plate.json), [compatibility matrix](audit-1.21.1.md) |
| A04 | `compatibility.ships.text` | AeroPortals 1.1.2 supports the safe base transfer path. Remote/swivel geometry remapping needs the API introduced in 1.2.3. The book must preserve this version boundary already stated in README. | [`MagAeroPortalsCompat`](../../src/main/java/com/stonytark/magnetization/compat/aeroportals/MagAeroPortalsCompat.java) |
| A05 | `pyrrhotite_block.range.text` | Repeat of F19 missed by its original entry index: catalysts do not chain and the largest reach does not win. Each qualifying catalyst reads directly adjacent heat; the hottest eligible heat wins. | [`PyrrhotiteHeatResolver.resolve`](../../src/main/java/com/stonytark/magnetization/content/pyrrhotite/PyrrhotiteHeatResolver.java) |
| A06 | `magnetized_ferrofluid.spotlight` | Only **source blocks** emit, with **MEDIUM** strength. Flowing tongues do not emit. The field attracts or repels according to polarity, rather than universally pulling. Correct repeated WEAK and field-immunity comments/tooltips without changing the implemented strength. | [`MagnetizedFerrofluidBlock.onPlace`](../../src/main/java/com/stonytark/magnetization/content/fluid/MagnetizedFerrofluidBlock.java), [`MagnetizedFerrofluidFieldHandler.onLevelTick`](../../src/main/java/com/stonytark/magnetization/content/fluid/MagnetizedFerrofluidFieldHandler.java) |
| A07 | `emp_charge.spotlight`, `tooltip.magnetization.emp_charge.use` | The pulse does not guarantee blackout of every upstream emitter or emptying every battery. It blackouts `AbstractEmitterBlockEntity`, clears supported `EmpDrainable` stores, and requests bounded extraction from other exposed FE capabilities. Nonextractable external stores are not forcibly cleared. | [`EmpChargeBlock.detonate` and `drainCapability`](../../src/main/java/com/stonytark/magnetization/content/emp/EmpChargeBlock.java) |
| A08 | `magnetostrictive_sensor.spotlight`, `permanent.temporary`, temporary-magnet tooltip | The eight-block sensor range and ten-minute Temporary Magnet lifetime are defaults. Sensor range has a per-block GUI override and server limits; magnet lifetime is configurable. Present them as defaults. | [`MagnetostrictiveSensorBlockEntity.effectiveRange`](../../src/main/java/com/stonytark/magnetization/content/sensor/MagnetostrictiveSensorBlockEntity.java), [`TemporaryMagnetBlockEntity.lifetimeTicks`](../../src/main/java/com/stonytark/magnetization/content/temporary/TemporaryMagnetBlockEntity.java) |
| A09 | `meteorite_sapling.intro.text` | Repeat of F17: the sapling dropping itself is not a contrast with the core. The core also drops itself. Remove “Unlike the Core itself.” | [`core loot`](../../src/generated/resources/data/magnetization/loot_table/blocks/meteorite_core.json) |
| A10 | `fe_rf.page1.text`, `fe_rf.page2.text`, energy-power config tooltip | “Any energy-providing mod” overstates the capability contract. A source must actually transfer NeoForge FE into the exposed capability; the presence of an arbitrary energy mod does not establish this. Match README's scoped wording. | [`AbstractEmitterBlockEntity`](../../src/main/java/com/stonytark/magnetization/content/AbstractEmitterBlockEntity.java), [`C&A native transfer test`](../../src/main/java/com/stonytark/magnetization/gametest/CreateAdditionGameTests.java) |
| A11 | `meteorite_core.ae2.text` | Historical defect at `3d1c78b3`: client lookup required native core block entities, and the AE2 switch gated discovery only. Separately repaired in `6966e850`: server-selected AE2 targets now reach the client, and the switch gates saved emission and targets too. Preserve the repaired compass claim, scoped to discovered, active sources within range. Client presentation, force suppression and full restart now have native evidence; see the recheck below. | [`CompassPropertyHooks.findNearestActiveMeteorite`](../../src/main/java/com/stonytark/magnetization/client/CompassPropertyHooks.java), [`CosmicCompassTargetPayload`](../../src/main/java/com/stonytark/magnetization/network/CosmicCompassTargetPayload.java), [`MeteoriteFieldRegistry.onLevelTick`](../../src/main/java/com/stonytark/magnetization/content/meteorite/MeteoriteFieldRegistry.java) |
| A12 | `magnetization.ponder.structural_inducer.header`, `.text_1` and scene geometry | The original “Launch a Structure” instruction misstates the current operation. The inducer scans opposite block FACING, assembles eligible structures, then reels them toward itself until arrival/timeout. Teach assembly and reeling, and place the depicted target in the actual scan cone. The developing SOUTH-facing scene's southern target was behind that cone. | [`StructuralInducerBlockEntity.captureNewStructures`, `driveAll` and `driveOne`](../../src/main/java/com/stonytark/magnetization/content/inducer/StructuralInducerBlockEntity.java), [`PonderSceneCatalog`](../../src/main/java/com/stonytark/magnetization/compat/ponder/PonderSceneCatalog.java) |

**A13 — percentage formatting:** Independent screenshot review found a visible
`Format error:` prefix on Magnetic Gravel and exposed the same risk in other
percentage-bearing book strings. Pinned Patchouli's `BookTextRenderer.setText`
passes localized literal body keys to Minecraft `I18n.get` with no arguments;
that method uses `String.format`. Literal percentages must be escaped as `%%`
in these translations to display one `%`. The
[preserved diagnostic capture](evidence/guide-content-supplement/layout-diagnostic/format-error.json)
records the defect. Native validation must reject formatting-error output and
recheck corrected page density. This finding concerns the book's actual
localization path; it does not prescribe changing every tooltip percentage.

Entry translation keys above have the prefix `book.magnetization.entry.` unless
a complete `tooltip.` key is shown. The original inventory preserves the
pre-correction text. A01–A12 are source-backed findings, not independent runtime
test results.

### Native layout findings during correction validation

The first complete diagnostic sweep opened 241 pages in four passes (964 page
records) and clicked all 17 authored links. It found **ten unique pages below
the 0.85 text-scale threshold**: Imprint Module page 3, Armor/Tools page 3,
Anomaly Terrain page 10, Iron Oxide Family page 11, MR Fluid Golem page 1,
Compatibility page 8, Hematite Lens page 3, Railgun page 2, Magnet Burning page 1,
and Fusion Fuels page 7. These refer to the diagnostic snapshot's page numbers;
splitting content changes them. Independently viewed captures confirm very small
text, and the Anomaly Terrain crafting title extends outside its page.
[Diagnostic records and three reviewed captures](evidence/guide-content-supplement/layout-diagnostic/results.json)
preserve those findings. Final corrected-layout evidence must replace the
diagnostic findings as the acceptance result.

Two fixture corrections were necessary: Patchouli's `Word.width` includes the
remaining span's hitbox rather than only its drawn substring, and trailing wrap
spaces have no visible glyphs. Visible bounds must measure the styled rendered
substring with trailing whitespace removed. Native title widths need separate
checks because body bounds do not include headings. Neither false-positive
hitbox measurements nor successful page opening establish readable layout.

A later diagnostic passed numeric bounds at all four scales/locales but still
showed degraded glyphs in auto-shrunk body text. The Oxidation Decay page at
scale 0.935 also split ordinary words around long configuration identifiers.
[Native captures](evidence/guide-content-supplement/layout-diagnostic/shrunken-glyphs.json)
record why a minimum scale of 0.85 is not sufficient visual acceptance on its
own. The analyzer now requires text scale exactly 1 and includes negative
controls for fractional scales up to 0.999. The previous numeric pass is
retained as a superseded diagnostic; corrected readability still requires
inspected captures.

The corrected 299-page candidate now passes the stricter independent gate:
**1,196 page records, 692 spreads, 15 actual link clicks, and text scale 1**
across English/German fallback at GUI scales 2/3.
[Complete numeric result](evidence/guide-content-supplement/final-book-results.json)
and [independently inspected full-size captures](evidence/guide-content-supplement/final-visual/review.json)
record that result. The Oxidation Decay, Cosmic Compass and Manual Access
captures show intact glyph strokes after pagination. Recipe pages in the prior
255-page candidate showed all 29 crafting and six smelting diagrams populated;
the final candidate additionally resolves their native recipe holders. These
book results are separate from the Ponder acceptance recorded below.

### A11 recheck after the AE2 repair

Commit `6966e850219a5c437df72b92bd21091464abf993` is present on `origin/main`
(checked 2026-10-01). It preserves optional AE2 detection and sends the nearest
active virtual source once per second. The client compares that target with
loaded native cores, rejects expired/out-of-range/other-dimension readings,
and clears its cached reading on logout. Disabling the hook stops emission
and sends empty target updates while retaining saved entries.

Reviewed evidence: all eight new selection/payload unit tests passed; the full
recorded suite reported 269 passing tests, and build/release-JAR checks passed.
The dedicated server loaded **AE2 19.2.18**, pinned as
`maven.modrinth:ae2:KDnFUmMm` (mod ID `ae2`), and its one required GameTest
passed against the actual registered **`ae2:meteorite`** structure.
[Preserved logs, JUnit reports and provenance](evidence/guide-content-supplement/ae2-recheck/results.json)
record the exact evidence reviewed; these are the repair agent's runs, not a
second independently executed client test.

Evidence boundaries: the GameTest supplies a deterministic vanilla structure
piece under AE2's real registry key and invokes the scanner directly. It checks
target selection, expiry, disabled/re-enabled targets and in-memory rediscovery
deduplication. It does **not** prove naturally generated meteorite discovery,
packet delivery and rendered needle behavior, native-vs-AE2 target competition
in a client, field force suppression while disabled, or persistence across a
full process save/restart. The server stalled saving chunks after its assertion
pass and was terminated (exit 143); this is not a clean lifecycle pass or a
diagnosed integration defect. Those distinctions keep A11 repaired in code
without overstating end-to-end validation.

A subsequent [native runtime audit](ae2-compass-runtime-validation.md) now closes
the packet/rendering, target competition, disabled force and full process
save/restart gaps. Independent review reran its analyzer successfully, verified
all 22 evidence checksums, read the server/client fixtures, and inspected both
AE2-selected and native-selected compass captures. Its nine stages use real
client/server JVMs, the shipping payload handler and item renderer, normal field
ticks, and actual saved NBT loaded by a second server process. The fixture seeds
virtual source coordinates directly and disables structure generation. Thus
**natural AE2 meteorite discovery remains outside this runtime evidence**; the
previous registered-structure GameTest remains its narrower supporting check.
This supersedes the initial three runtime gaps, not that discovery limitation.

The review also checked the remaining basics, emitter and fluid entries against
their recipe data and relevant implementation paths. No recorded finding is
not a blanket runtime pass. The original audit's full-book layout, clickable
navigation, Ponder semantics and configuration coverage requirements remain
applicable.

## Regression guard added

[`GuideInstructionContractsTest`](../../src/test/java/com/stonytark/magnetization/data/GuideInstructionContractsTest.java)
checks the reviewed layouts, ingredients and yields for the eight recipes behind
F01–F04, F18, F20 and F21. It also checks that our items populate the common alloy
ingredient tags. A deliberate change to these recipe contracts requires a new
guide review; the test does not prove natural-language instructions correct.
The reviewed configurable defaults are also guarded, including default-off
magnet consumption.

The existing book-reference test omitted Patchouli's secondary `recipe2` field.
No page at the audited base uses that field, so this omission did not hide a
known broken reference. The new guard examines `recipe`, `recipe2` and `recipes`
across every authored page and explicitly requires a deliberately nonexistent
secondary ID to fail resolution even when its primary recipe exists.
Resource resolution is static evidence;
native recipe availability under configuration gates and page rendering still
require the runtime fixture.

Reproduce the supplemental tests with Java 21:

```sh
./gradlew test --tests '*GuideInstructionContractsTest' --console=plain
```

The original **six supplemental tests passed** in an isolated export of `3d1c78b3` plus
the new test, using Java 21. [Results, source hashes, JUnit output and build log](evidence/guide-content-supplement/results.json)
record that run. The evolving guide/Ponder edits were excluded from this
baseline check and still require the coordinated final validation. Passing
unit tests alone does not close the native presentation requirements.

Two additional A13 guards now exercise the exact no-argument `String.format`
contract across localized book strings and demonstrate that raw `5%` fails
while escaped `5%%` displays `5%`. The [final eight-test result](evidence/guide-content-supplement/final-contracts/results.json)
passed in the coordinated snapshot; its test and language sources matched the
checkout byte-for-byte. It is separate from the original six-test run.

Review of the developing corrections also caught newly introduced MHD and
Homopolar Motor Ponder instructions claiming magnet consumption is enabled by
default. `propulsion.magnetSlotConsumesFuel` defaults to **false**; both catalog
and localized instructions must say consumption is optional. Fluid/FE use is
independent. This is a correction to the proposed guide change, not another
defect in the audited base.

## Independent native evidence gate

[`analyze-guide-book-audit.py`](../../scripts/analyze-guide-book-audit.py) compares
the native sweep's JSONL records with the exact source snapshot. It requires all
authored pages in all four English/German-fallback and GUI-scale 2/3 passes,
matching native page types and definitions, actual GUI scales, native text scale 1,
native visible word bounds and title widths within the page, reported
authored-link clicks, and distinct named PNG captures with recorded hashes.
Missing or duplicated pages, stale definitions, truncated images and incomplete
link coverage fail the gate. This checks the evidence inventory; it does not
substitute for inspecting the captured layouts and item rendering.

Run against the same isolated checkout used by the native client:

```sh
python3 scripts/analyze-guide-book-audit.py --repo /path/to/validated-checkout \
  --records /path/to/control/book-pages.jsonl \
  --screenshots /path/to/initial/screenshots --output /path/to/book-results.json
```

The analyzer's synthetic positive/negative controls are separate from native
Minecraft evidence. Reproduce them with
`python3 scripts/test-guide-book-audit.py`; the [test log](evidence/guide-content-supplement/analyzer-tests.log)
records the result. The completed native sweep and subsequent focused annotation correction are
recorded below.

## Final independent acceptance

The 299-page manual passes the complete four-pass reader gate with unscaled
body text, valid native recipe/item references, bounded headings and 15 actual
link clicks. All book definitions and translations remain unchanged by the
subsequent Ponder-only text edit; [source continuity](evidence/guide-content-supplement/final-visual/book-source-continuity.json)
records that comparison. The five lifecycle process pairs passed gifting,
recovery crafting, live disable/re-enable, restart and repeat-login checks.

All 18 Ponder scenes passed their concrete state/geometry assertions and full
playback. Independent inspection covered all 49 instruction captures and the
12 rare-earth processing-item icons. One annotation overlapped the navigation
controls; its shortened replacement passed a focused 796-tick native replay
and is now readable above those controls. The
[scene review and focused correction](evidence/guide-content-supplement/scene-review.json)
retain original capture hashes and the corrected image separately. This proves
tutorial geometry, displayed inputs and instructions, not live machine or
ship/train physics inside Ponder.

The final source review covers F01–F28 and A01–A13, with G1–G8 resolved within
the explicit profile and localization limits. The
[review checkpoint](evidence/guide-content-supplement/prose-review.json) and
[full-run source provenance](evidence/guide-content-supplement/full-run-provenance.json)
identify the reviewed sources. No additional confirmed guide defect remains
from this pass. This is not a blanket validation of every advertised optional
integration or arbitrary server configuration.
