package io.github.octaviusframework.driver.row

// Removed ByteArrayWindow import

import io.github.octaviusframework.driver.converter.result.mapper.ResultMapper
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.registry.TypeRegistry
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Suppress("UNCHECKED_CAST")
fun <T> Row.get(index: Int, targetType: KType): T {
    val raw = getRaw(index)
    val oid = getOid(index)
    val type = typeRegistry.types[oid]!!
    return resultMapper.deserialize(raw, targetType, sourceType = type) as T
}

inline fun <reified T> Row.get(index: Int): T {
    return get(index, typeOf<T>())
}

@Suppress("UNCHECKED_CAST")
fun <T> Row.get(columnName: String, targetType: KType): T {
    return get<T>(getColumnIndex(columnName), targetType)
}

inline fun <reified T> Row.get(columnName: String): T {
    return get<T>(getColumnIndex(columnName), typeOf<T>())
}

class Row(
    rawData: ByteArray,
    columnOffsets: IntArray,
    columnLengths: IntArray,
    val metadata: RowMetadata,
    val typeRegistry: TypeRegistry,
    val resultMapper: ResultMapper
) {

    private val values: List<Any?> = List(metadata.size) { index ->
        val colLength = columnLengths[index]
        if (colLength == -1) null
        else {
            val offset = columnOffsets[index]
            val oid = metadata.getOid(index)
            val codec = typeRegistry.getCodecByOid<Any>(oid)
                ?: throw OctaviusTypeException(TypeExceptionMessage.MISSING_CODEC, oid = oid, details = "Row")
            codec.fromBinary(rawData, offset, colLength)
        }
    }

    val columnNames: List<String>
        get() = metadata.columnNames

    fun getColumnIndex(columnName: String): Int {
        return metadata.getColumnIndex(columnName)
    }

    fun getRaw(index: Int): Any? {
        if (index !in values.indices) throw IllegalArgumentException("Column index out of bounds: $index")
        return values[index]
    }

    fun getOid(index: Int): Int {
        return metadata.getOid(index)
    }
}
