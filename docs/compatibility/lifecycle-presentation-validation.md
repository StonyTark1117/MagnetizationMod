# Patchouli lifecycle and Coaster/Ponder presentation validation

The corrected instructions and new native validation are accepted in the
[correction report](guide-content-corrections.md) and [new evidence index](evidence/guide-corrections/README.md). The original run below remains historical evidence.

Verified 2026-10-01: five audited server/client process pairs, native recovery
crafting and gift persistence, 37 Track Styles selections in world, eight field transitions,
and full playback of all 18 advertised Ponder scenes passed. The
[evidence index](evidence/lifecycle-presentation/README.md) links all 86 original
captures; [results](evidence/lifecycle-presentation/results.json) record assertions
and provenance. Also passed: 255 unit tests, 173 core GameTests, build/release
checks and minimal client startup with optional mods absent, recorded in
[release checks](evidence/lifecycle-presentation/release-checks.json).

These results establish lifecycle behavior and scene playback. The subsequent
[content accuracy audit](guide-content-audit.md) identifies incorrect book
instructions and a Fusion panel scene whose depicted geometry does not form.
Playback success does not establish instructional accuracy. The [correction report](guide-content-corrections.md) records the resulting instruction, geometry and complete-reader validation changes.

The run used an isolated snapshot of `46b682eb` plus the scoped changes identified
by the source hashes. Unrelated work in the shared checkout was excluded.
Minecraft 1.21.1 / NeoForge 21.1.252 used the Create `6.0.11-312` dev artifact,
Ponder 1.0.87, Aeronautics/Simulated 1.3.2, Patchouli 1.21.1-93, Coasters Simulated
0.1.5, Track Styles 1.1.0-hotfix1 (reports 1.1.0), Magnetized 1.1.0,
Steam ’n’ Rails 0.2.1 and Copycats 3.0.9. Rendering used Mesa 26.2.3 llvmpipe on private Xvfb, with native Flywheel
visualization active.

Reproduce with `scripts/run-lifecycle-presentation-audit.sh`. The opt-in fixture
uses a disposable dedicated server and a real connected client on a private
Xvfb display. Both processes use the shipping mod classes and pinned optional
artifacts. It does not insert a manual to make the login check pass or seek
Ponder scenes to their final frames.

After the run, `python3 scripts/summarize-lifecycle-presentation-audit.py`
verifies complete coverage and saves the screenshots, log excerpts, source
hashes and runtime artifact hashes in `evidence/lifecycle-presentation/`.
The runtime worlds and full logs remain under `build/validation-audit/`.

The script preserves one world across five separate server/client process pairs:

| Phase | Native assertions |
| --- | --- |
| Initial | Exactly one automatically gifted manual and persisted flag; actual item opening; native crafting result, book component, and consumption for Book + Raw Magnetite and Book + Lodestone; default iron recipe unavailable. |
| Live config/data reloads | Iron recovery enabled; magnetite and lodestone independently disabled; package disabled removes all three recipes and the book registry on both server and client. Re-enabling restores configured recipes, while book discovery waits for restart. |
| Disabled startup | A fresh real player receives no manual and no gift flag; book and recovery recipes remain absent. |
| Re-enabled startup | That same previously deferred player receives exactly one manual, with its gift flag, and opens the rediscovered book. |
| Repeat login after process restart | The original player's UUID, manual and gift flag survive; no duplicate gift is added. |
| Automatic gift disabled | A fresh player receives no manual and no gift flag, while book registration and recovery crafting remain available. |

The live edits go through NeoForge's actual config-file watcher; recipe changes
then use the server's asynchronous resource reload, the same operation used by
`/reload`. Config synchronization now reapplies Patchouli's master toggle on the
client after receiving authoritative server COMMON values. Previously this
network path changed config values without the local config-reload callback, so
the connected client's book could remain registered after the server disabled it.
As documented, restarting **both** client and server rediscovers the book after
re-enabling. A client that has already removed it cannot rediscover it merely by
joining another enabled server in the same process.

The presentation fixture stages all 37 addon selections exposed by Track Styles
1.1.0-hotfix1: 30 track variants, six support variants and the invisible option.
[Native menu inventory](evidence/lifecycle-presentation/track-style-menu.json).
It assigns each selection
with independent purple (`4422AA`) beams and cream (`DDDDAA`) rails. It uses the
native primary coaster curve and reciprocal connection, then checks client
synchronization before capturing each style. The visual checks must confirm
actual geometry and colors; metadata assertions alone are insufficient.
Rendered steel styles show those independent colors; wood variants keep their
textured palettes, while the white variant shows both chosen colors. Round and
square supports show the selected beam color and different sizes. The invisible
option hides the curve while leaving the endpoint anchors visible.
Magnetized MAGNET and BRAKE tracks each undergo off → on → off → on field
transitions without redstone. The client checks synchronized power, the native
render predicate and selected model before each capture.

Rendered review found empty gas diagrams in Gas Exciter, Gas Vent and Ion
Thruster scenes. The scenes now use colored glass markers, explicitly described
in their localized instructions, so the connected volume, excitation change,
vented cloud and three propellant choices are visible in Ponder. The fixture
also asserts these markers in the scene world before recording the final frame.

Every scene in the shipping `PonderSceneCatalog` is compiled for its advertised
item and played through ordinary `PonderUI` screen ticks. Captures cover its first
explanation and final stage; completion requires monotonically advancing time
and the full scene duration. This covers 16 core scenes plus Rails and Copycats,
including fresh playback of the historically captured four scenes.

These checks cover the named recovery, gift, reload/restart and presentation
workflows. They do not establish every upstream renderer/backend combination or
every crafting inventory edge case. Runtime logs retain upstream warnings;
passing fixture assertions are not a claim of a warning-free optional pack.
