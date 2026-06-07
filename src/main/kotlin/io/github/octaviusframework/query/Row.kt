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
    val objectDeserializer: io.github.octaviusframework.deserialization.ObjectDeserializer

    fun getColumnIndex(columnName: String): Int
    fun detach()

    fun getRaw(index: Int): Any?
    fun getRaw(columnName: String): Any?
}

inline fun <reified T> Row.getConverted(index: Int): T? {
    val raw = getRaw(index)
    if (raw == null) {
        if (null is T) return null as T
        throw NullPointerException("Wartość dla kolumny o indeksie $index wynosi null, ale oczekiwano nienullowalnego typu ${T::class.simpleName}")
    }
    
    if (raw is T) return raw
    return objectDeserializer.deserialize(raw, kotlin.reflect.typeOf<T>())
}

inline fun <reified T> Row.getConverted(columnName: String): T? {
    return getConverted<T>(getColumnIndex(columnName))
}

inline fun <reified T> Row.asClass(): T {
    val mapped = objectDeserializer.deserialize<T>(this, kotlin.reflect.typeOf<T>())
    return mapped ?: throw IllegalStateException("Nie udało się zmapować wiersza na klasę ${T::class.simpleName}")
}

fun Row.asMap(): Map<String, Any?> {
    val mapped = objectDeserializer.deserialize<Map<String, Any?>>(this, kotlin.reflect.typeOf<Map<String, Any?>>())
    return mapped ?: emptyMap()
}

@Deprecated("Użyj getConverted() lub getRaw() aby uniknąć niejednoznaczności", ReplaceWith("getConverted<T>(columnName)"))
inline fun <reified T> Row.get(columnName: String): T {
    return getConverted<T>(columnName) ?: throw NullPointerException()
}

@Deprecated("Użyj getConverted() lub getRaw() aby uniknąć niejednoznaczności", ReplaceWith("getConverted<T>(index)"))
inline fun <reified T> Row.get(index: Int): T {
    return getConverted<T>(index) ?: throw NullPointerException()
}


class OctaviusRow(
    columns: List<ByteArrayWindow?>,
    descriptors: List<FieldDescription>,
    override val typeRegistry: TypeRegistry,
    override val objectDeserializer: io.github.octaviusframework.deserialization.ObjectDeserializer
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

    override fun getRaw(index: Int): Any? {
        val field = fields.getOrNull(index) ?: throw IllegalArgumentException("Column index out of bounds: $index")

        val fieldValue = field.value
        if (fieldValue != null) return fieldValue

        val fieldContainer = field.container
        if (fieldContainer != null) return fieldContainer

        val fieldWindow = field.rawValue ?: return null

        val oid = field.descriptor.dataTypeOid
        val serializer = typeRegistry.getSerializerByOid<Any>(oid)
        
        if (serializer != null) {
            return serializer.fromBinary(fieldWindow)
        }
        
        return String(fieldWindow.data, fieldWindow.offset, fieldWindow.length, Charsets.UTF_8)
    }

    override fun getRaw(columnName: String): Any? {
        return getRaw(getColumnIndex(columnName))
    }
}
