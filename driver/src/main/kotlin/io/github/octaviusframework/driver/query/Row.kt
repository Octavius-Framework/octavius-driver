package io.github.octaviusframework.driver.query

// Removed ByteArrayWindow import
import io.github.octaviusframework.driver.codec.dynamic.ContainerCodec
import io.github.octaviusframework.driver.converter.result.mapper.ResultMapper
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.message.backend.FieldDescription
import io.github.octaviusframework.driver.message.backend.RowDescriptionMessage
import io.github.octaviusframework.driver.registry.TypeRegistry
import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface Row {
    val columnNames: List<String>
    val typeRegistry: TypeRegistry
    val resultMapper: ResultMapper

    fun getColumnIndex(columnName: String): Int

    fun getRaw(index: Int): Any?
    fun getOid(index: Int): Int
}

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
        return nameToIndexCache[columnName] ?: throw IllegalArgumentException("Column not found: $columnName")
    }

    fun getOid(index: Int): Int {
        if (index !in descriptors.indices) throw IllegalArgumentException("Column index out of bounds: $index")
        return descriptors[index].dataTypeOid
    }
}

class OctaviusRow(
    rawData: ByteArray,
    columnOffsets: IntArray,
    columnLengths: IntArray,
    val metadata: RowMetadata,
    override val typeRegistry: TypeRegistry,
    override val resultMapper: ResultMapper
) : Row {

    private val values: List<Any?> = List(metadata.size) { index ->
        val colLength = columnLengths[index]
        if (colLength == -1) null
        else {
            val offset = columnOffsets[index]
            val oid = metadata.getOid(index)
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
        get() = metadata.columnNames

    override fun getColumnIndex(columnName: String): Int {
        return metadata.getColumnIndex(columnName)
    }

    override fun getRaw(index: Int): Any? {
        if (index !in values.indices) throw IllegalArgumentException("Column index out of bounds: $index")
        return values[index]
    }

    override fun getOid(index: Int): Int {
        return metadata.getOid(index)
    }
}
