package io.github.octaviusframework.driver.container

import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionMessage
import io.github.octaviusframework.driver.registry.TypeRegistry

/**
 * Represents a single dimension of a PostgreSQL array.
 *
 * @property size The number of elements in this dimension.
 * @property lowerBound The starting index of this dimension (usually 1 in PostgreSQL).
 */
data class ArrayDimension(
    val size: Int,
    val lowerBound: Int
)

/**
 * Represents a PostgreSQL array type.
 *
 * @property arrayOid OID of the array type.
 * @property elementOid OID of the elements within the array.
 * @property dimensions List of dimensions for the array.
 * @property elements Flat list of elements contained in the array.
 */
class PgArray(
    val arrayOid: Int,
    val elementOid: Int,
    val dimensions: List<ArrayDimension>,
    val elements: MutableList<Any?>
) : PgContainer {
    override val containerOid: Int get() = arrayOid
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
        throw MappingException(
            MappingExceptionMessage.CASTING_ERROR,
            details = "Expected ${T::class.simpleName}, got ${if (value != null) value::class.simpleName else "null"}"
        )
    }
}

