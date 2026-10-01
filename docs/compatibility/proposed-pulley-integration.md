# Proposed Supplementaries pulley / Sable integration

This is separate work from the 1.4.6 material-tag audit. No pulley runtime adapter
is included in that release. Cannon operation and the bellows production line
have independent passing evidence and do not establish pulley support.

## Reproduced defect

Pinned Supplementaries **3.9.9** (`supplementaries`, Modrinth `WrZWfRjP`) and
Moonlight **3.6.8** (`moonlight`, `HTfE7eCk`) create
`supplementaries:moving_pulley_block` with a manually constructed block entity.
The inspected Sable **2.0.3** `SubLevelAssemblyHelper.moveBlocks` path preserves
its block state but leaves the destination block entity null: the block's ordinary
`newBlockEntity` factory returns null, and assembly only restores saved NBT when
a destination entity already exists.

The saved payload includes `supp_carried_be`, containing the chest inventory.
The minimal native assembly test loses that payload entity before any magnetic
field is applied. The complete moving-ship test also loses the chest across the
native extension/splitting sequence. The ground control extends and retracts,
retains 17 diamonds, and recovers all four chain items.
[Recorded localization](evidence/remaining-work/deeper-investigation/pulley-localization.txt).
The fresh Sable 2.0.5 runtime recheck reproduces both required pulley failures,
while the cannon and ground round-trip controls pass. See the
[remaining-work audit](remaining-work-audit.md) for the versioned evidence.

## Required correction and acceptance criteria

1. Restore manually constructed moving block entities from their registered type
   and saved state during assembly/splitting, preserving carried inventory and
   animation progress. Prefer an upstream Sable assembly correction if it can
   cover this lifecycle safely; otherwise evaluate a narrowly optional adapter.
   Simply adding a material tag cannot restore the payload.
2. Preserve or reestablish the pulley/rope/payload relationship across split
   sublevels. Verify coordinates and ownership after translation and rotation.
   Reconstructing the block entity alone is not proof that retraction works.
3. Complete native extension and retraction while the ship moves. Consume and
   recover the correct chain count, retain the chest contents, and assert no
   duplicate inventory or orphan moving payload remains.
4. Save and restart while extended and while a payload is moving; finish the
   cycle after reload. Cover removal/interruption and an ordinary-world control.
5. Recheck magnetic material counts and response after each transition, with
   fields both enabled and absent. Preserve optional-mod absence and the existing
   cannon, bellows, and attached-block tests.

The existing `runSupplementariesWorkflowGameTestServer` contains the cannon
control, minimal moving-payload assembly reproducer, and native pulley round
trip. Its required failures must become actual behavior passes before advertising
moving-ship pulley support. Keep the additional restart/interruption cases as
explicit acceptance work; the existing three tests do not cover those cases.

To collect the complete diagnostic profile, including its passing controls:

```sh
MAGNETIZATION_AUDIT_COLLECT_FAILURES=1 scripts/run-gametest-gate.sh \
  runSupplementariesWorkflowGameTestServer run-supplementaries-workflow-gametest 300
```

The gate still returns failure when required tests fail. The environment option
only delays that return until the full profile has completed; ordinary gates
continue to stop at the first failure.
