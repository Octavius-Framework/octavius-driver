package io.github.octaviusframework.query

import io.github.octaviusframework.network.messages.RowDescriptionMessage.FieldDescription
import io.github.octaviusframework.types.TypeRegistry
import io.github.octaviusframework.container.*
import io.github.octaviusframework.io.ByteArrayWindow
import io.github.octaviusframework.types.PgType

import io.github.octaviusframework.container.PgContainer

data class Field(
    val descriptor: FieldDescription,
    var rawValue: ByteArrayWindow?,
    var container: PgContainer? = null,
    var value: Any? = null
) {
    fun detach() {
        rawValue?.detach()
        container?.detach()
    }
}

interface Row {
    val fields: List<Field>
    val columnNames: List<String>
    val typeRegistry: TypeRegistry

    fun getColumnIndex(columnName: String): Int
    fun detach()
}

inline fun <reified T> Row.get(columnName: String): T? {
    val index = getColumnIndex(columnName)
    return get<T>(index)
}

inline fun <reified T> Row.get(index: Int): T? {
    val field = fields.getOrNull(index) ?: return null
    if (field.value != null && field.value is T) {
        return field.value as T
    }
    if (field.container != null && field.container is T) {
        return field.container as T
    }

    val window = field.rawValue ?: return null
    val oid = field.descriptor.dataTypeOid

    val handler = typeRegistry.getHandlerByOid<Any>(oid)
    if (handler != null) {
        val parsed = handler.fromBinary(window)
        if (parsed is T) {
            return parsed
        } else {
            throw IllegalStateException("Błąd rzutowania na indeksie $index: Oczekiwano ${T::class.simpleName}, a otrzymano ${parsed::class.simpleName}")
        }
    }
    
    if (String::class == T::class) return String(window.data, window.offset, window.length, Charsets.UTF_8) as T
    throw IllegalStateException("Brak handlera dla OID: $oid oraz typu ${T::class.simpleName}")
}

class OctaviusRow(
    columns: List<ByteArrayWindow?>,
    descriptors: List<FieldDescription>,
    override val typeRegistry: TypeRegistry
) : Row {

    override val fields: List<Field> = descriptors.zip(columns) { desc, window ->
        var container: PgContainer? = null
        if (window != null) {
            val pgType = typeRegistry.types[desc.dataTypeOid]
            if (pgType != null && (pgType is PgType.Array ||
                pgType is PgType.Composite ||
                pgType is PgType.Range ||
                pgType is PgType.Multirange)) {
                
                container = ContainerParsers.parseContainer(window, desc.dataTypeOid, typeRegistry)
            }
        }
        Field(desc, window, container)
    }

    override val columnNames: List<String>
        get() = fields.map { it.descriptor.name }

    override fun getColumnIndex(columnName: String): Int {
        val index = fields.indexOfFirst { it.descriptor.name == columnName }
        if (index == -1) throw IllegalArgumentException("Column not found: $columnName")
        return index
    }

    override fun detach() {
        fields.forEach { it.detach() }
    }
}
