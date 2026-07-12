package io.github.octaviusframework.driver.container

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.registry.TypeRegistry

/**
 * Reprezentuje pojedynczy wymiar tablicy w Postgresie.
 */
data class ArrayDimension(
    val size: Int,
    val lowerBound: Int
)

class PgArray(
    val arrayOid: Int,
    val elementOid: Int,
    val dimensions: List<ArrayDimension>,
    val elements: MutableList<Any?>,
    @PublishedApi internal val typeRegistry: TypeRegistry
) : PgContainer {

    val totalElements: Int
        get() = elements.size

    operator fun set(index: Int, newValue: Any?) {
        if (newValue is PgArray) {
            throw IllegalArgumentException("Array cannot contain another array")
        }
        elements[index] = newValue
    }

    inline fun <reified T> get(index: Int): T {
        val value = elements[index]
        if (value is T) return value
        throw OctaviusTypeException(
            TypeExceptionMessage.CASTING_ERROR,
            typeName = T::class.simpleName,
            details = "Expected ${T::class.simpleName}, got ${if (value != null) value::class.simpleName else "null"}"
        )
    }
}

