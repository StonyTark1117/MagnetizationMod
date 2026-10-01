# AE2 Cosmic Compass runtime verification

Verified on October 1, 2026 with Minecraft 1.21.1, NeoForge 21.1.252,
Applied Energistics 2 19.2.18 and GuideME 21.1.19. All three verification
requirements passed. The runtime implementation matches commit `6966e850`;
the audit ran from an isolated snapshot of the current workspace.

## Client packet delivery, selection and rendering

Two real client JVMs connected to two dedicated-server JVMs over loopback.
The audit observed the shipping packet handler's cache without calling the
handler or injecting client targets. It then evaluated the registered item
property, inspected the item renderer's chosen model and quad textures, and
captured the framebuffer while `GuiGraphics.renderItem` rendered the held
Cosmic Compass. Screenshots also show the ordinary hand and hotbar item.

The player faced south at `(0.5, 100, 0.5)`. The active virtual AE2 source was
west at `(-40, 100, 0)`. A closer, fully decayed virtual source at `(5, 100, 0)`
remained excluded throughout. The native core was south at `(0, 100, 80)`;
placing another at `(0, 100, 12)` made the native target closer.

| Case | Delivered AE2 reading | Selected target | Angle / model frame |
| --- | --- | --- | --- |
| AE2 closer than native | Active AE2 coordinates | AE2 | 0.75 / 24 |
| Native closer than AE2 | Active AE2 coordinates | Native | 0.5 / 16 |
| Hook disabled, native present | Empty packet | Native | 0.5 / 16 |
| Hook disabled, native removed | Empty packet | None | 0 / 00 |
| Hook re-enabled | Original active AE2 coordinates | AE2 | 0.75 / 24 |

The first phase exercised all five cases. After a clean full save and server
restart, a fresh client repeated both target selections and the disable/
re-enable transition. All nine stages selected the expected texture on all
18 rendered quads. Visual inspection confirmed that the needle points right
for the westward AE2 source and forward for the southern native source when
the player faces south.

Screenshots: [AE2 selected](evidence/ae2-compass-runtime/create/screenshots/ae-compass-create-4-ae2.png),
[native selected](evidence/ae2-compass-runtime/create/screenshots/ae-compass-create-1-native.png),
[no signal](evidence/ae2-compass-runtime/create/screenshots/ae-compass-create-3-none.png),
[AE2 after restart](evidence/ae2-compass-runtime/verify/screenshots/ae-compass-verify-3-ae2.png).

## Actual field-force suppression

Separate iron-item probes were positioned three blocks from the AE2 source
and the distant native core. Before every ordinary server tick, the audit
reset their positions and velocities; gravity was disabled. After normal
entity and level ticks it measured their real velocity changes. It did not
call `FieldApplicator.apply` or the registry tick handler manually.

Each stage covered 24 scheduled AE2 field ticks. With the hook enabled, the
AE2 probe's maximum velocity change was `44.36741767764299` blocks/tick.
Disabling `AE2_METEORITE_HOOK_ENABLED` produced exactly `0.0`, while the
native control continued at `44.36741767764299`. Re-enabling restored the
AE2 impulse. The same results held for the restored source after restart.

## Full save/restart persistence

The creation server (PID `438591`) completed ordinary shutdown and reported
that all dimensions were saved. A new server (PID `442128`) loaded the same
world files; no registry or block-entity deserialization was simulated.
The disposable runtime used regular files on `/dev/shm` to avoid unrelated
disk-write stalls. This verifies a full server-process restart, not a host
reboot.

| Persisted value | Before restart | After restart |
| --- | --- | --- |
| Active AE2 charged-at tick | -756 | -756 |
| Fully decayed AE2 charged-at tick | -11556 | -11556 |
| Native core charged-at tick | 844 | 844 |
| Virtual source count | 2 | 2 |

The saved world clock was 944 and the verification process observed 1386
before running its scenarios. Re-registering the restored active coordinate
with the new clock did not recharge it. Disabling preserved both saved
entries; re-enabling resumed the active source and kept the decayed source
inert. Both server processes exited successfully after clean saves.

The analyzer independently decodes the actual compressed SavedData and
`level.dat` files copied after each shutdown. It requires identical virtual
positions and charge timestamps across both saves, distinct server PIDs,
all nine client/model/force records, and all nine screenshots.

## Reproduction and evidence

Run `bash scripts/run-ae-meteorite-runtime-audit.sh`. It snapshots current
sources, uses isolated server/client directories and a private X server,
runs both processes sequentially, and invokes
`scripts/analyze-ae-meteorite-runtime-audit.py` before reporting success.

Evidence: [machine-readable summary](evidence/ae2-compass-runtime/summary.json),
[creation server log](evidence/ae2-compass-runtime/create/server.log),
[creation client log](evidence/ae2-compass-runtime/create/client.log),
[restart server log](evidence/ae2-compass-runtime/verify/server.log),
[restart client log](evidence/ae2-compass-runtime/verify/client.log),
[input source hashes](evidence/ae2-compass-runtime/source-sha256.txt),
and [evidence checksums](evidence/ae2-compass-runtime/SHA256SUMS).
The isolated snapshot also passed all 269 unit tests, `build`, and
`verifyReleaseJar`; see [build validation](evidence/ae2-compass-runtime/build-validation.log).
