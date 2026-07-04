package io.github.octaviusframework.driver.type.container

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeRegistry

/**
 * Represents a record structure (e.g. ROW(...) without a specific registered composite type) loaded from the database.
 * Internal values are kept in binary form and lazily cast on retrieval.
 */
class PgRecord(
    val type: PgType.Record,
    val fieldOids: List<UInt>,
    val fields: List<ContainerField>,
    @PublishedApi internal val typeRegistry: TypeRegistry
) : PgContainer {
    override fun detach() {
        fields.forEach { it.detach() }
    }

    /**
     * Leniwie rzutuje i zwraca atrybut po indeksem.
     */
    inline fun <reified T> get(index: Int): T {
        val field = fields[index]
        if (field.value != null) {
            if (field.value is T) return field.value as T
            throw OctaviusTypeException(
                TypeExceptionMessage.CASTING_ERROR,
                typeName = T::class.simpleName,
                details = "Otrzymano ${field.value!!::class.simpleName}"
            )
        }
        if (field.container != null) {
            if (field.container is T) return field.container as T
            throw OctaviusTypeException(
                TypeExceptionMessage.CASTING_ERROR,
                typeName = T::class.simpleName,
                details = "Otrzymano ${field.container!!::class.simpleName}"
            )
        }

        val window = field.rawValue
        if (window == null) {
            if (null is T) {
                return null as T
            } else {
                throw OctaviusTypeException(
                    TypeExceptionMessage.CASTING_ERROR,
                    typeName = T::class.simpleName,
                    details = "Expected non-null value for attribute at index $index, got null"
                )
            }
        }

        val attributeOid = fieldOids[index]
        val codec = typeRegistry.getCodecByOid<Any>(attributeOid)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_CODEC,
                oid = attributeOid,
                details = "Pobieranie pola rekordu"
            )

        val parsedValue = codec.fromBinary(window)

        if (parsedValue is PgContainer) {
            field.container = parsedValue
            field.rawValue = null
        } else {
            field.value = parsedValue
            field.rawValue = null
        }

        if (parsedValue is T) {
            return parsedValue
        } else {
            throw OctaviusTypeException(
                TypeExceptionMessage.CASTING_ERROR,
                typeName = T::class.simpleName,
                details = "Otrzymano ${if (parsedValue != null) parsedValue::class.simpleName else "null"}"
            )
        }
    }

    operator fun set(index: Int, newValue: Any?) {
        val field = fields[index]
        if (newValue is PgContainer) {
            field.container = newValue
            field.value = null
            field.rawValue = null
        } else {
            field.value = newValue
            field.container = null
            field.rawValue = null
        }
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

    fun getAttributeOid(index: Int): UInt {
        return fieldOids[index]
    }
}
