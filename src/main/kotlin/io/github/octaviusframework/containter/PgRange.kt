package io.github.octaviusframework.containter

import io.github.octaviusframework.types.TypeRegistry

/**
 * Reprezentuje zakres w bazie PostgreSQL (np. int4range, tsrange).
 * Wartości brzegowe przechowywane są natywnie, parsowanie zlecane jest leniwie.
 */
class PgRange internal constructor(
    val elementOid: UInt,
    val flags: Byte,
    val rawLowerBound: Any?,
    val rawUpperBound: Any?,
    @PublishedApi internal val typeRegistry: TypeRegistry
) {
    val isEmpty: Boolean get() = (flags.toInt() and 0x01) != 0
    val isLowerInclusive: Boolean get() = (flags.toInt() and 0x02) != 0
    val isUpperInclusive: Boolean get() = (flags.toInt() and 0x04) != 0
    val isLowerInfinite: Boolean get() = (flags.toInt() and 0x08) != 0
    val isUpperInfinite: Boolean get() = (flags.toInt() and 0x10) != 0
    val isLowerNull: Boolean get() = (flags.toInt() and 0x20) != 0
    val isUpperNull: Boolean get() = (flags.toInt() and 0x40) != 0

    /**
     * Leniwie rzutuje i zwraca dolną granicę zakresu.
     * Zwraca null, jeśli granicy brak (nieskończoność), jest jawnie null, lub zbiór jest pusty.
     */
    inline fun <reified T> lowerBound(): T? {
        if (isEmpty || isLowerInfinite || isLowerNull) return null
        return parseBound(rawLowerBound)
    }

    /**
     * Leniwie rzutuje i zwraca górną granicę zakresu.
     * Zwraca null, jeśli granicy brak (nieskończoność), jest jawnie null, lub zbiór jest pusty.
     */
    inline fun <reified T> upperBound(): T? {
        if (isEmpty || isUpperInfinite || isUpperNull) return null
        return parseBound(rawUpperBound)
    }

    @PublishedApi
    internal inline fun <reified T> parseBound(element: Any?): T? {
        if (element == null) return null
        if (element is T) return element

        val bytes = element as? io.github.octaviusframework.io.ByteArrayWindow
            ?: throw IllegalStateException("Oczekiwano PgBufferWindow, otrzymano ${element::class.simpleName}")

        val handler = typeRegistry.getHandlerByOid<Any>(elementOid)
            ?: throw IllegalStateException("Nie znaleziono handlera dla elementów zakresu o OID: $elementOid")
            
        val parsed = handler.fromBinary(bytes)
        if (parsed is T) {
            return parsed
        } else {
            throw IllegalStateException("Błąd rzutowania krawędzi zakresu: Oczekiwano ${T::class.simpleName}, a otrzymano ${parsed::class.simpleName}")
        }
    }
}