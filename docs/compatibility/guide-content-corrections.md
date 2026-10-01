# Field Manual and Ponder corrections

This report resolves the historical [28-finding content audit](guide-content-audit.md)
and [supplemental review](guide-content-supplement.md). The original inventories,
source hashes and diagnostic captures remain historical evidence. Corrected-source
validation is recorded separately in `evidence/guide-corrections/`.

The Field Manual now includes access, recovery recipes, deferred gifting and the
client/server restart requirement. Its authored references navigate to actual
entries. Dense explanations are split at topic boundaries, long headings use
short book-specific captions, and percentage literals are escaped for Minecraft's
native localization formatter. Item display names remain unchanged.

Verified results: **271 unit tests**, **173 required GameTests**, final build and
release-JAR checks, minimal client startup, five lifecycle process pairs, all
18 scene playbacks with 49 native instructions, 37 Track Styles and eight field
visual transitions. The reader sweep opened 299 authored pages across four
locale/GUI-scale passes: 1,196 page renders, 692 spreads, text scale 1.0 and
15 successful screen-routed links. All English spreads and Ponder instructions
received visual review. A focused GUI-scale-3 replay fixes and verifies the
rare-earth annotation overlap found during that review.

The [evidence index](evidence/guide-corrections/README.md) distinguishes the full
run from that focused repair, retains the original captures and resolves source
provenance. The final JAR contains exact source bytes for all 37 changed resource
files checked, including the explicit-air workshop schematic.

## Findings resolved

| Finding | Corrected instruction |
| --- | --- |
| F01 | Starter alloy: eight Iron Ingots around a vanilla Lodestone, yielding eight. |
| F02 | Three Magnetic Alloy Ingots yield three Magnetic Plates. |
| F03 | Lodestone Core uses four corner plates, four edge Lodestones and one central alloy. |
| F04 | Meteorite Sapling uses two fragments and three Raw Magnetite. |
| F05 | NORTH-facing Fusion panel stands in X/Y; expansion shows three interiors with twelve coils. Its played world must pass the shipping validator at both stages. |
| F06 | A powered placed anchor automatically binds the closest eligible ship; sneak-wrench clears the binding. |
| F07 | Field Compass is passive; no entity-locking gesture is advertised. |
| F08 | Vector Core uses GUI-selected thrust perpendicular to facing; facing also controls its capture cone. |
| F09 | Hematite steps EXTREME → STRONG → MEDIUM → WEAK → NONE. |
| F10 | Halbach bonus is +1 for one/two neighbors and +2 for three through six, capped at two. |
| F11 | Plain Ferrofluid responds to both poles; it is not immune. |
| F12 | Titanomagnetite records the strongest current source, overwriting weaker or stronger saved values. |
| F13 | Breaking/replacing Titanomagnetite preserves its recorded item components. |
| F14 | Default NORTH Elytra wearer is repelled by NORTH and attracted by SOUTH. |
| F15 | Fuel routes show the cell/fluid processing stages in their actual order. |
| F16 | Helium-3 has lower generation but longer runtime than Tritium under defaults; extraction timing is stated correctly. |
| F17 | Meteorite Core crafting, sapling acquisition and core/fragment loot agree with shipping data. |
| F18 | Enhanced/Cosmic Catalysts use the actual catalyst, Pyrrhotite and fragment ingredients. |
| F19 | Catalysts read adjacent heat directly; they do not relay it. Hottest eligible heat wins. |
| F20 | Elytra follows the native five-ingot pattern. |
| F21 | Horse armor uses seven ingots in an H. Equipment grants susceptibility; stamping chooses polarity. |
| F22 | Magnetite Ore base drop is one, before Fortune. |
| F23 | Excavator consumes world ore; it is not a renewable generator. |
| F24 | Tool collection follows net SOUTH attraction/NORTH repulsion and cancellation. |
| F25 | Elytra's malformed formatting token is fixed. |
| F26 | Ship pole follows Polarity Inverter parity, rather than a mounted magnet's pole. |
| F27 | Anchors damp rotation; they do not automatically level the ship. |
| F28 | Undead are not intrinsically susceptible; eligible armor or explicit entity tags are required. |

