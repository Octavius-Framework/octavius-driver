# Type System and Mapping

Type mapping in `octavius-driver` rests on a flexible **2-layer architecture** that keeps low-level binary wire communication cleanly separated from high-level mapping onto Kotlin objects.

Two things are worth internalizing before the details:

* **The type catalog is read from the database, not hardcoded.** On the first connection to a given database, the driver queries `pg_catalog` and builds a full picture of every type living there — including *your* enums, composites, domains, ranges and table row types.
* **`session.typeManager` is the door to that catalog, but the room behind it is shared.** The handle is per-session; the state is per-database and JVM-wide. Registering a converter through one session changes behavior for every other session pointing at the same database. See [Scope: a session handle over global state](#scope-a-session-handle-over-global-state).

Contents:
* [Where types come from](#where-types-come-from)
* [2-Layer architecture](#2-layer-architecture)
* [How a value actually travels](#how-a-value-actually-travels)
* [`TypeManager` — the entry point](#typemanager--the-entry-point)
* [Basic codecs](#basic-codecs)
* [Basic converters](#basic-converters)
* [Registering your own mappings](#registering-your-own-mappings)
* [Practical rules and gotchas](#practical-rules-and-gotchas)

## Where types come from

### The catalog load

PostgreSQL identifies every type by an **OID** (Object Identifier). Built-in OIDs are stable (`int4` is always `23`), but the OIDs of anything *you* create — `CREATE TYPE legio_status AS ENUM (...)`, a composite, a domain, even the implicit row type of a table — are assigned per database and per `CREATE`. They cannot be baked into a driver.

So the driver asks. On the first physical connection to a given database within the JVM, `GlobalTypeRegistry.ensureLoaded` fires `TypeRegistryLoader`, which runs a **single** query joining `pg_type` with `pg_namespace`, `pg_enum`, `pg_range` and `pg_attribute`. One round trip, then every later connection to the same database reuses the result.

The loader deliberately skips noise: composites belonging to `pg_catalog` and `information_schema`, and all pseudo-types except `void`, `record` and `_record`.

What comes back is classified into a sealed `PgType` hierarchy:

| `PgType` variant    | PostgreSQL `typtype`   | Extra information carried         |
|:--------------------|:-----------------------|:----------------------------------|
| `PgType.Base`       | `b`                    | —                                 |
| `PgType.Array`      | (element type present) | `elementOid`                      |
| `PgType.Composite`  | `c`                    | ordered `attributes` (name → OID) |
| `PgType.Enum`       | `e`                    | `values` in declaration order     |
| `PgType.Domain`     | `d`                    | `baseTypeOid`                     |
| `PgType.Range`      | `r`                    | `subtypeOid`                      |
| `PgType.Multirange` | `m`                    | `rangeOid`                        |
| `PgType.Record`     | `p` (`record`)         | the anonymous row pseudo-type     |
| `PgType.Void`       | `p` (`void`)           | return type of void functions     |

The result is a `TypeDictionary` — an **immutable snapshot** with pre-computed lookups by OID, by name+schema, array-by-element, range-by-subtype and multirange-by-range. Reloading swaps the whole snapshot atomically rather than mutating it in place, so readers never see a half-updated catalog.

### Codecs are synthesized for what the driver has never seen

Loading the catalog also rebuilds the `CodecDictionary`. An OID with no explicitly registered codec gets a **dynamic** one generated on the spot — but only if it belongs to one of the *structural* categories, the ones whose wire format the driver can derive from the catalog alone:

| Discovered type       | Synthesized codec       | Decodes to                           |
|:----------------------|:------------------------|:-------------------------------------|
| enum                  | `DynamicEnumCodec`      | `String`                             |
| domain                | `DynamicDomainCodec`    | *delegates to the base type's codec* |
| array                 | `DynamicContainerCodec` | `PgArray`                            |
| composite / table row | `DynamicContainerCodec` | `PgComposite`                        |
| anonymous `record`    | `DynamicContainerCodec` | `PgRecord`                           |
| range / multirange    | `DynamicContainerCodec` | `PgRange` / `PgMultirange`           |

This is why a type *you* create is never a hard failure: worst case you get the generic representation back (`PgComposite`, `String`, ...) and can map it upward yourself. The converter layer is what turns those into your own classes.

### Base types the driver does not implement

A `PgType.Base` is a different story. Its binary layout is specific to that one type and can't be inferred from the catalog, so it needs a hand-written codec — and only the ones in [Basic codecs](#basic-codecs) have one. Everything else is discovered by the loader, appears in the `TypeDictionary`, and still has **no codec**. Notably absent today:

`money`, `timetz`, `tsvector`, `tsquery`, `jsonpath`, `pg_lsn`, `pg_snapshot`/`txid_snapshot`, `aclitem`, `tid`/`xid`/`cid`, the `reg*` types (`regclass`, `regproc`, ...), `int2vector`/`oidvector`.

Selecting such a column throws `TypeException(MISSING_CODEC)` naming the OID, rather than returning something plausible-but-wrong. Two ways forward:

* **Cast in SQL** — `SELECT to_tsvector('latin') :: text` or `SELECT amount::numeric`. Cheap, and usually enough.
* **Write the codec** — implement `TypeCodec<T>` against PostgreSQL's binary format for that type and `registerCodec` it. See [Custom codecs](#custom-codecs).

The same applies inside containers: an `int4[]` decodes fine, a `tsquery[]` fails on its elements, since the array codec still has to decode each element through the element type's codec.

### Keeping the catalog fresh — `reloadTypes()`

The catalog snapshot is taken once. DDL executed **after** that point is invisible to the driver until you say so:

```kotlin
session.createNativeQuery("CREATE TYPE legio_status AS ENUM ('ON_CAMPAIGN', 'IN_GARRISON')").execute()
session.createNativeQuery("CREATE TYPE tribute AS (amount int, currency text)").execute()

session.reloadTypes() // re-reads pg_catalog for this database
```

Worth knowing about a reload:

* It is **global too** — it refreshes the shared registry for the whole database, not just this session.
* `CREATE TABLE` also creates a type (the table's row type), so table DDL counts.
* Custom **codecs survive**: registered codecs are replayed and re-bound against the new OIDs.
* Custom **converters and composite registrations are untouched** — they live in the converter registry, which a reload doesn't rebuild.
* In an application with a static schema, you typically never call it. In tests, migrations, or anything that creates types at runtime, call it once after the DDL.

### One registry per database, not per URL

Registries are cached in `GlobalTypeRegistry` under a `RegistryKey` of **host + port + database name** — deliberately *not* the whole connection URL:

```kotlin
internal data class RegistryKey(val host: String, val port: Int, val database: String)
```

Credentials, SSL settings and timeouts have no effect on the type catalog. Keying on them would fragment the cache (and would keep a password alive as a map key for the lifetime of the JVM). Practical consequences:

* Two HikariCP pools connecting as different users to the same database **share one registry** — and therefore one set of registered converters.
* Registration performed through *any* pool's session is visible to the others.
* If your application connects to thousands of *different* databases at runtime (a per-tenant-database setup, say), call `GlobalTypeRegistry.removeRegistry(url)` when you close a data source, so the registry can be collected. With a static set of URLs — the normal case — leave it alone.

## 2-Layer architecture

1. **Codecs Layer (`TypeCodec<T>`)**
    * **Role:** the direct translation between basic Kotlin types and PostgreSQL's native binary format (`ByteArray` and `PgByteWriter`).
    * **Operation:** codecs work at a low level, serializing and deserializing with full awareness of PostgreSQL type OIDs. The interface is deliberately tiny — `pgTypeName`, `pgSchema`, `oid`, `kotlinClass`, `isDefaultForKotlinType`, plus the `fromBinary` / `toBinary` function pair.
    * **Registration:** centrally managed by `TypeRegistry`, which associates codecs by Kotlin class or by the OID defined in the database.
    * **Errors:** anything thrown inside `fromBinary` / `toBinary` is caught and re-thrown as a `CodecException` carrying the type name, schema, OID and a truncated copy of the offending bytes.

2. **Converters Layer (`ResultConverter<S, T>` / `ParameterConverter<T>`)**
    * **Role:** a higher level of abstraction, mapping the intermediate structures codecs decode (`PgComposite`, `PgArray`, `PgRecord`, `Row`) onto whatever complex, user-defined structures you actually want.
    * **Operation:** handles reflective mapping onto classes (data classes), transformation into collections (`Collection<*>`), maps (`Map<String, Any?>`), and nested objects.
    * **Context:** `SerializationContext` and `DeserializationContext` recursively resolve and convert nested types within complex structures, bridging the object layer and the binary layer smoothly in both directions. Both expose the `typeManager`, so a converter can resolve OIDs or build containers mid-conversion.
    * **Errors:** failures surface as a `MappingException` whose `path` accumulates the segment names it passed through — so a bad field five levels down in a nested composite tells you *which* field.

Thanks to this split, adding support for a specific custom PostgreSQL type is usually just writing a small, focused codec — the reflective work of wiring it into data classes and collections is handled automatically by the generic converter layer above it.

## How a value actually travels

```
 read   raw bytes ──▶ TypeCodec.fromBinary ──▶ intermediate value ──▶ ResultConverter ──▶ Senator
                      (chosen by column OID)   String, Int, PgComposite…   (at row.get<T>())

 write  Senator ──▶ ParameterConverter ──▶ intermediate value ──▶ TypeCodec.toBinary ──▶ raw bytes
                    (chosen by Kotlin class)  PgComposite, String…   (by OID, else by class)
```

### Reading (database → Kotlin)

1. A `DataRow` message arrives. For **every** column, the driver looks up a codec by the column's OID and decodes the bytes immediately — a `Row` is fully decoded at construction time. Missing codec for an OID means `TypeException(MISSING_CODEC)`.
2. The results are *intermediate* values: `Int`, `String`, `PgComposite`, `PgArray`, and so on.
3. Nothing else happens until you ask. `row.get<Senator>("senator")` hands the intermediate value, the requested `KType` and the source `PgType` to the `ResultMapper`.
4. The mapper picks a `ResultConverter` (see [precedence](#how-a-converter-gets-chosen)) and calls it. Converters recurse through `context.convert(...)` for nested attributes, elements and fields.
5. If no converter matches but the value is already an instance of the requested class, it's passed straight through. Otherwise: `MappingException(NO_CONVERTER_FOUND)`.
6. A `null` requested as a non-nullable Kotlin type raises `MappingException(REQUIRED_ATTRIBUTE_MISSING)` rather than a `NullPointerException` somewhere later.

Because step 1 is eager and steps 3–5 are lazy, the cost of `row.get<T>()` is conversion only — and asking the same row for two different shapes (`get<Map<String, Any?>>` and `get<Senator>`) decodes the wire data just once.

### Writing (Kotlin → database)

Note the ordering: parameters are serialized **before** anything is sent, and the OIDs the driver derives here are what it declares to PostgreSQL in the `Parse` message. As in other PostgreSQL drivers, the types aren't negotiated with the server — the client decides, and binary format means it has to.

1. If the value is wrapped in `PgTyped`, its type name is resolved to an OID right away (honoring this session's `search_path`) and the wrapper is unwrapped. Otherwise the expected OID starts out **unresolved**.
2. A `PgContainer` (`PgComposite`, `PgArray`, `PgRange`, ...) already knows its own OID and short-circuits straight to serialization.
3. Otherwise the `ParameterConverter` chain runs, newest registration first, first match wins. No match at all means the value passes through untouched.
4. If the converter's output is still untyped and the OID is still unresolved, the converter's `getDefaultTypeName()` is consulted and the result gets wrapped in `PgTyped`. This is how a registered enum gets its type name attached without you writing `::legio_status` in the SQL — the converter states what the client couldn't otherwise know.
5. Serialization picks the codec **by OID** when one is known, otherwise **by Kotlin class** — which only works for codecs marked `isDefaultForKotlinType`. For plain scalar parameters this second branch is the usual one; see [One default per Kotlin class](#one-default-per-kotlin-class).
6. The OID that goes into `Parse` is the resolved one, or the OID the chosen codec is bound to, or a last-resort resolution of the codec's type name against the session's search path.

Values nested inside a composite or an array are a separate case: there the enclosing type's catalog definition supplies each attribute's or element's OID, so step 5 always takes the by-OID branch.

## `TypeManager` — the entry point

`TypeManager` is the public, high-level API over the internal `TypeRegistry`. You reach it from any session:

```kotlin
val session = dataSource.getOctaviusSession()
session.typeManager.registerEnum<LegioStatus>()
```

### What it exposes

| Member                                  | Purpose                                                                                        |
|:----------------------------------------|:-----------------------------------------------------------------------------------------------|
| `resolveOid(typeName, schema, isArray)` | Resolves a type name to its OID, honoring this session's `search_path`.                        |
| `registerCodec(codec)`                  | Binary-level mapping for a PostgreSQL type.                                                    |
| `registerResultConverter(converter)`    | Database → Kotlin object mapping.                                                              |
| `registerParameterConverter(converter)` | Kotlin object → database parameter mapping.                                                    |
| `registerEnum<T>(...)`                  | Registers both directions for a Kotlin enum in one call.                                       |
| `registerAutoComposite<T>(...)`         | Maps a data class to a PostgreSQL composite type reflectively.                                 |
| `typeDictionary`                        | The current catalog snapshot — `getPgType(oid)`, `getArrayType(...)`, `forEachType { }`.       |
| `codecDictionary`                       | Codec lookups — `getCodecByOid(...)`, `getCodecByClass(...)`.                                  |
| `converterRegistry`                     | The registered converters and composite registrations.                                         |
| `containers`                            | `ContainerFactory` — builds `PgComposite`, `PgRange`, `PgMultirange` instances by name or OID. |

### Scope: a session handle over global state

This is the part that surprises people, so it's worth stating bluntly:

> **`typeManager` is created per session, but the registry it writes to is shared per database across the whole JVM.**

`OctaviusSessionImpl` constructs a fresh `TypeManager` for each session, but hands it the `TypeRegistry` that `GlobalTypeRegistry` keeps for that database. Nothing is copied. So:

* Registering an enum, composite, converter or codec through **one** session affects **every** session on that database — those already open, those still to be opened, and those borrowed from a different connection pool.
* Closing the session that performed the registration changes nothing; the registration outlives it.
* The type catalog itself is loaded once and shared the same way.

The practical shape this leads to is a **single registration step at application startup**, not per-request wiring:

```kotlin
// Once, at startup — e.g. in a Spring @PostConstruct or an init block
dataSource.getOctaviusSession().use { session ->
    session.typeManager.registerEnum<LegioStatus>()
    session.typeManager.registerAutoComposite<Address>()
    session.typeManager.registerAutoComposite<SenatorProfile>()
}
// Every session opened afterwards already knows about all three.
```

Doing it per request isn't just wasted work — registries are copy-on-write lists, and re-registering the same converter **prepends another copy** every time. The newest one wins so behavior stays correct, but the list grows without bound and lookups get slower. Register once.

### What *is* per-session: the search path

The one genuinely session-scoped ingredient is the schema search path. `TypeManager` is constructed with a `searchPathProvider` bound to its connection, and `resolveOid` passes it through:

```kotlin
session.setSearchPath("imperium", "public")
session.getSearchPath() // -> [imperium, public]

session.typeManager.resolveOid("legio_status")             // resolved via search_path
session.typeManager.resolveOid("legio_status", "imperium") // explicit schema, search_path ignored
```

Resolution order for a name with no explicit schema:

1. An explicitly requested schema always wins (and throws if the type isn't there).
2. Otherwise, schemas are tried in `search_path` order — first match wins.
3. If the name doesn't appear in the search path at all but exists in exactly **one** schema, that one is used.
4. If it exists in several schemas and none is in the search path: `TypeException` — *"Ambiguous type. Schema must be specified."*

So two sessions with different search paths can resolve the same type name to different OIDs, even though they share one registry. If you have a multi-schema database with colliding type names, pass the schema explicitly at registration time (`registerEnum<LegioStatus>(schema = "imperium")`).

### Query-scoped overrides

When a global registration is too big a hammer, converters can be scoped to a **single query**. Each query object creates child registries whose parent is the global one:

```kotlin
val senators = session.createNamedQuery("SELECT * FROM senators WHERE ordo = @ordo")
    .registerResultConverter(OneOffSenatorConverter())   // this query only
    .fetchObjects<Senator>("ordo" to "patrician")
```

The child registry is consulted first and falls back to the global one, so a query-scoped converter overrides a global converter for that query and disappears with the query object. Nothing global is mutated.

| Registered on              | Visible to                                          | Lifetime                                        |
|:---------------------------|:----------------------------------------------------|:------------------------------------------------|
| `query.register*Converter` | that one query instance                             | until the query is discarded                    |
| `session.typeManager.*`    | every session on that host+port+database in the JVM | lifetime of the JVM (or until `removeRegistry`) |

### Thread safety and cost

All registries are built for **many readers, rare writers**:

* `TypeDictionary` and `CodecDictionary` are immutable; updates build a new instance and publish it through a `@Volatile` field under a `ReentrantLock`.
* Converter registries hold `@Volatile` copy-on-write collections; new entries are inserted at the front, so **the most recently registered converter wins**.
* Reads — every query, every row, every conversion — take no locks at all.

The flip side is that each registration copies the collection it touches. Cheap a handful of times at startup, wasteful in a hot path.

## Basic codecs

The `io.github.octaviusframework.driver.codec` package ships the codecs translating between PostgreSQL and Kotlin types.

| PostgreSQL Type                                             | Kotlin Type                                                               | Notes                                            |
|:------------------------------------------------------------|:--------------------------------------------------------------------------|:-------------------------------------------------|
| `int2`                                                      | `Short`                                                                   |                                                  |
| `int4`                                                      | `Int`                                                                     |                                                  |
| `int8`                                                      | `Long`                                                                    |                                                  |
| `float4`                                                    | `Float`                                                                   |                                                  |
| `float8`                                                    | `Double`                                                                  |                                                  |
| `numeric`                                                   | `java.math.BigDecimal`                                                    |                                                  |
| `text`, `varchar`, `unknown`, `bpchar` (`character`)        | `String`                                                                  |                                                  |
| `json`, `jsonb`                                             | `String`                                                                  | Processed later by JSON converters               |
| `timestamptz`                                               | `kotlin.time.Instant`                                                     | <sup>1</sup>                                     |
| `timestamp`                                                 | `kotlinx.datetime.LocalDateTime`                                          | <sup>1</sup>                                     |
| `date`                                                      | `kotlinx.datetime.LocalDate`                                              | <sup>1</sup>                                     |
| `time`                                                      | `kotlinx.datetime.LocalTime`                                              |                                                  |
| `interval`                                                  | `PgInterval`                                                              |                                                  |
| `bool`                                                      | `Boolean`                                                                 |                                                  |
| `bytea`                                                     | `ByteArray`                                                               |                                                  |
| `uuid`                                                      | `kotlin.uuid.Uuid`                                                        |                                                  |
| `xml`                                                       | `String`                                                                  |                                                  |
| `bit`, `varbit`                                             | `java.util.BitSet`                                                        |                                                  |
| `inet`, `cidr`, `macaddr`, `macaddr8`                       | `String`                                                                  | String preserves original notation e.g., `/24`   |
| `point`, `line`, `lseg`, `box`, `path`, `polygon`, `circle` | `PgPoint`, `PgLine`, `PgLseg`, `PgBox`, `PgPath`, `PgPolygon`, `PgCircle` | Mapped to native driver geometric data classes   |
| `void`                                                      | `Unit`                                                                    | Return type of void functions (e.g. `pg_notify`) |
| `oid`, `name`, `"char"`                                     | `Int`, `String`, `String`                                                 | Internal PostgreSQL types                        |
| `array`                                                     | `PgArray`                                                                 | Evaluated at runtime                             |
| `composite`, `record`                                       | `PgComposite`, `PgRecord`                                                 | Evaluated at runtime                             |
| `enum`                                                      | `String`                                                                  | Evaluated at runtime                             |
| `domain`                                                    | *(Base type)*                                                             | Delegates to the codec of the underlying type    |

All of these carry a fixed, hardcoded `oid`, so they work before the catalog is even loaded. The rows marked *"Evaluated at runtime"* are the dynamic codecs from [the catalog load](#codecs-are-synthesized-for-what-the-driver-has-never-seen) — those OIDs differ per database, so there is nothing to hardcode. Anything not on this list and not structural has no codec at all: see [Base types the driver does not implement](#base-types-the-driver-does-not-implement).

### One default per Kotlin class

Several PostgreSQL types map onto the same Kotlin class — `text`, `varchar`, `bpchar`, `json`, `jsonb`, `xml`, `inet`, `name` all decode to `String`. Decoding is unambiguous, because the codec is chosen by the column's OID. Encoding is not: given a bare `String` parameter, which type should it go out as?

The tie-break is `isDefaultForKotlinType`. Exactly one codec claims each Kotlin class, and it decides whenever the driver has no OID to work from:

| Kotlin class                                                                             | Default PostgreSQL type                                                        | Also decoded by (not default)                                                                                          |
|:-----------------------------------------------------------------------------------------|:-------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------|
| `String`                                                                                 | `text`                                                                         | `varchar`, `bpchar`, `unknown`, `json`, `jsonb`, `xml`, `inet`, `cidr`, `macaddr`, `macaddr8`, `name`, `"char"`, enums |
| `Int`                                                                                    | `int4`                                                                         | `oid`                                                                                                                  |
| `java.util.BitSet`                                                                       | `bit`                                                                          | `varbit`                                                                                                               |
| `Short`, `Long`, `Float`, `Double`, `BigDecimal`, `Boolean`, `ByteArray`, `Uuid`, `Unit` | `int2`, `int8`, `float4`, `float8`, `numeric`, `bool`, `bytea`, `uuid`, `void` | —                                                                                                                      |
| `Instant`, `LocalDateTime`, `LocalDate`, `LocalTime`, `PgInterval`                       | `timestamptz`, `timestamp`, `date`, `time`, `interval`                         | —                                                                                                                      |
| `PgPoint`, `PgLine`, `PgLseg`, `PgBox`, `PgPath`, `PgPolygon`, `PgCircle`                | `point`, `line`, `lseg`, `box`, `path`, `polygon`, `circle`                    | —                                                                                                                      |

This is the **common** path, not a rare fallback — so it's worth knowing where those OIDs come from.

The client decides them. Parameters are serialized first, the driver derives an OID for each one, and those OIDs are declared to PostgreSQL in the `Parse` message; the `Describe` that follows targets the portal, i.e. the shape of the *result*, not the parameters. That's ordinary PostgreSQL driver behavior rather than anything specific to Octavius — pgjdbc likewise takes its parameter types from the `setXxx` call you made, not from the server.

Octavius has even less room to defer, because it is **binary in both directions**: `Bind` declares format code 1 for every parameter and every result column. The classic escape hatch — declaring a parameter with the unspecified OID and letting the server coerce it from context, which is what pgjdbc's `stringtype=unspecified` buys you — depends on the text format. A binary representation is specific to one type, so the codec has to be chosen *before* a single byte is written. There is nothing to defer.

The one exception is a null with no type hint: it's declared as OID `0` and carries no bytes at all, so the server resolves it from context.

For every other top-level parameter that isn't wrapped in `PgTyped`, the default-per-class codec *is* the answer. If you're arriving from pgjdbc, this is the same class of problem `stringtype` exists to solve — just addressed per value instead of per connection.

Where an OID **is** known, and the default therefore doesn't apply:

* the value was wrapped in `PgTyped`;
* the value is — or converts into — a `PgContainer` (`PgComposite`, `PgArray`, `PgRange`, ...), which carries its own OID and is therefore self-describing;
* the value is *nested* — a composite attribute or an array element — where the OID comes from the catalog definition of the enclosing type;
* a `ParameterConverter` supplied one through `getDefaultTypeName()`, which is how a converter whose output is a plain scalar declares its target type. Registered enums rely on this, as does the built-in `jsonb` converter.

Everywhere else, a bare `String` goes out as `text` and a bare `Int` as `int4`. That's right far more often than not, but it's also why sending a `String` into a `jsonb`, `inet` or unregistered enum column needs the type stated explicitly:

```kotlin
// Without the hint this String is declared as `text` in Parse
session.createNamedQuery("INSERT INTO senators (dossier) VALUES (@dossier)")
    .update("dossier" to jsonString.withPgType("jsonb"))
```

### Infinity Values for Date/Time

<sup>1</sup> PostgreSQL's special `infinity` / `-infinity` values are fully supported for date and timestamp types, via dedicated constants:

| PostgreSQL Type | Special Values          | Kotlin Constants                                             |
|-----------------|-------------------------|--------------------------------------------------------------|
| `date`          | `infinity`, `-infinity` | `LocalDate.DISTANT_FUTURE`, `LocalDate.DISTANT_PAST`         |
| `timestamp`     | `infinity`, `-infinity` | `LocalDateTime.DISTANT_FUTURE`, `LocalDateTime.DISTANT_PAST` |
| `timestamptz`   | `infinity`, `-infinity` | `Instant.DISTANT_FUTURE`, `Instant.DISTANT_PAST`             |

Handy, incidentally, for anything modeled as lasting "in perpetuity" — an empire's founding decree, say, with no scheduled end date.

### Numeric (BigDecimal) Special Values

Unlike dates, the driver **does not** map PostgreSQL `numeric` special values (`NaN`, `Infinity`, `-Infinity`) to Kotlin. 
Java and Kotlin use `java.math.BigDecimal` for exact-precision decimal types, and that class mathematically prohibits non-finite values by design. 
If your query retrieves a `numeric` column containing `NaN` or `Infinity`, the driver will immediately throw an `IllegalArgumentException` / `TypeException` to prevent silent data corruption (such as treating Infinity as zero).

If your domain logic genuinely relies on `NaN` or `Infinity` (e.g. sensor readings, AI analysis), you should either:
1. Use standard IEEE 754 floating-point types (`float4` / `float8`), which map to Kotlin's `Float` and `Double` and fully support non-finite values.
2. Override the default `NumericCodec` with your own custom codec that maps the `numeric` OID (1700) to a custom Kotlin wrapper class capable of representing both exact decimals and non-finite concepts.

### PgInterval

By default, PostgreSQL's `interval` type maps to the driver's own `PgInterval` class rather than to a stock Kotlin type like `kotlin.time.Duration` or `kotlinx.datetime.DateTimePeriod`.

That's a deliberate choice — there's no single clean equivalent in the Kotlin standard library:
* **Database-side calculation is usually the right call.** Interval math belongs in the database, where timezones and variable-length dates are already handled correctly.
* **`Duration` has limits.** It's tempting to reach for `Duration`, but it's based on absolute time and can't represent variable-length calendar units — days and months — accurately. Converting approximately (assuming 1 month = 30 days, 1 day = 24 hours) can quietly introduce drift.
* **`DateTimePeriod` has its own limits.** It's exact, and does support months/days, but it's often awkward to actually compute with.

So when you extract an interval, you get a `PgInterval` that preserves the raw database representation — months, days, microseconds — as-is. `PgInterval` exposes explicit extensions like `toDurationApproximate()`, `toDurationExact()`, and `toDateTimePeriod()`, so you decide how (and whether) to collapse it.

If your application consistently wants, say, an approximate `Duration`, writing and registering a custom `ResultConverter` lets you intercept and convert `PgInterval` values everywhere, automatically.

### PgTyped

`PgTyped` wraps a value so you can explicitly declare the PostgreSQL type it should be sent as. It matters wherever the driver can't work the type out on its own — an empty collection being the classic case.

Why an empty collection is a problem is worth spelling out, because the blame lies on this side of the wire. When no target OID is known, the array converter infers the element type by looking at the **first non-null element's runtime class**. The JVM erases generics, so an empty `List<Int>` and an empty `List<String>` are the same object at runtime with nothing to inspect — Kotlin's `reified` doesn't help either, since by the time the value reaches the serializer it is an `Any?` in an array of parameters. The driver refuses to guess and throws `TypeException(TYPE_NOT_FOUND)`; the same happens for a collection holding only nulls.

Wrap any value with the `.withPgType(...)` extension functions:
* `value.withPgType(PgStandardType.INT4_ARRAY)`
* `value.withPgType("legio_status")` — a custom enum type, say, for a legion's current campaign status
* `value.withPgType("legio_status", schema = "imperium", isArray = true)` — pinned to a schema, as an array

The wrapped name is resolved through the session's search path while the parameter is serialized, and the resulting OID is what the driver declares in `Parse` — which is why `PgTyped` is the right escape hatch for ambiguous parameters and `::cast` in the SQL usually isn't necessary. Nesting one `PgTyped` inside another throws — the wrapper is meant to be the outermost layer. Data classes registered with `registerAutoComposite` get their type attached automatically, so wrapping them by hand is unnecessary.

## Basic converters

Converters, in the `io.github.octaviusframework.driver.converter` package, split into those deserializing query results (`ResultConverter`) and those preparing query parameters (`ParameterConverter`).

### ResultConverters (Reading from DB to objects)

These fire when you pull data out of a row (e.g. `row.get(TargetClass::class)`) — they decide what Kotlin shape comes back based on the class you asked for.

| Converter Class                                              | Returns (Output Type)                  | When Used (Target Class in `get`)                                           | Description                                                                                                     |
|:-------------------------------------------------------------|:---------------------------------------|:----------------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------|
| `ReflectionRowConverter` <br> `ReflectionCompositeConverter` | Kotlin Data Class                      | Data classes (e.g., `Senator::class`)                                       | Maps result rows (`Row`) and complex DB types (`PgComposite`) directly onto Kotlin data classes via reflection. |
| `MapRowConverter` <br> `MapCompositeConverter`               | `Map<String, Any?>`                    | `Map::class`                                                                | Decodes straight to a universal dictionary — handy when the schema isn't fully known up front.                  |
| `MapRecordConverter`                                         | `Map<String, Any?>`                    | `Map::class`, `Any::class`                                                  | Handles anonymous PostgreSQL `record` types, decoded into a dictionary.                                         |
| `CollectionArrayConverter`                                   | `Collection<T>` <br> (e.g., `List<T>`) | `Collection::class`, `List::class`, `Set::class`                            | Turns binary PostgreSQL arrays (`PgArray`) into ordinary Kotlin collections.                                    |
| `PrimitiveArrayConverter`                                    | Kotlin Array                           | Primitive arrays (e.g., `IntArray::class`, `CharArray::class`)              | Turns binary PostgreSQL arrays into primitive Kotlin arrays.                                                    |
| `JsonElementConverter`                                       | `JsonElement`                          | `JsonElement::class`, `JsonObject::class`, `JsonArray::class`, `Any::class` | Passes `JSON`/`JSONB` data up as Kotlinx Serialization JSON elements.                                           |
| `RangeResultConverter` <br> `MultiRangeResultConverter`      | `Range<T>`, `MultiRange<T>`            | `Range::class`, `MultiRange::class`                                         | Deserializes PostgreSQL range and multirange types.                                                             |

### ParameterConverters (Writing objects to DB)

These translate your Kotlin objects into a shape the codec layer can serialize.

| Converter Class                                               | Accepted Input Type       | Description                                                                                                           |
|:--------------------------------------------------------------|:--------------------------|:----------------------------------------------------------------------------------------------------------------------|
| `ReflectionCompositeParameterConverter`                       | Kotlin Data Class         | Turns a data class into a logical `PgComposite` structure, ready to pass through the codec layer as a composite type. |
| `CollectionArrayParameterConverter`                           | `Collection<T>`           | Packs a Kotlin collection into structures for database array serialization.                                           |
| `PrimitiveArrayParameterConverter`                            | Kotlin Array              | Packs a standard Kotlin array into structures for database array serialization.                                       |
| `JsonElementParameterConverter`                               | `JsonElement`             | Adapts Kotlinx JSON elements for serialization to PostgreSQL `JSON`/`JSONB`.                                          |
| `RangeParameterConverter` <br> `MultiRangeParameterConverter` | `PgRange`, `PgMultiRange` | Converts Kotlin range wrappers into PostgreSQL range/multirange types.                                                |

### How a converter gets chosen

**Result converters** are indexed by `supportedSourceClass` — the runtime class of the *decoded* value, not the class you asked for. Lookup goes:

1. Converters registered for that exact source class, **newest first**, taking the first whose `canConvert(sourceClass, expectedType, sourceType, context)` returns `true`.
2. Then converters registered under `Any::class`, again newest first — a catch-all slot for converters that key off the PostgreSQL type rather than the decoded class.
3. Then the parent registry (for query-scoped registries, the global one).
4. Then the identity fallback, then `MappingException(NO_CONVERTER_FOUND)`.

**Parameter converters** live in one flat list, newest first, and the first whose `canConvert(sourceClass, expectedOid, context)` returns `true` wins. The default `canConvert` accepts exact class matches and subclasses, so a converter for a sealed base type covers its subclasses.

Because `canConvert` receives both the expected Kotlin type *and* the source `PgType`, a converter can be as narrow as you like — matching only `tribute` composites in the `imperium` schema, for example — and simply decline everything else.

## Registering your own mappings

All of these go through `session.typeManager` and are, again, **global for the database**. Do them once at startup, after any DDL that creates the types involved.

### Enums

```kotlin
enum class LegioStatus { ON_CAMPAIGN, IN_GARRISON, DISBANDED }

session.typeManager.registerEnum<LegioStatus>()
```

One call registers both directions. Defaults:

* **Type name** — the class name converted from `PascalCase` to `snake_case`: `LegioStatus` → `legio_status`. Override with `typeName = "..."`, and pin the schema with `schema = "..."`.
* **Value naming** — `kotlinConvention = PASCAL_CASE`, `pgConvention = SNAKE_CASE_UPPER`, i.e. labels are expected to be uppercase in the database (`'ON_CAMPAIGN'`).

For the common style where PostgreSQL labels are lowercase, state the conventions:

```kotlin
// Kotlin: ON_CAMPAIGN   PostgreSQL: 'on_campaign'
session.typeManager.registerEnum<LegioStatus>(
    pgConvention = CaseConvention.SNAKE_CASE_LOWER,
    kotlinConvention = CaseConvention.SNAKE_CASE_UPPER
)
```

Available conventions are `SNAKE_CASE_UPPER`, `SNAKE_CASE_LOWER`, `PASCAL_CASE` and `CAMEL_CASE`. A database label with no matching Kotlin constant throws during deserialization rather than falling back silently.

### Composites (data classes)

```kotlin
data class Address(val city: String, val street: String)

data class SenatorProfile(
    val fullName: String,                     // -> full_name
    @PgName("cognomen_officiale")
    val cognomen: String,                     // -> cognomen_officiale
    val residence: Address?                   // nested composite, mapped recursively
)

session.typeManager.registerAutoComposite<Address>()
session.typeManager.registerAutoComposite<SenatorProfile>("senator_profile", schema = "imperium")
```

Notes:

* The type name defaults to the class name in `snake_case`, exactly like enums.
* Attribute names default to each property name converted `camelCase` → `snake_case`; `@PgName` overrides an individual property.
* Attributes are matched **by name**, not by position, and the reflection metadata is cached on registration.
* A composite attribute that is missing from the class is simply written as `NULL`; a non-nullable Kotlin property with no matching attribute (or a `NULL` value) raises `MappingException(REQUIRED_ATTRIBUTE_MISSING)` with the offending path.
* Registration is bidirectional: the class can be built from a `PgComposite`, and — because the mapping is also keyed by type name — `row.get<Any>("profile")` resolves back to `SenatorProfile`.
* Nothing is resolved against the catalog at registration time, so the order of `registerAutoComposite` and `reloadTypes()` doesn't matter. The type only has to exist by the time a query uses it.

### Custom converters

When reflection isn't the mapping you want — a legacy column layout, a value class, a JSON payload you want as your own DTO — write the converter explicitly:

```kotlin
class TributeResultConverter : ResultConverter<PgComposite, Tribute> {
    override val supportedSourceClass = PgComposite::class

    override fun canConvert(
        sourceClass: KClass<*>, expectedType: KType, sourceType: PgType, context: DeserializationContext
    ): Boolean = expectedType.classifier == Tribute::class &&
                 sourceType is PgType.Composite && sourceType.name == "tribute"

    override fun convert(
        source: PgComposite, expectedType: KType, sourceType: PgType, context: DeserializationContext
    ): Tribute = Tribute(source.get("amount"), source.get("currency"))
}

class TributeParameterConverter : ParameterConverter<Tribute> {
    override val supportedClass = Tribute::class

    override fun convert(source: Tribute, expectedOid: Int, context: SerializationContext): Any {
        // Known when the value is nested in a composite/array, or was wrapped in PgTyped
        val composite = if (expectedOid.isKnownOid) {
            context.typeManager.containers.createComposite(expectedOid)
        } else {
            context.typeManager.containers.createComposite("tribute")
        }
        composite["amount"] = source.amount
        composite["currency"] = source.currency
        return composite
    }

    // Supplies the type name when there is no expected OID — the usual case at top level
    override fun getDefaultTypeName(sourceClass: KClass<*>, context: SerializationContext) =
        QualifiedName("", "tribute")
}

session.typeManager.registerResultConverter(TributeResultConverter())
session.typeManager.registerParameterConverter(TributeParameterConverter())
```

`ContainerFactory` (`typeManager.containers`) is the clean way to build containers by name or OID: `createComposite`, `createRange`, `createEmptyRange`, `createMultirange`.

### Custom codecs

Reach for a codec only when you need to change how the **bytes** are read or written — a PostGIS type, a domain you want decoded to a value class, or replacing a built-in.

```kotlin
class CircleCodec : TypeCodec<Circle> {
    override val pgTypeName = "circle"
    override val oid: Int? = null              // resolved from the catalog instead
    override val kotlinClass = Circle::class
    override val fromBinary: (ByteArray, Int, Int) -> Circle = { data, offset, len -> decodeCircle(data, offset, len) }
    override val toBinary: (Circle, PgByteWriter) -> Unit = { value, writer -> encodeCircle(value, writer) }
}

session.typeManager.registerCodec(CircleCodec())
```

A codec is bound to OIDs in one of three ways, depending on what it declares:

| Declared                     | Binding behavior                                                                                                                                                                 |
|:-----------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `oid` set                    | Bound directly to that OID. No catalog lookup, works before the catalog is loaded.                                                                                               |
| `oid` null, `pgSchema` set   | The OID is resolved from the catalog at registration time; an unknown name throws `TypeException`.                                                                               |
| `oid` null, `pgSchema` empty | Bound to **every** OID in the catalog whose type name matches, across all schemas. The OID used for outbound parameters is resolved in-flight against the session's search path. |

Set `isDefaultForKotlinType = true` if the codec should also be chosen when the driver only knows the Kotlin class of a parameter and not its target OID. For a sealed class, that also registers all of its sealed subclasses.

Registered codecs are remembered and re-bound on every catalog reload, so a codec registered before its type exists starts working after the next `reloadTypes()`.

### Overriding a built-in

Registration is last-wins in both layers, so overriding the defaults means registering your own on top:

* **Codec** — register a codec with the same OID (e.g. `1700` for `numeric`) to replace the built-in binary handling.
* **Converter** — register a `ResultConverter` that claims the shape you want to intercept; being newest, it is consulted before the built-in one. This is the natural place to turn every `PgInterval` into a `Duration`, or every `jsonb` into your own DTO.

## Practical rules and gotchas

* **Register once, at startup.** Registration is global and permanent for the JVM; doing it per request grows the registries and buys nothing.
* **`reloadTypes()` after runtime DDL** — including `CREATE TABLE`, whose row type is a composite. Without it, new types resolve to nothing.
* **Register before creating the query object.** A query snapshots the codec dictionary when it's constructed, so `createNativeQuery(...)` should come *after* `registerCodec(...)`. Converters are looked up per conversion and don't have this constraint.
* **Same database, different credentials, same registry.** The cache key is host + port + database only.
* **Ambiguous type names need a schema.** If the same type name exists in several schemas and none is on the search path, resolution throws instead of guessing.
* **Empty collections need `PgTyped`.** Erasure leaves nothing in an `emptyList()` for the driver to infer an element type from — the same goes for a list of nothing but nulls.
* **Not every PostgreSQL type has a codec.** `money`, `timetz`, `tsvector`, `tsquery`, `jsonpath` and friends throw `TypeException(MISSING_CODEC)`. Cast them in SQL or write the codec.
* **`MappingException.path` points at the failure.** For nested composites and arrays, read the path before reading the message.
* **Thousands of dynamic database URLs?** Call `GlobalTypeRegistry.removeRegistry(url)` when tearing down a data source. Otherwise, ignore it.

Centralizing everything behind `TypeRegistry`, `ParameterConverterRegistry`, and `ResultConverterRegistry` makes the whole system easy to extend — plugging in PostGIS support or a custom JSON engine is a matter of registering a converter, not rewriting the pipeline.
