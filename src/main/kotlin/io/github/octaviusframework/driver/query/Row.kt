package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
// Removed ByteArrayWindow import
import io.github.octaviusframework.driver.converter.result.mapper.ResultMapper
import io.github.octaviusframework.driver.message.backend.RowDescriptionMessage
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeRegistry
import io.github.octaviusframework.driver.type.container.PgContainer
import io.github.octaviusframework.driver.codec.dynamic.ContainerCodec
import kotlin.reflect.typeOf

interface Row {
    val columnNames: List<String>
    val typeRegistry: TypeRegistry
    val resultMapper: ResultMapper

    fun getColumnIndex(columnName: String): Int

    fun getRaw(index: Int): Any?
    fun getOid(index: Int): Int
}

inline fun <reified T> Row.get(index: Int): T {
    val raw = getRaw(index)
    val oid = getOid(index)
    val type = typeRegistry.types[oid]!!
    return resultMapper.deserialize(raw, typeOf<T>(), sourceType = type)
}

inline fun <reified T> Row.get(columnName: String): T {
    return get<T>(getColumnIndex(columnName))
}

inline fun <reified T> Row.getEntireRowAs(): T {
    return resultMapper.deserialize(this, typeOf<T>(), PgType.Record(2249, "record", "pg_catalog"))
}

class OctaviusRow(
    rawData: ByteArray,
    columnOffsets: IntArray,
    columnLengths: IntArray,
    val descriptors: List<RowDescriptionMessage.FieldDescription>,
    override val typeRegistry: TypeRegistry,
    override val resultMapper: ResultMapper
) : Row {

    private val values: List<Any?> = List(descriptors.size) { index ->
        val colLength = columnLengths[index]
        if (colLength == -1) null
        else {
            val offset = columnOffsets[index]
            val oid = descriptors[index].dataTypeOid
            if (ContainerCodec.isContainerType(oid, typeRegistry)) {
                ContainerCodec.parseContainer(rawData, offset, colLength, oid, typeRegistry)
            } else {
                val codec = typeRegistry.getCodecByOid<Any>(oid)
                    ?: throw OctaviusTypeException(TypeExceptionMessage.MISSING_CODEC, oid = oid, details = "Row")
                codec.fromBinary(rawData, offset, colLength)
            }
        }
    }

    override val columnNames: List<String>
        get() = descriptors.map { it.name }

    private val nameToIndexCache: Map<String, Int> by lazy {
        val map = HashMap<String, Int>()
        descriptors.forEachIndexed { index, desc ->
            map.putIfAbsent(desc.name, index)
        }
        map
    }

    override fun getColumnIndex(columnName: String): Int {
        return nameToIndexCache[columnName] ?: throw IllegalArgumentException("Column not found: $columnName")
    }

    override fun getRaw(index: Int): Any? {
        if (index !in values.indices) throw IllegalArgumentException("Column index out of bounds: $index")
        return values[index]
    }

    override fun getOid(index: Int): Int {
        if (index !in descriptors.indices) throw IllegalArgumentException("Column index out of bounds: $index")
        return descriptors[index].dataTypeOid
    }
}
