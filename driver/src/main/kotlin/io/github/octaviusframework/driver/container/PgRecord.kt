package io.github.octaviusframework.driver.container

import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason
import io.github.octaviusframework.driver.type.PgType

/**
 * Represents an anonymous record structure (e.g., ROW(...) without a specific registered composite type) loaded from the database.
 *
 * @property type The record type definition.
 * @property fieldOids Array of OIDs corresponding to the fields in this record.
 * @property fields Array containing the values of the fields in this record.
 */
class PgRecord internal constructor(
    val type: PgType.Record,
    val fieldOids: IntArray,
    val fields: Array<Any?>
) : PgContainer {
    override val containerOid: Int get() = type.oid

    /**
     * Returns the field at [index], cast to [T].
     *
     * A record is anonymous, so fields are reachable by position only - there are no names to ask by.
     *
     * @param T The expected field type. Declare it nullable to accept a SQL `NULL`.
     * @param index Zero-based index in the order the record's columns were selected.
     * @return The field value.
     * @throws MappingException `COLUMN_NOT_FOUND` if [index] is outside this record's fields,
     *   `CONVERSION_ERROR` if the value is `null` under a non-nullable [T], or is not a [T].
     */
    inline fun <reified T> get(index: Int): T {
        if (index !in fields.indices) throw MappingException(
            MappingExceptionReason.COLUMN_NOT_FOUND,
            details = "Field index out of bounds: $index (record holds ${fields.size} fields)"
        )

        val value = fields[index]

        if (value is T) {
            return value
        }

        if (value == null) {
            throw MappingException(
                MappingExceptionReason.CONVERSION_ERROR,
                details = "Expected non-null value for attribute at index $index, got null"
            )
        }

        throw MappingException(
            MappingExceptionReason.CONVERSION_ERROR,
            details = "Expected ${T::class.simpleName}, got ${value::class.simpleName}"
        )
    }

    /**
     * Returns the PostgreSQL OID of the field at [index].
     *
     * @param index Zero-based index in the order the record's columns were selected.
     * @return The field's type OID.
     * @throws MappingException `COLUMN_NOT_FOUND` if [index] is outside this record's fields.
     */
    fun getAttributeOid(index: Int): Int {
        if (index !in fieldOids.indices) throw MappingException(
            MappingExceptionReason.COLUMN_NOT_FOUND,
            details = "Field index out of bounds: $index (record holds ${fieldOids.size} fields)"
        )
        return fieldOids[index]
    }
}


