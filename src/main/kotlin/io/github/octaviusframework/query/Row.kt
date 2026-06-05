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

inline fun <reified T> Row.get(columnName: String): T {
    val index = getColumnIndex(columnName)
    return get<T>(index)
}

inline fun <reified T> Row.get(index: Int): T {
    val field = fields.getOrNull(index) ?: throw IllegalArgumentException("Column index out of bounds: $index")

    val fieldValue = field.value
    val fieldContainer = field.container
    val fieldWindow = field.rawValue

    val parsedValue: Any? = if (fieldValue != null) {
        fieldValue
    } else if (fieldContainer != null) {
        fieldContainer
    } else if (fieldWindow != null) {
        val oid = field.descriptor.dataTypeOid
        val handler = typeRegistry.getHandlerByOid<Any>(oid)
        if (handler != null) {
            handler.fromBinary(fieldWindow)
        } else if (String::class == T::class) {
            String(fieldWindow.data, fieldWindow.offset, fieldWindow.length, Charsets.UTF_8)
        } else {
            throw IllegalStateException("Brak handlera dla OID: $oid oraz typu ${T::class.simpleName}")
        }
    } else {
        null
    }

    if (parsedValue == null) {
        if (null is T) {
            return null as T
        } else {
            throw NullPointerException("Wartość dla kolumny o indeksie $index wynosi null, ale oczekiwano nienullowalnego typu ${T::class.simpleName}")
        }
    }

    if (parsedValue is T) {
        return parsedValue
    } else {
        throw IllegalStateException("Błąd rzutowania na indeksie $index: Oczekiwano ${T::class.simpleName}, a otrzymano ${parsedValue::class.simpleName}")
    }
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

    private val nameToIndexCache: Map<String, Int> by lazy {
        val map = HashMap<String, Int>()
        fields.forEachIndexed { index, field ->
            map.putIfAbsent(field.descriptor.name, index)
        }
        map
    }

    override fun getColumnIndex(columnName: String): Int {
        return nameToIndexCache[columnName] ?: throw IllegalArgumentException("Column not found: $columnName")
    }

    override fun detach() {
        fields.forEach { it.detach() }
    }
}
