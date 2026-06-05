package io.github.octaviusframework.containter

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
    val windows: List<io.github.octaviusframework.io.ByteArrayWindow?>?,
    val eagerContainers: List<Any?>?,
    @PublishedApi internal val typeRegistry: TypeRegistry
) {

    val totalElements: Int
        get() = windows?.size ?: eagerContainers?.size ?: 0

    /**
     * Konwertuje całą tablicę do płaskiej Listy obiektów docelowego typu.
     * Tutaj następuje faktyczne (leniwe) parsowanie z ByteArray na właściwe typy.
     */
    inline fun <reified T> toList(): List<T?> {
        if (eagerContainers != null) {
            return eagerContainers.map { element ->
                if (element == null) null else element as T
            }
        }

        val handler = typeRegistry.getHandlerByOid<Any>(elementOid)
            ?: throw IllegalStateException("Nie znaleziono handlera dla elementu tablicy o OID: $elementOid")

        return windows!!.map { window ->
            if (window == null) return@map null
            val parsedValue = handler.fromBinary(window)
            if (parsedValue is T) {
                parsedValue
            } else {
                throw IllegalStateException("Błąd rzutowania: Oczekiwano ${T::class.simpleName}, a otrzymano ${parsedValue::class.simpleName}")
            }
        }
    }

    /**
     * Opcjonalnie: metody zoptymalizowane pod JVM do konwersji na prymitywne tablice bez boxingu (autoboxing w Javie psuje wydajność dla dużych kolekcji intów).
     * Zakłada, że elementy nie są nullami (lub można to jakoś inaczej obsłużyć).
     */
    fun toIntArray(): IntArray {
        if (windows == null) throw IllegalStateException("Tablica zawiera eager kontener, nie można rzutować na IntArray")

        val handler = typeRegistry.getHandlerByOid<Int>(elementOid)
            ?: throw IllegalStateException("Nie znaleziono handlera dla OID: $elementOid")

        val result = IntArray(windows.size)
        for (i in windows.indices) {
            val window = windows[i]
                ?: throw NullPointerException("Znaleziono wartość NULL podczas rzutowania na IntArray")
            
            result[i] = handler.fromBinary(window)
        }
        return result
    }
}
