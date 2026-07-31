package io.github.octaviusframework.driver.row

import io.github.octaviusframework.driver.exception.MappingExceptionMessage
import io.github.octaviusframework.driver.exception.OctaviusMappingException

class RowMetadata(
    val descriptors: List<FieldDescription>
) {
    val size: Int get() = descriptors.size

    val columnNames: List<String> = descriptors.map { it.name }

    private val nameToIndexCache: Map<String, Int>

    init {
        val map = HashMap<String, Int>()
        descriptors.forEachIndexed { index, desc ->
            map.putIfAbsent(desc.name, index)
        }
        nameToIndexCache = map
    }

    fun getColumnIndex(columnName: String): Int {
        return nameToIndexCache[columnName] ?: throw OctaviusMappingException(MappingExceptionMessage.COLUMN_NOT_FOUND, "Column not found: $columnName")
    }

    fun getOid(index: Int): Int {
        if (index !in descriptors.indices) throw OctaviusMappingException(MappingExceptionMessage.COLUMN_INDEX_OUT_OF_BOUNDS, "Column index out of bounds: $index")
        return descriptors[index].dataTypeOid
    }
}