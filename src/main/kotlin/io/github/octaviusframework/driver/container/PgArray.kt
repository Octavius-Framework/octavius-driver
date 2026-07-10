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

    fun setElement(indices: IntArray, newValue: Any?) {
        require(indices.size == dimensions.size) { "Expected ${dimensions.size} indices for multidimensional array, got ${indices.size}" }
        val flatIndex = indices.foldIndexed(0) { idx, acc, i ->
            acc * dimensions[idx].size + i
        }
        set(flatIndex, newValue)
    }

    fun setDimension(indices: IntArray, newValues: Iterable<Any?>) {
        val expectedIndices = dimensions.size - 1
        require(indices.size == expectedIndices) { "Expected $expectedIndices indices to specify dimension, got ${indices.size}" }
        val lastDimensionSize = dimensions.last().size

        var baseIndex = 0
        if (expectedIndices > 0) {
            for (i in indices.indices) {
                var multiplier = 1
                for (j in i + 1 until dimensions.size) {
                    multiplier *= dimensions[j].size
                }
                baseIndex += indices[i] * multiplier
            }
        }

        val list = newValues.toList()
        require(list.size == lastDimensionSize) { "Expected $lastDimensionSize values for last dimension, got ${list.size}" }

        list.forEachIndexed { i, value ->
            set(baseIndex + i, value)
        }
    }

    fun setDimension(indices: IntArray, vararg newValues: Any?) {
        setDimension(indices, newValues.toList())
    }

    fun setAll(newValues: Iterable<Any?>) {
        val list = newValues.toList()
        require(list.size == totalElements) { "Expected $totalElements elements, got ${list.size}" }
        list.forEachIndexed { i, value ->
            set(i, value)
        }
    }

    fun setAll(vararg newValues: Any?) {
        setAll(newValues.toList())
    }

    inline fun <reified T> getElement(indices: IntArray): T {
        require(indices.size == dimensions.size) { "Expected ${dimensions.size} indices for multidimensional array, got ${indices.size}" }
        val flatIndex = indices.foldIndexed(0) { idx, acc, i ->
            acc * dimensions[idx].size + i
        }
        return get<T>(flatIndex)
    }

    inline fun <reified T> get(index: Int): T {
        val value = elements[index]
        if (value is T) return value
        if (value == null && null is T) return null as T
        throw OctaviusTypeException(
            TypeExceptionMessage.CASTING_ERROR,
            typeName = T::class.simpleName,
            details = "Expected ${T::class.simpleName}, got ${if (value != null) value::class.simpleName else "null"}"
        )
    }
}

