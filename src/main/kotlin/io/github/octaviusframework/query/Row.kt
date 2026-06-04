package io.github.octaviusframework.query

import io.github.octaviusframework.network.messages.DataRowMessage
import io.github.octaviusframework.network.messages.RowDescriptionMessage

interface Row {
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
    private val dataRow: DataRowMessage,
    private val rowDescription: RowDescriptionMessage?
) : Row {

    override val columnNames: List<String>
        get() = rowDescription?.fields?.map { it.name } ?: emptyList()

    private fun getColumnIndex(columnName: String): Int {
        val index = rowDescription?.fields?.indexOfFirst { it.name == columnName } ?: -1
        if (index == -1) throw IllegalArgumentException("Column not found: $columnName")
        return index
    }

    override fun getValue(columnName: String): Any? = getValue(getColumnIndex(columnName))

    override fun getValue(index: Int): Any? {
        val bytes = dataRow.columns.getOrNull(index) ?: return null
        // TODO: Decode properly using TypeRegistry based on OID from rowDescription.
        // For now, defaulting to string decoding.
        return String(bytes)
    }
}
