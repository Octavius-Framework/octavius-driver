package io.github.octaviusframework.driver.type

import io.github.octaviusframework.driver.exception.TypeException
import io.github.octaviusframework.driver.exception.TypeExceptionReason
import io.github.octaviusframework.driver.identifier.QualifiedName

/**
 * Wraps a value to explicitly specify the target PostgreSQL type.
 *
 * Useful for handling type ambiguities, e.g., with arrays.
 *
 * @property value Value to embed in the query (avoid using with data classes where this is added automatically!).
 * @property pgType PostgreSQL type name to which the value should be cast.
 * @throws TypeException `NESTED_PGTYPED_NOT_ALLOWED` if [value] is itself a [PgTyped].
 */
data class PgTyped(val value: Any?, val pgType: QualifiedName) {
    init {
        if (value is PgTyped) {
            throw TypeException(TypeExceptionReason.NESTED_PGTYPED_NOT_ALLOWED)
        }
    }
}

/**
 * Wraps a value in PgTyped to explicitly specify the target PostgreSQL type
 * in a type-safe manner.
 *
 * @param pgType One of the built-in types.
 * @return The wrapped value.
 */
fun Any?.withPgType(pgType: PgStandardType): PgTyped =
    PgTyped(this, QualifiedName("", pgType.typeName, isArray = pgType.isArray))

/**
 * Wraps a value in PgTyped with explicit schema and name.
 *
 * @param name Type name (e.g. "my_type").
 * @param schema Schema name (optional). Left empty, the type is resolved through the search path.
 * @param isArray Whether it's an array type (optional).
 * @return The wrapped value.
 */
fun Any?.withPgType(name: String, schema: String = "", isArray: Boolean = false): PgTyped =
    PgTyped(this, QualifiedName(schema, name, isArray))

/**
 * Wraps a value in PgTyped using explicit QualifiedName class.
 *
 * @param pgType The qualified type name.
 * @return The wrapped value.
 */
fun Any?.withPgType(pgType: QualifiedName): PgTyped =
    PgTyped(this, pgType)

