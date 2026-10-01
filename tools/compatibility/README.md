# Registrate diagnostics (Java 21)

These tools investigate Create 6.0.11, Aeronautics 1.3.0 and Registrate
MC1.21-1.3.0+67. They are outside the mod source set and are not packaged in the
release JAR. They do not establish that a particular historical crash was caused
by a race.

Run the callback-collection model with:

```sh
python3 scripts/run-registrate-race-probe.py
```

The script checks the SHA-256 of the runtime's Guava 32.1.2-jre artifact. It uses
`JAVA_HOME`, or the Java home in `gradle.properties`, and compares concurrent
`put`/`removeAll` calls on distinct entries with synchronized and sequential
controls. Counts vary with scheduling. An empty entry map with `isEmpty() ==
false` reproduces the predicate that makes Registrate throw without listing an
unused entry. This is a collection model, not a Minecraft launch test.

The ownership agent observes entries into `AbstractRegistrate.accept` and
`addRegisterCallback(String, ...)`, keyed by active-call count and Java thread ID.
It stores stack traces and prints them when registration events begin. NeoForge
can give different threads the same name, so use the IDs. It also changes timing;
its observations show concurrent entry is possible, not its uninstrumented
frequency. An exception escaping a called method can leave the active counter
stale; use only traces from successful, exception-free registration lifecycles
when interpreting overlap.

Compile the standalone agent with a Java 21 JDK:

```sh
mkdir -p build/registrate-ownership/classes
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
  -d build/registrate-ownership/classes tools/compatibility/RegistrateOwnershipAgent.java
printf 'Premain-Class: RegistrateOwnershipAgent\n' > build/registrate-ownership/MANIFEST.MF
jar cfm build/registrate-ownership/agent.jar build/registrate-ownership/MANIFEST.MF \
  -C build/registrate-ownership/classes .
```

For an isolated audit client JVM, supply both VM arguments:

- `--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED`
- `-javaagent:/absolute/path/to/build/registrate-ownership/agent.jar`

Use immutable copies of the compiled classes, resources and launch argument
files for repeated clients. Do not run multiple Gradle builds against the same
output directories concurrently: a resource-copy collision is a harness error,
not mod compatibility evidence. Preserve separate stdout logs and confirm the
actual loaded-mod list for the present/absent controls.

## Deferred-shaft candidate probe

`AeronauticsDeferredShaftAgent.java` tests a narrowly scoped upstream proposal:
replace the two eager `AllBlocks.SHAFT` reads feeding Aeronautics' encasing
transforms with deferred suppliers. It requires exactly two matching sites in
Aeronautics 1.3.0; a valid candidate run must print
`AERONAUTICS_CANDIDATE_TRANSFORMED lookups=2`. A transformer exception alone is
not a reliable launch failure because Java instrumentation can continue loading
the original class. Reject runs missing the explicit transformation marker.

Compile with Java 21:

```sh
mkdir -p build/registrate-candidate/classes
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED \
  -d build/registrate-candidate/classes tools/compatibility/AeronauticsDeferredShaftAgent.java
printf 'Premain-Class: AeronauticsDeferredShaftAgent\n' > build/registrate-candidate/MANIFEST.MF
jar cfm build/registrate-candidate/agent.jar build/registrate-candidate/MANIFEST.MF \
  -C build/registrate-candidate/classes .
```

Add both ASM exports to an isolated client JVM, the ownership agent above, and
`-javaagent:/absolute/path/to/build/registrate-candidate/agent.jar=candidate`.
Use `=control` for the unmodified Aeronautics comparison. Quick-connect to a
reserved, non-listening loopback port in a fresh game directory; entering the
connection screen signals that registration has completed. Require
`CANDIDATE_REGISTRY_PROBE_PASS`, compare the complete sorted block/item/entity
registry IDs and shaft-variant lists, and preserve duplicates in those lists.
The original artifact registers each of its 16 colored shaft variants twice;
the candidate deliberately preserves that behavior (32 deferred calls).

This verifies registration and the encasing table, not an in-world shaft
encasing interaction. Instrumentation changes scheduling. Successful controls
and candidates cannot establish a reduction in crash frequency when neither
reproduces the historical crash. This tool is diagnostic only and is excluded
from the release JAR.
