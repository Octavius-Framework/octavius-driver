package io.github.octaviusframework.query

import io.github.octaviusframework.network.messages.RowDescriptionMessage.FieldDescription
import io.github.octaviusframework.types.TypeRegistry

data class Field(
    val descriptor: FieldDescription,
    val rawValue: ByteArray?
)

interface Row {
    val fields: List<Field>
    val columnNames: List<String>

    fun getValue(columnName: String): Any?
    fun getValue(index: Int): Any?
}

inline fun <reified T> Row.get(columnName: String): T {
    val value = getValue(columnName)
    if (value is T) {
        return value
    } else {
        throw IllegalStateException("Type mismatch: Expected type matching ${T::class.simpleName}, but got ${if (value == null) "null" else value::class.simpleName}")
    }
}

inline fun <reified T> Row.get(index: Int): T {
    val value = getValue(index)
    if (value is T) {
        return value
    } else {
        throw IllegalStateException("Type mismatch: Expected type matching ${T::class.simpleName}, but got ${if (value == null) "null" else value::class.simpleName}")
    }
}

class OctaviusRow(
    columns: List<ByteArray?>,
    descriptors: List<FieldDescription>,
    private val typeRegistry: TypeRegistry
) : Row {

    override val fields: List<Field> = descriptors.zip(columns) { desc, bytes ->
        Field(desc, bytes)
    }

    override val columnNames: List<String>
        get() = fields.map { it.descriptor.name }

    private fun getColumnIndex(columnName: String): Int {
        val index = fields.indexOfFirst { it.descriptor.name == columnName }
        if (index == -1) throw IllegalArgumentException("Column not found: $columnName")
        return index
    }

    override fun getValue(columnName: String): Any? = getValue(getColumnIndex(columnName))

    override fun getValue(index: Int): Any? {
        val field = fields.getOrNull(index) ?: return null
        val bytes = field.rawValue ?: return null
        val handler = typeRegistry.getHandlerByOid<Any>(field.descriptor.dataTypeOid)
        if (handler != null) {
            return handler.fromBinary(bytes)
        }
        return String(bytes)
    }
}