Supplement A01–A10 corrects gun slots/eligibility, tag scope, AeroPortals versions,
repeated catalyst prose, MEDIUM source-only Magnetized Ferrofluid, bounded EMP
behavior, configurable defaults, sapling loot wording and NeoForge FE transfer
requirements. A11's AE2 compass behavior was repaired separately in `6966e850`;
its independent runtime follow-up is described in the supplement. A12 corrects
Structural Inducer capture opposite block FACING and reeling toward the inducer.
A13 corrects the five percentage-bearing book strings and adds positive/negative
formatter regression checks.

## Acceptance and scope

| Audit gap | Resolution and evidence boundary |
| --- | --- |
| G1 Navigation | Native screen mouse routing must accept every authored local link and land on its expected entry. Coverage is non-vacuous. |
| G2 Access/lifecycle | New access/configuration pages share the native crafting, gift and reload/restart rules. The fixture preserves one world across five separate client/server process pairs. |
| G3 Scene assertions | Played Tokamak/Fusion worlds use shipping geometry validators; rails use shared pairing/tag-length rules; Copycats stores actual Iron. All eighteen scenes have concrete state assertions. |
| G4 Bounds/defaults | Gas scanning, rail capture/breaking, optional tags and configurable values are qualified in prose. |
| G5 Drift guards | Recipe layouts, inputs/yields, referenced recipes/tags and reviewed config defaults have independent contracts; native pages must resolve real recipe holders and spotlight items. |
| G6 Native layout | Every authored page is opened/captured in English and German fallback at configured and effective GUI scales 2/3. The independent gate checks definitions, coverage, text scale, visible glyph bounds, all native heading types and capture provenance. Captures also require visual review. |
| G7 Compatibility/locale scope | The complete reader sweep proves these layouts and fallback behavior. It does not prove German translation quality or every optional integration/configuration combination. Runtime compatibility claims remain limited to the pinned profiles and their existing behavioral evidence. |
| G8 Comments | Hematite, meteorite, recipes, gun slots, Ferrofluid and inducer comments now match executable rules. |

Ponder uses a shipping 7×5×7 empty workshop schematic. The borrowed Create
hand-crank schematic clipped out-of-bounds rail and input/output additions.
The new fixture captures every rendered instruction and each displayed input
item, as well as first/final stages; it never seeks to scene completion.

Piping, gold FE supplies, gas glass, moving hulls and coupled car models are
explicit tutorial diagrams. Native scene assertions establish geometry,
materials, installed inputs/modules and depicted configuration; they do not
claim Ponder executes a real reactor, gas recovery, craft processing or ship/train
physics simulation. Those behaviors rely on the separately identified shipping
implementation and behavioral tests.

The renderer profile is Minecraft 1.21.1 / NeoForge 21.1.252, Create 6.0.11-312,
Ponder 1.0.87, Aeronautics/Simulated 1.3.2, Patchouli 1.21.1-93, Coasters Simulated
0.1.5, Track Styles 1.1.0-hotfix1, Magnetized 1.1.0, Steam ’n’ Rails 0.2.1 and
Copycats 3.0.9. Mesa llvmpipe runs on a private Xvfb display, with native Flywheel
visualization. Original artifact/source hashes and explicit evidence limitations
are retained in the reports.

Reproduce with Java 21:

```sh
./gradlew build verifyReleaseJar
./gradlew smokeGameTest
bash scripts/run-client-profile-smoke.sh runClientMinimal run-smoke-client magnetization 240
bash scripts/run-lifecycle-presentation-audit.sh
python3 scripts/summarize-lifecycle-presentation-audit.py --output docs/compatibility/evidence/guide-corrections
python3 scripts/analyze-guide-book-audit.py --records build/validation-audit/control/book-pages.jsonl \
  --screenshots build/validation-audit/initial/screenshots \
  --output docs/compatibility/evidence/guide-corrections/book-results.json
python3 scripts/test-guide-book-audit.py
```
