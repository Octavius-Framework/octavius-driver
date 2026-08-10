# Type System and Mapping

Type mapping in `octavius-driver` rests on a flexible **2-layer architecture** that keeps low-level binary wire communication cleanly separated from high-level mapping onto Kotlin objects.

## 2-Layer Architecture

1. **Codecs Layer (`TypeCodec<T>`)**
    * **Role:** the direct translation between basic Kotlin types and PostgreSQL's native binary format (`ByteArray` and `PgByteWriter`).
    * **Operation:** codecs work at a low level, serializing and deserializing with full awareness of PostgreSQL type OIDs.
    * **Registration:** centrally managed by `TypeRegistry`, which associates codecs by Kotlin class or by the OID defined in the database.

2. **Converters Layer (`ResultConverter<S, T>` / `ParameterConverter<T>`)**
    * **Role:** a higher level of abstraction, mapping the intermediate structures codecs decode (`PgComposite`, `PgArray`, `PgRecord`, `Row`) onto whatever complex, user-defined structures you actually want.
    * **Operation:** handles reflective mapping onto classes (data classes), transformation into collections (`Collection<*>`), maps (`Map<String, Any?>`), and nested objects.
    * **Context:** `SerializationContext` and `DeserializationContext` recursively resolve and convert nested types within complex structures, bridging the object layer and the binary layer smoothly in both directions.

Thanks to this split, adding support for a specific custom PostgreSQL type is usually just writing a small, focused codec — the reflective work of wiring it into data classes and collections is handled automatically by the generic converter layer above it.

## Basic Codecs

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

`PgTyped` wraps a value so you can explicitly declare the PostgreSQL type it should be sent as. This matters most when there's genuine ambiguity — sending an empty collection, for instance, where the database has no way to infer whether you mean `int4[]` or `text[]`.

Wrap any value with the `.withPgType(...)` extension functions:
* `value.withPgType(PgStandardType.INT4_ARRAY)`
* `value.withPgType("legio_status")` — a custom enum type, say, for a legion's current campaign status

## Basic Converters

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

Centralizing everything behind `TypeRegistry`, `ParameterConverterRegistry`, and `ResultConverterRegistry` makes the whole system easy to extend — plugging in PostGIS support or a custom JSON engine is a matter of registering a converter, not rewriting the pipeline.
