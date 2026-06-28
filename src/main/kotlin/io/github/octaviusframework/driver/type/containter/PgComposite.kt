package io.github.octaviusframework.driver.type.containter

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.io.ByteArrayWindow
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeRegistry

data class ContainerField(
    var rawValue: ByteArrayWindow?,
    var container: PgContainer? = null,
    var value: Any? = null
) {
    fun detach() {
        rawValue?.detach()
        container?.detach()
    }
}

/**
 * Represents a composite structure (e.g. row of a specific type) loaded from the database.
 * Internal values are kept in binary form and lazily cast on retrieval.
 */
class PgComposite(
    val type: PgType.Composite,
    val fields: List<ContainerField>,
    @PublishedApi internal val typeRegistry: TypeRegistry
) : PgContainer {
    override fun detach() {
        fields.forEach { it.detach() }
    }

    /**
     * Returns a list of all attribute names of this composite.
     */
    val attributeNames: List<String>
        get() = type.attributes.keys.toList()

    /**
     * Leniwie rzutuje i zwraca atrybut po indeksie.
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

        val attributeOid = type.attributes.values.toList()[index]
        val serializer = typeRegistry.getCodecByOid<Any>(attributeOid)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_SERIALIZER,
                oid = attributeOid,
                details = "Pobieranie atrybutu kompozytu"
            )

        val parsedValue = serializer.fromBinary(window)
        if (parsedValue is T) {
            return parsedValue
        } else {
            throw OctaviusTypeException(
                TypeExceptionMessage.CASTING_ERROR,
                typeName = T::class.simpleName,
                details = "Otrzymano ${parsedValue::class.simpleName}"
            )
        }
    }

    fun getColumnIndex(columnName: String): Int {
        val index = type.attributes.keys.indexOf(columnName)
        if (index == -1) throw OctaviusTypeException(
            TypeExceptionMessage.ATTRIBUTE_NOT_FOUND,
            details = "Atrybut: $columnName"
        )
        return index
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

    operator fun set(columnName: String, newValue: Any?) {
        set(getColumnIndex(columnName), newValue)
    }

    /**
     * Leniwie rzutuje i zwraca atrybut po jego nazwie.
     */
    inline fun <reified T> get(name: String): T {
        val index = type.attributes.keys.indexOf(name)
        if (index == -1) throw OctaviusTypeException(
            TypeExceptionMessage.ATTRIBUTE_NOT_FOUND,
            details = "Atrybut '$name' w kompozycie '${type.name}'"
        )
        return get<T>(index)
    }
}
