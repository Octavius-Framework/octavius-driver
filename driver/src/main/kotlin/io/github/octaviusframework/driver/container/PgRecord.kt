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

    inline fun <reified T> get(index: Int): T {
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
            details = "Expected ${T::class.simpleName}, got ${if (value != null) value::class.simpleName else "null"}"
        )
    }

    fun getAttributeOid(index: Int): Int {
        
        return fieldOids[index]
    }
}


