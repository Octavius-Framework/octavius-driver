package io.github.octaviusframework.driver.container

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.registry.TypeRegistry
import io.github.octaviusframework.driver.type.PgType

/**
 * Represents an anonymous record structure (e.g., ROW(...) without a specific registered composite type) loaded from the database.
 *
 * @property type The record type definition.
 * @property fieldOids Array of OIDs corresponding to the fields in this record.
 * @property fields Array containing the values of the fields in this record.
 * @property typeRegistry Registry used for resolving types.
 */
class PgRecord internal constructor(
    val type: PgType.Record,
    val fieldOids: IntArray,
    val fields: Array<Any?>,
    @PublishedApi internal val typeRegistry: TypeRegistry
) : PgContainer {
    inline fun <reified T> get(index: Int): T {
        val value = fields[index]

        if (value is T) {
            return value
        }

        if (value == null) {
            throw OctaviusTypeException(
                TypeExceptionMessage.CASTING_ERROR,
                typeName = T::class.simpleName,
                details = "Expected non-null value for attribute at index $index, got null"
            )
        }

        throw OctaviusTypeException(
            TypeExceptionMessage.CASTING_ERROR,
            typeName = T::class.simpleName,
            details = "Expected ${T::class.simpleName}, got ${value::class.simpleName}"
        )
    }


    fun getAttributeType(index: Int): PgType {
        
        val oid = fieldOids[index]
        return typeRegistry.types[oid]
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.TYPE_NOT_FOUND,
                oid = oid,
                details = "Nie znaleziono typu w rejestrze"
            )
    }

    fun getAttributeOid(index: Int): Int {
        
        return fieldOids[index]
    }
}


