# Type System and Mapping (Octavius Driver)

The type mapping architecture in the `octavius-driver` library is based on an efficient and flexible **2-layer architecture**. It separates low-level binary communication with the database from high-level mapping to Kotlin objects.

## 2-Layer Architecture

1.  **Codecs Layer (`TypeCodec<T>`)**
    *   **Role:** Responsible for direct translation between basic Kotlin types and the native PostgreSQL binary format (represented by byte arrays and `PgByteWriter`).
    *   **Operation:** Codecs operate at a low level, serializing and deserializing data considering PostgreSQL type OIDs.
    *   **Registration:** Codecs are centrally managed by `TypeRegistry`, which associates them based on Kotlin classes or OIDs defined in the database.

2.  **Converters Layer (`ResultConverter<S, T>` / `ParameterConverter<T>`)**
    *   **Role:** Provides a higher level of abstraction that maps intermediate structures decoded by codecs (e.g., `PgComposite`, `PgArray`, `PgRecord`, `OctaviusRow`) to target complex user-defined data structures.
    *   **Operation:** Handles reflective mapping (to classes, e.g., data classes), transformations to collections (`Collection<*>`), maps (`Map<String, Any?>`), and other nested objects.
    *   **Context:** Utilizes `SerializationContext` and `DeserializationContext` interfaces to recursively resolve and convert nested types in complex structures, enabling a smooth transition from the object layer to the binary layer and vice versa.

Thanks to this approach, adding support for a custom specific type for PostgreSQL is limited to writing a relatively small and simple codec, while the entire logic of assigning it to appropriate fields in data classes or collections is still handled automatically by the generic converter layer.

## Basic Codecs

The `io.github.octaviusframework.driver.codec` package provides codecs to translate types between PostgreSQL and Kotlin.

| PostgreSQL Type                                      | Kotlin Type                      | Notes                                            |
|:-----------------------------------------------------|:---------------------------------|:-------------------------------------------------|
| `int2`                                               | `Short`                          |                                                  |
| `int4`                                               | `Int`                            |                                                  |
| `int8`                                               | `Long`                           |                                                  |
| `float4`                                             | `Float`                          |                                                  |
| `float8`                                             | `Double`                         |                                                  |
| `numeric`                                            | `java.math.BigDecimal`           |                                                  |
| `text`, `varchar`, `unknown`, `bpchar` (`character`) | `String`                         |                                                  |
| `json`, `jsonb`                                      | `String`                         | Processed later by JSON converters               |
| `timestamptz`                                        | `kotlin.time.Instant`            | <sup>1</sup>                                     |
| `timestamp`                                          | `kotlinx.datetime.LocalDateTime` | <sup>1</sup>                                     |
| `date`                                               | `kotlinx.datetime.LocalDate`     | <sup>1</sup>                                     |
| `time`                                               | `kotlinx.datetime.LocalTime`     |                                                  |
| `interval`                                           | `PgInterval`                     |                                                  |
| `bool`                                               | `Boolean`                        |                                                  |
| `bytea`                                              | `ByteArray`                      |                                                  |
| `uuid`                                               | `kotlin.uuid.Uuid`               |                                                  |
| `void`                                               | `Unit`                           | Return type of void functions (e.g. `pg_notify`) |
| `oid`, `name`, `"char"`                              | `Int`, `String`, `String`        | Internal PostgreSQL types                        |
| `array`                                              | `PgArray`                        | Evaluated at runtime                             |
| `composite`, `record`                                | `PgComposite`, `PgRecord`        | Evaluated at runtime                             |
| `enum`                                               | `String`                         | Evaluated at runtime                             |
| `domain`                                             | *(Base type)*                    | Delegates to the codec of the underlying type    |

### Infinity Values for Date/Time

<sup>1</sup> **PostgreSQL special values** (`infinity`, `-infinity`) are fully supported for date and timestamp types using provided constants:

| PostgreSQL Type | Special Values          | Kotlin Constants                                             |
|-----------------|-------------------------|--------------------------------------------------------------|
| `date`          | `infinity`, `-infinity` | `LocalDate.DISTANT_FUTURE`, `LocalDate.DISTANT_PAST`         |
| `timestamp`     | `infinity`, `-infinity` | `LocalDateTime.DISTANT_FUTURE`, `LocalDateTime.DISTANT_PAST` |
| `timestamptz`   | `infinity`, `-infinity` | `Instant.DISTANT_FUTURE`, `Instant.DISTANT_PAST`             |

### PgInterval

By default, the PostgreSQL `interval` type is mapped to the internal `PgInterval` class. The driver does not provide built-in converters to automatically map this type to standard Kotlin types like `kotlin.time.Duration` or `kotlinx.datetime.DateTimePeriod`. 

