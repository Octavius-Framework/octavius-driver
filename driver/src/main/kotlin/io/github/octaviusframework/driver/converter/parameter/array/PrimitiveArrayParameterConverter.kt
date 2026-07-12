package io.github.octaviusframework.driver.converter.parameter.array

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.container.ArrayDimension
import io.github.octaviusframework.driver.container.PgArray

class PrimitiveArrayParameterConverter : ParameterConverter<Any> {
    override fun canConvert(source: Any, expectedOid: Int?, typeManager: TypeManager): Boolean {
        if (source is ByteArray) return false
        return source.javaClass.isArray && source.javaClass.componentType?.isPrimitive == true
    }

    override fun convert(source: Any, expectedOid: Int?, context: SerializationContext, typeManager: TypeManager): Any? {
        val typeRegistry = typeManager.registry

        val arrayType = if (expectedOid != null) {
            typeRegistry.types[expectedOid] as? PgType.Array
        } else {
            val componentType = source.javaClass.componentType?.kotlin
            if (componentType != null) {
                val elementOid = typeRegistry.getCodecByClass(componentType)?.oid
                if (elementOid != null) {
                    typeRegistry.types.values.firstOrNull { it is PgType.Array && it.elementOid == elementOid } as? PgType.Array
                } else null
            } else null
        }

        if (arrayType == null) {
            throw OctaviusTypeException(
                TypeExceptionMessage.TYPE_NOT_FOUND,
                details = "Cannot infer array type for the primitive array. The array is empty, or the element type is unknown. Use explicit typing (e.g. .withPgType(...))."
            )
        }

        val elementOid = arrayType.elementOid

        val convertedElements: MutableList<Any?> = when (source) {
            is IntArray -> MutableList(source.size) { context.convert(source[it], elementOid) }
            is DoubleArray -> MutableList(source.size) { context.convert(source[it], elementOid) }
            is FloatArray -> MutableList(source.size) { context.convert(source[it], elementOid) }
            is LongArray -> MutableList(source.size) { context.convert(source[it], elementOid) }
            is ShortArray -> MutableList(source.size) { context.convert(source[it], elementOid) }
            is BooleanArray -> MutableList(source.size) { context.convert(source[it], elementOid) }
            is CharArray -> MutableList(source.size) { context.convert(source[it], elementOid) }
            else -> throw IllegalArgumentException("Unsupported primitive array type")
        }

        val dimensions = listOf(ArrayDimension(convertedElements.size, 1))

        return PgArray(
            arrayOid = arrayType.oid,
            elementOid = elementOid,
            dimensions = dimensions,
            elements = convertedElements,
            typeRegistry = typeRegistry
        )
    }
}

