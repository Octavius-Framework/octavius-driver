package io.github.octaviusframework.driver.type.containter

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.io.ByteArrayWindow
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeRegistry
import io.github.octaviusframework.driver.codec.TypeCodec

/**
 * Reprezentuje pojedynczy wymiar tablicy w Postgresie.
 */
data class ArrayDimension(
    val size: Int,
    val lowerBound: Int
)


class PgArray(
    val arrayOid: UInt,
    val elementOid: UInt,
    val dimensions: List<ArrayDimension>,
    var windows: MutableList<ByteArrayWindow?>?,
    var containers: MutableList<PgContainer?>?,
    var values: MutableList<Any?>?,
    @PublishedApi internal val typeRegistry: TypeRegistry
) : PgContainer {

    val totalElements: Int
        get() = windows?.size ?: containers?.size ?: values?.size ?: 0

    operator fun set(index: Int, newValue: Any?) {
        if (newValue is PgArray) {
            throw IllegalArgumentException("Tablica nie może zawierać innej tablicy")
        }

        if (newValue == null) {
            values?.set(index, null)
            containers?.set(index, null)
            windows?.set(index, null)
            return
        }

        if (newValue is PgContainer) {
            if (values != null && values!!.any { it != null }) {
                throw IllegalArgumentException("Tablica zawiera już wartości niebędące kontenerami")
            }
            if (containers == null) {
                val elementType = typeRegistry.types[elementOid]
                if (elementType !is PgType.Composite &&
                    elementType !is PgType.Range &&
                    elementType !is PgType.Multirange
                ) {
                    throw OctaviusTypeException(
                        TypeExceptionMessage.NOT_A_CONTAINER,
                        oid = elementOid,
                        details = "Tablica typu OID $elementOid nie przechowuje kontenerów"
                    )
                }
                containers = MutableList(totalElements) { null }
            }
            containers!![index] = newValue
            windows?.set(index, null)
            values?.set(index, null)
        } else {
            if (containers != null && containers!!.any { it != null }) {
                throw IllegalArgumentException("Tablica zawiera już kontenery")
            }
            if (values == null) {
                values = MutableList(totalElements) { null }
            }
            values!![index] = newValue
            windows?.set(index, null)
            containers?.set(index, null)
        }
    }

    fun setElement(indices: IntArray, newValue: Any?) {
        require(indices.size == dimensions.size) { "Oczekiwano ${dimensions.size} indeksów dla tablicy wielowymiarowej, otrzymano ${indices.size}" }
        val flatIndex = indices.foldIndexed(0) { idx, acc, i ->
            acc * dimensions[idx].size + i
        }
        set(flatIndex, newValue)
    }

    fun setDimension(indices: IntArray, newValues: Iterable<Any?>) {
        val expectedIndices = dimensions.size - 1
        require(indices.size == expectedIndices) { "Oczekiwano $expectedIndices indeksów do określenia wymiaru, otrzymano ${indices.size}" }
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
        require(list.size == lastDimensionSize) { "Oczekiwano $lastDimensionSize wartości dla ostatniego wymiaru, otrzymano ${list.size}" }

        list.forEachIndexed { i, value ->
            set(baseIndex + i, value)
        }
    }

    fun setDimension(indices: IntArray, vararg newValues: Any?) {
        setDimension(indices, newValues.toList())
    }

    fun setAll(newValues: Iterable<Any?>) {
        val list = newValues.toList()
        require(list.size == totalElements) { "Oczekiwano $totalElements elementów, otrzymano ${list.size}" }
        list.forEachIndexed { i, value ->
            set(i, value)
        }
    }

    fun setAll(vararg newValues: Any?) {
        setAll(newValues.toList())
    }

    inline fun <reified T> getElement(indices: IntArray): T? {
        require(indices.size == dimensions.size) { "Oczekiwano ${dimensions.size} indeksów dla tablicy wielowymiarowej, otrzymano ${indices.size}" }
        val flatIndex = indices.foldIndexed(0) { idx, acc, i ->
            acc * dimensions[idx].size + i
        }
        return get<T>(flatIndex)
    }

    @PublishedApi
    internal val elementSerializer: TypeCodec<Any>? by lazy {
        typeRegistry.getCodecByOid(elementOid)
    }

    inline fun <reified T> get(index: Int): T? {
        if (values != null && values!![index] != null) {
            val v = values!![index]
            if (v is T) return v as T
            throw OctaviusTypeException(
                TypeExceptionMessage.CASTING_ERROR,
                typeName = T::class.simpleName,
                details = "Otrzymano ${v!!::class.simpleName}"
            )
        }
        if (containers != null && containers!![index] != null) {
            val c = containers!![index]
            if (c is T) return c as T
            throw OctaviusTypeException(
                TypeExceptionMessage.CASTING_ERROR,
                typeName = T::class.simpleName,
                details = "Otrzymano ${c!!::class.simpleName}"
            )
        }

        val window = windows!![index] ?: return null
        val serializer = elementSerializer
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_SERIALIZER,
                oid = elementOid,
                details = "Pobieranie elementu tablicy"
            )

        val parsedValue = serializer.fromBinary(window)
        if (parsedValue is T) return parsedValue
        throw OctaviusTypeException(
            TypeExceptionMessage.CASTING_ERROR,
            typeName = T::class.simpleName,
            details = "Otrzymano ${parsedValue::class.simpleName}"
        )
    }

    override fun detach() {
        windows?.forEach { it?.detach() }
        containers?.forEach { it?.detach() }
    }
}
