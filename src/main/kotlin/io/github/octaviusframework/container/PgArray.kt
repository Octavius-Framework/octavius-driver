package io.github.octaviusframework.container

import io.github.octaviusframework.io.ByteArrayWindow
import io.github.octaviusframework.types.TypeRegistry
import io.github.octaviusframework.exceptions.OctaviusTypeException
import io.github.octaviusframework.exceptions.TypeExceptionMessage

/**
 * Reprezentuje pojedynczy wymiar tablicy w Postgresie.
 */
data class ArrayDimension(
    val size: Int,
    val lowerBound: Int
)


class PgArray internal constructor(
    val arrayOid: UInt,
    val elementOid: UInt,
    val dimensions: List<ArrayDimension>,
    val hasNulls: Boolean,
    val windows: MutableList<ByteArrayWindow?>?,
    val containers: MutableList<PgContainer?>?,
    var values: MutableList<Any?>?,
    @PublishedApi internal val typeRegistry: TypeRegistry
) : PgContainer {

    val totalElements: Int
        get() = windows?.size ?: containers?.size ?: values?.size ?: 0

    operator fun set(index: Int, newValue: Any?) {
        if (newValue is PgContainer) {
            if (containers == null) throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = elementOid.toInt(), details = "Tablica typu OID $elementOid nie przechowuje kontenerów")
            containers[index] = newValue
            windows?.set(index, null)
            values?.let { it[index] = null }
        } else {
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

    inline fun <reified T> getElement(indices: IntArray): T? {
        require(indices.size == dimensions.size) { "Oczekiwano ${dimensions.size} indeksów dla tablicy wielowymiarowej, otrzymano ${indices.size}" }
        val flatIndex = indices.foldIndexed(0) { idx, acc, i -> 
            acc * dimensions[idx].size + i 
        }
        return get<T>(flatIndex)
    }

    @PublishedApi internal val elementSerializer: io.github.octaviusframework.types.TypeSerializer<Any>? by lazy {
        typeRegistry.getSerializerByOid<Any>(elementOid)
    }

    inline fun <reified T> get(index: Int): T? {
        if (values != null && values!![index] != null) return values!![index] as T
        if (containers != null) return containers[index] as? T

        val window = windows!![index] ?: return null
        val serializer = elementSerializer
            ?: throw OctaviusTypeException(TypeExceptionMessage.MISSING_SERIALIZER, oid = elementOid.toInt(), details = "Pobieranie elementu tablicy")
            
        val parsedValue = serializer.fromBinary(window)
        if (parsedValue is T) return parsedValue
        throw OctaviusTypeException(TypeExceptionMessage.CASTING_ERROR, typeName = T::class.simpleName, details = "Otrzymano ${parsedValue::class.simpleName}")
    }

    override fun detach() {
        windows?.forEach { it?.detach() }
        containers?.forEach { it?.detach() }
    }

    /**
     * Konwertuje całą tablicę do płaskiej Listy obiektów docelowego typu.
     * Tutaj następuje faktyczne (leniwe) parsowanie z ByteArray na właściwe typy.
     */
    inline fun <reified T> toList(): List<T?> {
        val count = totalElements
        val result = ArrayList<T?>(count)
        
        val serializer = if (containers == null) elementSerializer else null
        if (containers == null && serializer == null) {
            throw OctaviusTypeException(TypeExceptionMessage.MISSING_SERIALIZER, oid = elementOid.toInt(), details = "Element tablicy")
        }

        for (i in 0 until count) {
            if (values != null && values!![i] != null) {
                result.add(values!![i] as T)
                continue
            }
            if (containers != null) {
                val element = containers[i]
                if (element == null) result.add(null)
                else result.add(element as T)
                continue
            }
            val window = windows!![i]
            if (window == null) {
                result.add(null)
                continue
            }
            val parsedValue = serializer!!.fromBinary(window)
            if (parsedValue is T) {
                result.add(parsedValue)
            } else {
                throw OctaviusTypeException(TypeExceptionMessage.CASTING_ERROR, typeName = T::class.simpleName, details = "Otrzymano ${parsedValue::class.simpleName}")
            }
        }
        return result
    }

    /**
     * Opcjonalnie: metody zoptymalizowane pod JVM do konwersji na prymitywne tablice bez boxingu (autoboxing w Javie psuje wydajność dla dużych kolekcji intów).
     * Zakłada, że elementy nie są nullami (lub można to jakoś inaczej obsłużyć).
     */
    fun toIntArray(): IntArray {
        if (containers != null) throw OctaviusTypeException(TypeExceptionMessage.CASTING_ERROR, details = "Tablica zawiera eager kontener, nie można rzutować na IntArray")

        val serializer = elementSerializer
            ?: throw OctaviusTypeException(TypeExceptionMessage.MISSING_SERIALIZER, oid = elementOid.toInt(), details = "toIntArray")

        val count = totalElements
        val result = IntArray(count)
        for (i in 0 until count) {
            if (values != null && values!![i] != null) {
                result[i] = values!![i] as Int
                continue
            }
            val window = windows!![i]
                ?: throw NullPointerException("Znaleziono wartość NULL podczas rzutowania na IntArray")
            
            result[i] = serializer.fromBinary(window) as Int
        }
        return result
    }
}
