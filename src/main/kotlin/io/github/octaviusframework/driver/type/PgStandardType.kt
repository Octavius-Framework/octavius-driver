package io.github.octaviusframework.driver.type

/**
 * Represents standard, built-in PostgreSQL data types.
 * Used for type-safe type specification in the `withPgType` method.
 */
enum class PgStandardType(val typeName: String, val isArray: Boolean = false, val oid: Int) {
    // --- Simple types ---
    // Fixed-point types
    INT2("int2", false, 21),
    INT4("int4", false, 23),
    INT8("int8", false, 20),

    // Floating-point types
    FLOAT4("float4", false, 700),
    FLOAT8("float8", false, 701),
    NUMERIC("numeric", false, 1700),

    // Text types
    VARCHAR("varchar", false, 1043),
    BPCHAR("bpchar", false, 1042),
    TEXT("text", false, 25),

    // Date and time
    DATE("date", false, 1082),
    TIMESTAMP("timestamp", false, 1114),
    TIMESTAMPTZ("timestamptz", false, 1184),
    TIME("time", false, 1083),
    INTERVAL("interval", false, 1186),

    // Json
    JSON("json", false, 114),
    JSONB("jsonb", false, 3802),

    // Other
    BOOL("bool", false, 16),
    UUID("uuid", false, 2950),
    BYTEA("bytea", false, 17),
    UNKNOWN("unknown", false, 705),

    // --- Array types ---
    INT2_ARRAY("int2", true, 1005),
    INT4_ARRAY("int4", true, 1007),
    INT8_ARRAY("int8", true, 1016),
    FLOAT4_ARRAY("float4", true, 1021),
    FLOAT8_ARRAY("float8", true, 1022),
    NUMERIC_ARRAY("numeric", true, 1231),
    VARCHAR_ARRAY("varchar", true, 1015),
    BPCHAR_ARRAY("bpchar", true, 1014),
    TEXT_ARRAY("text", true, 1009),
    DATE_ARRAY("date", true, 1182),
    TIMESTAMP_ARRAY("timestamp", true, 1115),
    TIMESTAMPTZ_ARRAY("timestamptz", true, 1185),
    TIME_ARRAY("time", true, 1183),
    INTERVAL_ARRAY("interval", true, 1187),
    JSON_ARRAY("json", true, 199),
    JSONB_ARRAY("jsonb", true, 3807),
    BOOL_ARRAY("bool", true, 1000),
    UUID_ARRAY("uuid", true, 2951),
    BYTEA_ARRAY("bytea", true, 1001),
}

/**
 * Wraps a value to explicitly specify the target PostgreSQL type.
 *
 * Useful for handling type ambiguities, e.g., with arrays.
 *
 * @param value Value to embed in the query (avoid using with data classes where this is added automatically!).
 * @param pgType PostgreSQL type name to which the value should be cast.
 */
data class PgTyped(val value: Any?, val pgType: QualifiedName)

/**
 * Wraps a value in PgTyped to explicitly specify the target PostgreSQL type
 * in a type-safe manner.
 */
fun Any?.withPgType(pgType: PgStandardType): PgTyped = 
    PgTyped(this, QualifiedName("", pgType.typeName, isArray = pgType.isArray))

/**
 * Wraps a value in PgTyped with explicit schema and name.
 * 
 * @param name Type name (e.g. "my_type").
 * @param schema Schema name (optional).
 * @param isArray Whether it's an array type (optional).
 */
fun Any?.withPgType(name: String, schema: String = "", isArray: Boolean = false): PgTyped = 
    PgTyped(this, QualifiedName(schema, name, isArray))

/**
 * Wraps a value in PgTyped using explicit QualifiedName class.
 */
fun Any?.withPgType(pgType: QualifiedName): PgTyped =
    PgTyped(this, pgType)

