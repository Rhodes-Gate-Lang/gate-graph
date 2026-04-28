# gate-graph

Tools for working with **`.gateo`** gate graphs (for example, turning them into diagrams). This repo wires up **[gateo-java](https://github.com/Rhodes-Gate-Lang/gateo-java)** so Java code can load compiled graphs as native objects.

## Requirements

- **JDK 17+** (Gradle uses a Java 17 toolchain).

## Gradle layout

- **`build.gradle.kts`** — Java application with two runtime pieces:
  - **`gateo-java`**: the library JAR is **not** on Maven Central; the build downloads it from [GitHub Releases](https://github.com/Rhodes-Gate-Lang/gateo-java/releases) into `build/gateo-release/` before compilation. The version is the `gateoJavaVersion` value near the top of the file (currently **2.0.2**).
  - **`protobuf-java`**: declared explicitly so the classpath matches the POM shipped with `gateo-java` (same version the library was built against).

Common commands:

```bash
./gradlew run              # run the smoke main (see below)
./gradlew build            # compile + tests (when you add tests)
./gradlew clean run        # force a fresh download of the gateo-java JAR
```

To try another **`.gateo`** path without editing code:

```bash
./gradlew run --args="/absolute/or/relative/path/to/file.gateo"
```

The default in [`GateoSmoke`](src/main/java/com/rhodesgatelang/gategraph/GateoSmoke.java) is `../FullAdder.gateo` relative to the project directory (the file in the parent **gate-lang** checkout).

## Using `GateObject` in code

Use the **`com.rhodesgatelang.gateo`** facade for I/O and **`com.rhodesgatelang.gateo.v3`** for the immutable graph model.

### Reading and writing

```java
import com.rhodesgatelang.gateo.Gateo;
import com.rhodesgatelang.gateo.v3.GateObject;
import java.nio.file.Path;

Path file = Path.of("design.gateo");
GateObject graph = Gateo.read(file);

// Optional: write back or round-trip bytes
Gateo.write(Path.of("out.gateo"), graph);
byte[] wire = Gateo.toBytes(graph);
GateObject again = Gateo.read(wire);
```

`Gateo.read` checks the on-wire schema major (v2 only) and runs light structural validation. Failures throw **`GateoParseException`**, **`GateoValidationException`**, **`VersionException`**, or **`GateoIOException`** as documented in [gateo-java](https://github.com/Rhodes-Gate-Lang/gateo-java).

Avoid importing the generated protobuf **`gateo.v2.Gateo`** type with a simple name in the same file as **`com.rhodesgatelang.gateo.Gateo`** — the class names collide. Prefer the facade for normal use, or use a fully qualified generated type only when you need raw protobuf access.

### Walking the graph

`GateObject` is a **record** with three fields:

| Part | Role |
|------|------|
| **`version()`** | `Version(major, minor)` from the file |
| **`components()`** | `List<ComponentInstance>` — instance tree; index **0** is the synthetic root (`parent` is **0**) |
| **`nodes()`** | `List<Node>` — gates in topological-ish order; operand indices are positions in this list |

**`ComponentInstance`**: `name()`, `parent()` (index into `components()`).

**`Node`**: `type()` (`GateType` enum: `INPUT`, `OUTPUT`, `AND`, `OR`, `XOR`, `NOT`, `LITERAL`, …), `inputs()` (`List<Integer>` node indices), `width()`, `parent()` (which component instance owns this node), `name()`, `literalValue()` (both `Optional` / `OptionalLong`).

Example loop (same idea as `GateoSmoke`):

```java
for (int i = 0; i < graph.nodes().size(); i++) {
  Node n = graph.nodes().get(i);
  // n.type(), n.inputs(), n.parent() index into components(), etc.
}
```

For full API details and schema pinning, see the **[gateo-java README](https://github.com/Rhodes-Gate-Lang/gateo-java/blob/main/README.md)**.

## License

See [LICENSE](LICENSE).
