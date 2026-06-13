package io.github.octaviusframework.query

import io.github.octaviusframework.network.messages.RowDescriptionMessage.FieldDescription
import io.github.octaviusframework.types.TypeRegistry
import io.github.octaviusframework.container.*
import io.github.octaviusframework.io.ByteArrayWindow
import io.github.octaviusframework.types.PgType

import io.github.octaviusframework.container.PgContainer
import io.github.octaviusframework.deserialization.ObjectDeserializer
import io.github.octaviusframework.exceptions.OctaviusTypeException
import io.github.octaviusframework.exceptions.TypeExceptionMessage
import kotlin.reflect.typeOf

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
    val objectDeserializer: ObjectDeserializer

    fun getColumnIndex(columnName: String): Int
    fun detach()

    fun getRaw(index: Int): Any?
}

inline fun <reified T> Row.get(index: Int): T {
    val raw = getRaw(index)
    val oid = fields[index].descriptor.dataTypeOid
    val type = typeRegistry.types[oid]!!
    return objectDeserializer.deserialize(raw, typeOf<T>(), sourceType = type)
}

inline fun <reified T> Row.get(columnName: String): T {
    return get<T>(getColumnIndex(columnName))
}

inline fun <reified T> Row.getEntireRowAs(): T {
    return objectDeserializer.deserialize(this, typeOf<T>(),
        typeRegistry.types.entries.first { it.value is PgType.Record }.value)
}

class OctaviusRow(
    columns: List<ByteArrayWindow?>,
    descriptors: List<FieldDescription>,
    override val typeRegistry: TypeRegistry,
    override val objectDeserializer: ObjectDeserializer
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
            val value = serializer.fromBinary(fieldWindow)
            field.value = value
            field.rawValue = null
            return value
        }
        
        throw OctaviusTypeException(TypeExceptionMessage.MISSING_SERIALIZER, oid = oid, details = "Row")
    }
}