This is an intentional design choice because there is no single perfect equivalent in the Kotlin standard library:
*   **Database Calculations:** It's generally best practice to perform interval math directly in the database where timezones and variable-length dates are fully supported.
*   **`Duration` limitations:** Developers usually want to extract intervals as a `Duration`. However, `Duration` is based on absolute time and cannot accurately represent variable-length calendar units (days and months). An approximate conversion (which assumes 1 month = 30 days, 1 day = 24 hours) might be inaccurate.
*   **`DateTimePeriod` limitations:** While `DateTimePeriod` is exact and supports months/days, it is often inconvenient to work with or perform calculations on.

Therefore, when extracting an interval, you receive a `PgInterval` which strictly preserves the raw DB representation (months, days, and microseconds). `PgInterval` provides explicit extension functions like `toDurationApproximate()`, `toDurationExact()`, and `toDateTimePeriod()` allowing you to decide how it should be converted.

If your application has specific requirements (e.g., you always want approximate `Duration` extraction), you can easily write a custom `ResultConverter` and register it to automatically intercept and convert `PgInterval` values to your desired type across the entire application.

### PgTyped

`PgTyped` is a wrapper class that allows you to explicitly specify the target PostgreSQL type for a given parameter value. This is extremely useful for handling type ambiguities, such as when sending an empty collection and the database needs to know the exact array type (e.g., `int4[]` vs `text[]`).

You can wrap any value using the `.withPgType(...)` extension functions:
* `value.withPgType(PgStandardType.INT4_ARRAY)`
* `value.withPgType("my_custom_type")`

## Basic Converters

Converters (in the `io.github.octaviusframework.driver.converter` package) are divided into those responsible for deserializing query results (`ResultConverter`) and those preparing query parameters (`ParameterConverter`).

### ResultConverters (Reading from DB to objects)

These converters are used when you extract data from a row (e.g., `row.get(TargetClass::class)`). They dictate what Kotlin class is returned based on the requested target class.

| Converter Class                                              | Returns (Output Type)                  | When Used (Target Class in `get`)                                           | Description                                                                                                                         |
|:-------------------------------------------------------------|:---------------------------------------|:----------------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------|
| `ReflectionRowConverter` <br> `ReflectionCompositeConverter` | Kotlin Data Class                      | Data classes (e.g., `MyUser::class`)                                        | Maps result rows (`OctaviusRow`) and complex DB types (`PgComposite`) directly to Kotlin data classes based on flexible reflection. |
| `MapRowConverter` <br> `MapCompositeConverter`               | `Map<String, Any?>`                    | `Map::class`                                                                | Decodes data directly to a universal dictionary. Very useful when the database data schema is not fully known.                      |
| `MapRecordConverter`                                         | `Map<String, Any?>`                    | `Map::class`, `Any::class`                                                  | Handles decoding anonymous PostgreSQL `record` types into a dictionary.                                                             |
| `CollectionArrayConverter`                                   | `Collection<T>` <br> (e.g., `List<T>`) | `Collection::class`, `List::class`, `Set::class`                            | Processes binary PostgreSQL arrays (`PgArray`) into flexible Kotlin collections.                                                    |
| `PrimitiveArrayConverter`                                    | Kotlin Array                           | Primitive Arrays (e.g., `IntArray::class`, `CharArray::class`)              | Processes binary PostgreSQL arrays into primitive Kotlin arrays.                                                                    |
| `JsonElementConverter`                                       | `JsonElement`                          | `JsonElement::class`, `JsonObject::class`, `JsonArray::class`, `Any::class` | Handles `JSON` and `JSONB` data types and passes them upwards as Kotlinx Serialization JSON elements.                               |
| `RangeResultConverter` <br> `MultiRangeResultConverter`      | `Range<T>`, `MultiRange<T>`            | `Range::class`, `MultiRange::class`                                         | Deserializes PostgreSQL range and multirange types.                                                                                 |

### ParameterConverters (Writing objects to DB)

These converters are used to translate Kotlin objects into formats that codecs can serialize to the database.

| Converter Class                                               | Accepted Input Type             | Description                                                                                                                         |
|:--------------------------------------------------------------|:--------------------------------|:------------------------------------------------------------------------------------------------------------------------------------|
| `ReflectionCompositeParameterConverter`                       | Kotlin Data Class               | Translates user data classes into logical structures (`PgComposite`) ready to be pushed through the codec layer as composite types. |
| `CollectionArrayParameterConverter`                           | `Collection<T>`                 | Packs Kotlin collections into structures for database array serialization.                                                          |
| `PrimitiveArrayParameterConverter`                            | Kotlin Array                    | Packs standard Kotlin arrays into structures for database array serialization.                                                      |
| `JsonElementParameterConverter`                               | `JsonElement`                   | Adapts Kotlinx JSON elements for serialization to PostgreSQL `JSON`/`JSONB` types.                                                  |
| `RangeParameterConverter` <br> `MultiRangeParameterConverter` | `PgRange<T>`, `PgMultiRange<T>` | Converts Kotlin range wrappers into PostgreSQL range/multirange types.                                                              |

The designed architecture, through centralization of registries (`TypeRegistry`, `ParameterConverterRegistry`, `ResultConverterRegistry`), allows for extremely easy extensibility and injection of custom dedicated behaviors (e.g., adding PostGIS support or custom JSON engines).
