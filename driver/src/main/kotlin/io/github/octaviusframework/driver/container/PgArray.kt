package io.github.octaviusframework.driver.container

import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason

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
 * @property dimensions List of dimensions for the array. A one-dimensional array has one entry;
 *   PostgreSQL arrays are rectangular, so a multi-dimensional one describes each axis here.
 * @property elements Flat list of elements contained in the array. Multi-dimensional arrays are stored
 *   flattened in row-major order, so [dimensions] is what gives the flat list its shape.
 */
class PgArray internal constructor(
    val arrayOid: Int,
    val elementOid: Int,
    val dimensions: List<ArrayDimension>,
    val elements: List<Any?>
) : PgContainer {
    override val containerOid: Int get() = arrayOid

    /** The number of elements across every dimension, i.e. the size of the flat [elements] list. */
    val totalElements: Int
        get() = elements.size

    /**
     * Returns the element at [index] in the flat element list, cast to [T].
     *
     * Also written `array[0]`, where the expected type is what fixes [T] - `val id: Int = array[0]`.
     *
     * @param T The expected element type. Declare it nullable to accept a SQL `NULL` element.
     * @param index Zero-based index into [elements], regardless of the array's PostgreSQL lower bound.
     * @return The element.
     * @throws MappingException `COLUMN_NOT_FOUND` if [index] is outside [elements], `CONVERSION_ERROR`
     *   if the element is not a [T].
     */
    inline operator fun <reified T> get(index: Int): T {
        if (index !in elements.indices) throw MappingException(
            MappingExceptionReason.COLUMN_NOT_FOUND,
            details = "Element index out of bounds: $index (array holds ${elements.size} elements)"
        )
        val value = elements[index]
        if (value is T) return value
        throw conversionErrorAt(
            "[$index]",
            "Expected ${T::class.simpleName}, got ${if (value != null) value::class.simpleName else "null"}"
        )
    }
}

