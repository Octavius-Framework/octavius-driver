package io.github.octaviusframework.container

import io.github.octaviusframework.io.ByteArrayWindow
import io.github.octaviusframework.types.TypeRegistry

/**
 * Reprezentuje pojedynczy wymiar tablicy w Postgresie.
 */
data class ArrayDimension(
    val size: Int,
    val lowerBound: Int
)


class PgArray internal constructor(
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
            if (containers == null) throw IllegalStateException("Tablica typu OID $elementOid nie przechowuje kontenerów")
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

    inline fun <reified T> get(index: Int): T? {
        if (values != null && values!![index] != null) return values!![index] as T
        if (containers != null) return containers[index] as? T

        val window = windows!![index] ?: return null
        val handler = typeRegistry.getHandlerByOid<Any>(elementOid)
            ?: throw IllegalStateException("Nie znaleziono handlera dla OID: $elementOid")
            
        val parsedValue = handler.fromBinary(window)
        if (parsedValue is T) return parsedValue
        throw IllegalStateException("Błąd rzutowania: Oczekiwano ${T::class.simpleName}, a otrzymano ${parsedValue::class.simpleName}")
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
        
        val handler = if (containers == null) typeRegistry.getHandlerByOid<Any>(elementOid) else null
        if (containers == null && handler == null) {
            throw IllegalStateException("Nie znaleziono handlera dla elementu tablicy o OID: $elementOid")
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
            val parsedValue = handler!!.fromBinary(window)
            if (parsedValue is T) {
                result.add(parsedValue)
            } else {
                throw IllegalStateException("Błąd rzutowania: Oczekiwano ${T::class.simpleName}, a otrzymano ${parsedValue::class.simpleName}")
            }
        }
        return result
    }

    /**
     * Opcjonalnie: metody zoptymalizowane pod JVM do konwersji na prymitywne tablice bez boxingu (autoboxing w Javie psuje wydajność dla dużych kolekcji intów).
     * Zakłada, że elementy nie są nullami (lub można to jakoś inaczej obsłużyć).
     */
    fun toIntArray(): IntArray {
        if (containers != null) throw IllegalStateException("Tablica zawiera eager kontener, nie można rzutować na IntArray")

        val handler = typeRegistry.getHandlerByOid<Int>(elementOid)
            ?: throw IllegalStateException("Nie znaleziono handlera dla OID: $elementOid")

        val count = totalElements
        val result = IntArray(count)
        for (i in 0 until count) {
            if (values != null && values!![i] != null) {
                result[i] = values!![i] as Int
                continue
            }
            val window = windows!![i]
                ?: throw NullPointerException("Znaleziono wartość NULL podczas rzutowania na IntArray")
            
            result[i] = handler.fromBinary(window)
        }
        return result
    }
}
