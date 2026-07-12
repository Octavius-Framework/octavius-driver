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

### PgTyped


## Basic Converters

Converters (in the `io.github.octaviusframework.driver.converter` package) are divided into those responsible for deserializing query results (`ResultConverter`) and those preparing query parameters (`ParameterConverter`).

*   **ResultConverters (Reading from DB to objects):**
    *   **`ReflectionRowConverter` / `ReflectionCompositeConverter`:** Powerful tools mapping result rows (`OctaviusRow`) and complex DB types (`PgComposite`) directly to Kotlin data classes, based on flexible reflection.
    *   **`MapRowConverter` / `MapCompositeConverter`:** Decode data directly to a universal `Map<String, Any?>` dictionary. Very useful tools when the data schema in the database is not 100% known to the application.
    *   **Array:** `PrimitiveArrayConverter` and `CollectionArrayConverter` processing binary arrays from PostgreSQL (as `PgArray`) deserialized by codecs into flexible collections and arrays available in Kotlin.
    *   **JSON:** `JsonElementConverter` handling `JSON` and `JSONB` data types and passing them upwards in an easy format.

*   **ParameterConverters (Writing objects to DB):**
    *   **`ReflectionCompositeParameterConverter`:** Translates complex and sometimes nested user classes into logical structures (`PgComposite`) ready to be immediately pushed through the codec layer as composite types.
    *   **Array:** `CollectionArrayParameterConverter`, `PrimitiveArrayParameterConverter` with a similar purpose of packing Kotlin lists and standard arrays into structures for database serialization.
    *   **JSON:** `JsonElementParameterConverter` adapting and converting JSON data tree elements.

The designed architecture, through centralization of registries (`TypeRegistry`, `ParameterConverterRegistry`, `ResultConverterRegistry`), allows for extremely easy extensibility and injection of custom dedicated behaviors (e.g., in case of wanting to add support for geographical libraries in the form of a PostGIS plugin or integration of custom engines working with the JSON system).
