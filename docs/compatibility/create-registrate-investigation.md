# Create / Aeronautics registration concurrency investigation

Target: Minecraft 1.21.1, NeoForge 21.1.252, Create 6.0.11 (`6.0.11-295`),
Aeronautics 1.3.0, Registrate `MC1.21-1.3.0+67`, Guava `32.1.2-jre`, Java 21.
This is an upstream correction proposal, not a shipped Magnetization patch.

## Findings

The original failure throws `Found unused register callbacks` from Create's
Registrate instance without printing any unused-entry warnings. Registry rollback
then fails while constructing attributes. The attribute exception is secondary.

The pinned `AbstractRegistrate` uses an unsynchronized `HashMultimap` for pending
callbacks. `addRegisterCallback(String, ...)` inserts into it and `accept(...)`
removes callbacks for an accepted entry. `onRegister(...)` tests `isEmpty()`,
prints the map's entries, clears it, and throws in a development environment.
Thus collection inconsistency can explain the otherwise unusual empty-warning
failure; production mode does not execute that same development-only throw.

The [Java 21 collection model](evidence/remaining-work/deeper-investigation/callback-race-results.json)
reproduces this predicate using two threads operating on different entry names:
233 of 10,000 trials report nonempty while the entry map is empty. There are 526
inconsistent-count trials in total. Both the synchronized concurrent control and
the sequential control remain consistent. This model alone is not proof of the
historical Minecraft failure.

The [actual absent-mod ownership trace](evidence/remaining-work/deeper-investigation/ownership-magnetization-absent.txt)
then establishes concurrent access to the **same Create Registrate instance**:
threads 104 and 106 each enter `accept`/`addRegisterCallback` while two calls are
active. Both have NeoForge's `modloading-worker-0` name; they are distinct JVM
threads. Each recorded active-count-two stack has only one instrumented method
frame, excluding nested instrumented calls on the same thread as the explanation
for that count. The stacks show Create's own construction path and Aeronautics'
`AeroBlocks` initialization entering Create's `AllBlocks`. This occurs with
**Magnetization absent**, establishing an upstream unsafe-concurrency path.
Instrumentation changes timing, and concurrent method entry is not itself a
captured corrupt mutation in the original failing launch.

Aeronautics' envelope-encased-shaft builder eagerly evaluates `AllBlocks.SHAFT`
when constructing its `EncasingRegistry.addVariantTo(...)` transforms. This
forces Create block registration from Aeronautics' parallel constructor while
Create can be registering other classes on another worker.

The [38 additional launch results](evidence/remaining-work/deeper-investigation/startup-followup-results.json)
include 19 with Magnetization and 19 without it. Combined with the earlier 29,
67 audited launches reached the connection screen without reproducing the
original exception. The 1.4.6 release audit later reproduced the same first
`Found unused register callbacks` exception in the Ponder/Steam 'n' Rails/Copycats+
client profile on 2026-10-01. Its [first-failure excerpt](evidence/remaining-work/registrate-recurrence-2026-10-01.txt)
records the loaded versions and source-log checksum. The subsequent
`neoforge:swim_speed` failure occurred during registry rollback, as in the
original report. An isolated run of the same profile passed shortly before the
recurrence, so this remains intermittent. The recurrence does not establish
which component corrupted the callback state or verify the diagnostic candidate.
For the 1.4.6 release audit, this development-client recurrence is tracked as a
nonblocking upstream dependency issue: the unsafe path occurs without
Magnetization, and the specific unused-callback exception is development-only.
It does not prove the published JAR has a startup failure.
One resource-rewrite collision was explicitly excluded and
repeated from a frozen build snapshot; it is not a compatibility pass or a
Registrate recurrence.

## Diagnostic candidate comparison

A standalone bytecode agent implements only the deferred-supplier proposal in
an isolated client; it is outside the Magnetization source set and release JAR.
[Six controls and six candidates](evidence/remaining-work/deeper-investigation/deferred-shaft-candidate-results.json)
all reached the connection screen with Magnetization absent, using Sable 2.0.3.
The complete lists match across all 12 launches: 686 Create/Aeronautics blocks,
745 items, 11 entity types and 34 shaft-variant entries. The original artifact
registers each of its 16 colored shafts twice, plus Create's two variants; the
candidate preserves those duplicates and executes 32 deferred lookups.

Five of six controls show overlapping Create Registrate method entries. Each
candidate uses a single Create registration thread and shows no overlap in this
trace. [Ownership summaries](evidence/remaining-work/deeper-investigation/deferred-shaft-candidate-traces.txt).
These 12 launches are additional to the 67 earlier unmodified launches.
Externally interrupted attempts are excluded.

This validates the candidate's registration tables and the targeted ownership
change. It does **not** validate native in-world shaft encasing, behavior with
Magnetization installed, a published upstream build, or a reduction in crash
frequency. Neither controls nor candidates reproduced the historical failure.

## Concrete upstream correction proposal

1. Defer Aeronautics' `AllBlocks.SHAFT` lookup until its registration callback
   executes. `EncasingRegistry.addVariantTo` accepts a `Supplier`; construct that
   supplier without eagerly reading the `AllBlocks` static field. Audit both
   envelope-encased-shaft transforms and other eager upstream registry references.
2. Review Registrate's complete shared registration lifecycle for thread safety,
   including its registration tables and callback lists. Synchronizing only the
   callback map fixes the collection model, but has not been established as a
   complete fix for Registrate.
3. Validate a candidate upstream change with ownership traces, registry counts,
   native shaft-encasing behavior and repeated startup, both with and without
   Magnetization. The diagnostic comparison above covers absent-mod registration only; no published candidate upstream build is advertised as verified here.

Do not suppress the unused-callback exception or attribute rollback failure as a
fix. The recorded crash is consistent with the demonstrated upstream race, but
capturing the callback map's corrupt state during an actual failing launch remains
the missing causal evidence. No issue or message has been sent upstream.

Reproduction tooling and its limits: [diagnostic tools](../../tools/compatibility/README.md).
