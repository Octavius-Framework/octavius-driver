package io.github.octaviusframework.driver.type

/**
 * Represents standard, built-in PostgreSQL data types.
 * Used for type-safe type specification in the `withPgType` method.
 */
enum class PgStandardType(val typeName: String, val isArray: Boolean = false, val oid: UInt) {
    // --- Simple types ---
    // Fixed-point types
    INT2("int2", false, 21u),
    INT4("int4", false, 23u),
    INT8("int8", false, 20u),

    // Floating-point types
    FLOAT4("float4", false, 700u),
    FLOAT8("float8", false, 701u),
    NUMERIC("numeric", false, 1700u),

    // Text types
    VARCHAR("varchar", false, 1043u),
    BPCHAR("bpchar", false, 1042u),
    TEXT("text", false, 25u),

    // Date and time
    DATE("date", false, 1082u),
    TIMESTAMP("timestamp", false, 1114u),
    TIMESTAMPTZ("timestamptz", false, 1184u),
    TIME("time", false, 1083u),
    INTERVAL("interval", false, 1186u),

    // Json
    JSON("json", false, 114u),
    JSONB("jsonb", false, 3802u),

    // Other
    BOOL("bool", false, 16u),
    UUID("uuid", false, 2950u),
    BYTEA("bytea", false, 17u),
    UNKNOWN("unknown", false, 705u),

    // --- Array types ---
    INT2_ARRAY("int2", true, 1005u),
    INT4_ARRAY("int4", true, 1007u),
    INT8_ARRAY("int8", true, 1016u),
    FLOAT4_ARRAY("float4", true, 1021u),
    FLOAT8_ARRAY("float8", true, 1022u),
    NUMERIC_ARRAY("numeric", true, 1231u),
    VARCHAR_ARRAY("varchar", true, 1015u),
    BPCHAR_ARRAY("bpchar", true, 1014u),
    TEXT_ARRAY("text", true, 1009u),
    DATE_ARRAY("date", true, 1182u),
    TIMESTAMP_ARRAY("timestamp", true, 1115u),
    TIMESTAMPTZ_ARRAY("timestamptz", true, 1185u),
    TIME_ARRAY("time", true, 1183u),
    INTERVAL_ARRAY("interval", true, 1187u),
    JSON_ARRAY("json", true, 199u),
    JSONB_ARRAY("jsonb", true, 3807u),
    BOOL_ARRAY("bool", true, 1000u),
    UUID_ARRAY("uuid", true, 2951u),
    BYTEA_ARRAY("bytea", true, 1001u),
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
