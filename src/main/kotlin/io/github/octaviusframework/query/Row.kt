package io.github.octaviusframework.query

import io.github.octaviusframework.network.messages.RowDescriptionMessage.FieldDescription
import io.github.octaviusframework.types.TypeRegistry
import io.github.octaviusframework.containter.*
import io.github.octaviusframework.types.PgType

data class Field(
    val descriptor: FieldDescription,
    val rawValue: ByteArray?,
    val eagerContainer: Any? = null
)

interface Row {
    val fields: List<Field>
    val columnNames: List<String>
    val typeRegistry: TypeRegistry

    fun getColumnIndex(columnName: String): Int
}

inline fun <reified T> Row.get(columnName: String): T? {
    val index = getColumnIndex(columnName)
    return get<T>(index)
}

inline fun <reified T> Row.get(index: Int): T? {
    val field = fields.getOrNull(index) ?: return null
    if (field.eagerContainer != null && field.eagerContainer is T) {
        return field.eagerContainer as T
    }

    val bytes = field.rawValue ?: return null
    val oid = field.descriptor.dataTypeOid

    val handler = typeRegistry.getHandlerByOid<Any>(oid)
    if (handler != null) {
        val parsed = handler.fromBinary(bytes)
        if (parsed is T) {
            return parsed
        } else {
            throw IllegalStateException("Błąd rzutowania na indeksie $index: Oczekiwano ${T::class.simpleName}, a otrzymano ${parsed::class.simpleName}")
        }
    }
    
    if (String::class == T::class) return String(bytes) as T
    throw IllegalStateException("Brak handlera dla OID: $oid oraz typu ${T::class.simpleName}")
}

class OctaviusRow(
    columns: List<ByteArray?>,
    descriptors: List<FieldDescription>,
    override val typeRegistry: TypeRegistry
) : Row {

    override val fields: List<Field> = descriptors.zip(columns) { desc, bytes ->
        var eagerContainer: Any? = null
        if (bytes != null) {
            val pgType = typeRegistry.types[desc.dataTypeOid]
            if (pgType != null) {
                eagerContainer = when (pgType) {
                    is PgType.Array -> ContainerParsers.parsePgArray(bytes, desc.dataTypeOid, typeRegistry)
                    is PgType.Composite -> ContainerParsers.parsePgComposite(bytes, desc.dataTypeOid, typeRegistry)
                    is PgType.Range -> ContainerParsers.parsePgRange(bytes, desc.dataTypeOid, typeRegistry)
                    is PgType.Multirange -> ContainerParsers.parsePgMultirange(bytes, desc.dataTypeOid, typeRegistry)
                    else -> null
                }
            }
        }
        Field(desc, if (eagerContainer != null) null else bytes, eagerContainer)
    }

    override val columnNames: List<String>
        get() = fields.map { it.descriptor.name }

    override fun getColumnIndex(columnName: String): Int {
        val index = fields.indexOfFirst { it.descriptor.name == columnName }
        if (index == -1) throw IllegalArgumentException("Column not found: $columnName")
        return index
    }
}
