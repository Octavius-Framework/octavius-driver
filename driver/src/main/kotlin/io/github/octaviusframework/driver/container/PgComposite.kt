package io.github.octaviusframework.driver.container

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.registry.TypeRegistry
import io.github.octaviusframework.driver.type.PgType

/**
 * Represents a composite structure (e.g., row of a specific type) loaded from the database.
 *
 * @property type The composite type definition.
 * @property fields Array containing the values of the fields in this composite structure.
 * @property typeRegistry Registry used for resolving types.
 */
class PgComposite internal constructor(
    val type: PgType.Composite,
    val fields: Array<Any?>,
    @PublishedApi internal val typeRegistry: TypeRegistry
) : PgContainer {
    val attributeNames: List<String>
        get() = type.attributeNames

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

    fun getColumnIndex(columnName: String): Int {
        val index = type.nameToIndex[columnName] ?: -1
        if (index == -1) throw OctaviusTypeException(
            TypeExceptionMessage.ATTRIBUTE_NOT_FOUND,
            details = "Atrybut: $columnName"
        )
        return index
    }

    operator fun set(index: Int, newValue: Any?) {
        fields[index] = newValue
    }

    operator fun set(columnName: String, newValue: Any?) {
        set(getColumnIndex(columnName), newValue)
    }

    inline fun <reified T> get(name: String): T {
        val index = type.nameToIndex[name] ?: -1
        if (index == -1) throw OctaviusTypeException(
            TypeExceptionMessage.ATTRIBUTE_NOT_FOUND,
            details = "Atrybut '$name' w kompozycie '${type.name}'"
        )
        return get<T>(index)
    }

    fun getAttributeType(index: Int): PgType {
        val oid = type.attributeOids[index]
        return typeRegistry.types[oid]
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.TYPE_NOT_FOUND,
                oid = oid,
                details = "Nie znaleziono typu w rejestrze"
            )
    }

    fun getAttributeType(name: String): PgType {
        return getAttributeType(getColumnIndex(name))
    }

    fun getAttributeOid(index: Int): Int {
        return type.attributeOids[index]
    }

    fun getAttributeOid(name: String): Int {
        return getAttributeOid(getColumnIndex(name))
    }
}

