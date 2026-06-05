package io.github.octaviusframework.containter

import io.github.octaviusframework.types.PgType
import io.github.octaviusframework.types.TypeRegistry

/**
 * Reprezentuje strukturę kompozytu (np. wiersz konkretnego typu) załadowaną z bazy danych.
 * Wartości wewnętrzne są trzymane w formie binarnej i leniwie rzutowane przy pobieraniu.
 */
class PgComposite internal constructor(
    val type: PgType.Composite,
    val rawAttributes: List<Any?>,
    @PublishedApi internal val typeRegistry: TypeRegistry
) {
    /**
     * Zwraca listę nazw wszystkich atrybutów tego kompozytu.
     */
    val attributeNames: List<String>
        get() = type.attributes.keys.toList()

    /**
     * Leniwie rzutuje i zwraca atrybut po indeksie.
     */
    inline fun <reified T> get(index: Int): T? {
        val element = rawAttributes[index] ?: return null
        if (element is T) return element

        val bytes = element as? io.github.octaviusframework.io.ByteArrayWindow
            ?: throw IllegalStateException("Oczekiwano PgBufferWindow, otrzymano ${element::class.simpleName}")

        val oid = type.attributes.values.elementAt(index)
        val handler = typeRegistry.getHandlerByOid<Any>(oid)
            ?: throw IllegalStateException("Nie znaleziono handlera dla OID: $oid")
        
        val parsed = handler.fromBinary(bytes)
        if (parsed is T) {
            return parsed
        } else {
            throw IllegalStateException("Błąd rzutowania atrybutu o indeksie $index: Oczekiwano ${T::class.simpleName}, a otrzymano ${parsed::class.simpleName}")
        }
    }

    /**
     * Leniwie rzutuje i zwraca atrybut po jego nazwie.
     */
    inline fun <reified T> get(name: String): T? {
        val index = type.attributes.keys.indexOf(name)
        if (index == -1) throw IllegalArgumentException("Atrybut o nazwie '$name' nie istnieje w kompozycie '${type.name}'")
        return get<T>(index)
    }
}