# Annotation Scanning

*A boundary was walked, not consulted. The surveyor and the neighbours went the line together and put a hand on
each terminus in turn — and where a stone was gone, that went in the record as a stone that was gone. Nobody
concluded that the field had no edge there.*

`client-scanner` walks the packages you name, finds the annotated classes, and registers them — so thirty types
are named once instead of thirty times.

```kotlin
val db = OctaviusClient.fromDataSource(dataSource)
db.dynamicTypes.install()

val found = db.registerAnnotatedTypes("com.roma.domain", "com.roma.dto")
logger.info { "Octavius registered $found" }
```

```kotlin
dependencies {
    implementation("io.github.octavius-framework:client-scanner:0.9.8")
}
```

## Why It Is a Module of Its Own

It does nothing the hand-written calls cannot. `typeManager.registerEnum`, `registerAutoComposite` and
`dynamicTypes.register` are the whole of what it ends up doing, and registering by hand stays fully supported —
this is for the application with enough types that naming them individually is where the next one gets
forgotten.

What it needs that those do not is a way to walk a classpath **correctly**: jars inside jars, the module path,
a Spring Boot fat jar. That is a dependency ([ClassGraph](https://github.com/classgraph/classgraph)) worth
keeping off everyone who registers by hand, which is the whole reason for the separate coordinate.

## The Annotations

The annotations are **not** here. They live in the multiplatform `pg-model` module, which the driver takes
as an `api` dependency — so a class shared with another platform can carry them in `commonMain` and still be
found on the JVM. A desktop application and a browser extension reading the same DTOs was the case that forced
it: an annotation compiled only for the JVM cannot be written on a class in `commonMain` at all.

| Annotation             | Goes on      | Registers as                  |
|------------------------|--------------|-------------------------------|
| `@PgEnumType`          | an enum      | a PostgreSQL `ENUM` type      |
| `@PgCompositeType`     | a data class | a PostgreSQL `COMPOSITE` type |
| `@DynamicallyMappable` | any class    | a `dynamic_dto` discriminator |

`@PgEnumType` carries the same case conventions `registerEnum` takes, and they reach further than the column:
a scanned enum used inside a `dynamic_dto` payload is written under those same labels, with no serializer to
write and no `@Serializable` on the enum. Mark the property `@Contextual` and that is the whole of it — see
[What JSON Does Not Carry](dynamic-dto.md#what-json-does-not-carry).

It and `@PgCompositeType` derive the type name from the class where none is given — `ScanRank` to `scan_rank`
— using the driver's own converter. **`@DynamicallyMappable` states its name and always will**: that one is a
discriminator stored in the data, so deriving it would mean a class rename silently orphaning every row written
before it. See [Registering a Class](dynamic-dto.md#registering-a-class).

`@PgName` is the fourth annotation in that module and is not scanned for — it overrides one attribute's name
and the driver reads it during mapping.

## What a Scan Reports

```kotlin
data class ScanReport(
    val enums: List<RegisteredType>,
    val composites: List<RegisteredType>,
    val dynamicTypes: List<RegisteredType>,
    val unresolved: List<RegisteredType>
)
```

`total` and `isEmpty()` come with it. A scan that matched nothing logs a warning rather than passing quietly —
a package name with a typo in it otherwise registers nothing and says nothing.

Each enum and composite name is checked against the database, and the ones the database has no type for go into
`unresolved`. **Reported, not refused.**

Registering ahead of a type that does not exist yet is a working flow: converters survive a `reloadTypes()`, so
a name missing at scan time is a question rather than an answer — you may be about to run the migration that
creates it. The other way to get there is a typo, which is otherwise silent until some query cannot map a
column. So the scan says it out loud and leaves the decision where it belongs:

```kotlin
val found = db.registerAnnotatedTypes("com.roma.domain")
check(found.unresolved.isEmpty()) { "Octavius: no type in the database for ${found.unresolved}" }
```

Registration itself never checked — not here and not in the driver's own `registerEnum`. This does not change
that; it only says what it saw.

`registerAnnotatedTypes` takes an optional `classLoader` for the container that does not hand its classes to
the context loader.

## Next

- [`dynamic_dto`](dynamic-dto.md) — what `@DynamicallyMappable` registers into
- [Type System](../driver/type-system.md#registering-your-own-mappings) — the calls this is a shortcut for
