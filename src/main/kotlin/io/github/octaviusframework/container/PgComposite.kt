package io.github.octaviusframework.container

import io.github.octaviusframework.io.ByteArrayWindow
import io.github.octaviusframework.types.PgType
import io.github.octaviusframework.types.TypeRegistry

data class ContainerField(
    var rawValue: ByteArrayWindow?,
    var container: PgContainer? = null,
    var value: Any? = null
) {
    fun detach() {
        rawValue?.detach()
        container?.detach()
    }
}

/**
 * Reprezentuje strukturę kompozytu (np. wiersz konkretnego typu) załadowaną z bazy danych.
 * Wartości wewnętrzne są trzymane w formie binarnej i leniwie rzutowane przy pobieraniu.
 */
class PgComposite internal constructor(
    val type: PgType.Composite,
    val fields: List<ContainerField>,
    @PublishedApi internal val typeRegistry: TypeRegistry
) : PgContainer {
    override fun detach() {
        fields.forEach { it.detach() }
    }
    /**
     * Zwraca listę nazw wszystkich atrybutów tego kompozytu.
     */
    val attributeNames: List<String>
        get() = type.attributes.keys.toList()

    /**
     * Leniwie rzutuje i zwraca atrybut po indeksie.
     */
    inline fun <reified T> get(index: Int): T? {
        val field = fields[index]
        if (field.value != null && field.value is T) return field.value as T
        if (field.container != null && field.container is T) return field.container as T

        val window = field.rawValue ?: return null

        val oid = type.attributes.values.elementAt(index)
        val handler = typeRegistry.getHandlerByOid<Any>(oid)
            ?: throw IllegalStateException("Nie znaleziono handlera dla OID: $oid")
        
        val parsed = handler.fromBinary(window)
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
