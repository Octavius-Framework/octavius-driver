package io.github.octaviusframework.driver.row

import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason

/**
 * Holds metadata information about the columns in a row returned by a query.
 *
 * This includes the descriptions of individual fields, the total number of columns,
 * and a caching mechanism to efficiently resolve column names to their indices.
 *
 * @property descriptors The list of [FieldDescription] objects representing each column's metadata.
 */
class RowMetadata(
    val descriptors: List<FieldDescription>
) {
    /**
     * The total number of columns in the row.
     */
    val size: Int get() = descriptors.size

    /**
     * A list of all column names in the order they were returned.
     */
    val columnNames: List<String> = descriptors.map { it.name }

    private val nameToIndexCache: Map<String, Int>

    init {
        val map = HashMap<String, Int>()
        descriptors.forEachIndexed { index, desc ->
            map.putIfAbsent(desc.name, index)
        }
        nameToIndexCache = map
    }

    /**
     * Finds the zero-based index of the column with the specified [columnName].
     *
     * If multiple columns share the same name, this returns the index of the first occurrence.
     *
     * @param columnName The name of the column.
     * @return The zero-based index of the column.
     * @throws MappingException if no column with the given name exists.
     */
    fun getColumnIndex(columnName: String): Int {
        return nameToIndexCache[columnName] ?: throw MappingException(MappingExceptionReason.COLUMN_NOT_FOUND, "Column not found: $columnName")
    }

    /**
     * Retrieves the PostgreSQL Object Identifier (OID) for the type of the column at the specified [index].
     *
     * @param index The zero-based index of the column.
     * @return The OID of the column's data type.
     * @throws MappingException if the index is out of bounds.
     */
    fun getOid(index: Int): Int {
        if (index !in descriptors.indices) throw MappingException(MappingExceptionReason.COLUMN_NOT_FOUND, "Column index out of bounds: $index")
        return descriptors[index].dataTypeOid
    }
}