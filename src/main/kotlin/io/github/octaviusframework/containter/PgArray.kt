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
    val rawElements: List<ByteArray?>,
    @PublishedApi internal val typeRegistry: TypeRegistry
) {

    /**
     * Całkowity rozmiar tablicy (suma wszystkich elementów we wszystkich wymiarach).
     */
    val totalElements: Int
        get() = rawElements.size

    /**
     * Konwertuje całą tablicę do płaskiej Listy obiektów docelowego typu.
     * Tutaj następuje faktyczne (leniwe) parsowanie z ByteArray na właściwe typy.
     */
    inline fun <reified T> toList(): List<T?> {
        val handler = typeRegistry.getHandlerByOid<Any>(elementOid)
            ?: throw IllegalStateException("Nie znaleziono handlera dla elementu tablicy o OID: $elementOid")

        return rawElements.map { bytes ->
            if (bytes == null) {
                null
            } else {
                val parsedValue = handler.fromBinary(bytes)
                if (parsedValue is T) {
                    parsedValue
                } else {
                    throw IllegalStateException("Błąd rzutowania: Oczekiwano ${T::class.simpleName}, a otrzymano ${parsedValue::class.simpleName}")
                }
            }
        }
    }

    /**
     * Opcjonalnie: metody zoptymalizowane pod JVM do konwersji na prymitywne tablice bez boxingu (autoboxing w Javie psuje wydajność dla dużych kolekcji intów).
     * Zakłada, że elementy nie są nullami (lub można to jakoś inaczej obsłużyć).
     */
    fun toIntArray(): IntArray {
        val handler = typeRegistry.getHandlerByOid<Int>(elementOid)
            ?: throw IllegalStateException("Nie znaleziono handlera dla OID: $elementOid")

        val result = IntArray(rawElements.size)
        for (i in rawElements.indices) {
            val bytes = rawElements[i]
                ?: throw NullPointerException("Znaleziono wartość NULL podczas rzutowania na IntArray")
            result[i] = handler.fromBinary(bytes)
        }
        return result
    }
}