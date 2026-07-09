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

        val elementClass = when (source) {
            is IntArray -> Int::class
            is DoubleArray -> Double::class
            is FloatArray -> Float::class
            is LongArray -> Long::class
            is ShortArray -> Short::class
            is BooleanArray -> Boolean::class
            is CharArray -> Char::class
            else -> throw IllegalArgumentException("Unsupported primitive array type")
        }

        val arrayType = if (expectedOid != null) {
            typeRegistry.types[expectedOid] as? PgType.Array
        } else {
            val elementOid = typeRegistry.getCodecByClass(elementClass)?.oid
            if (elementOid != null) {
                typeRegistry.types.values.firstOrNull { it is PgType.Array && it.elementOid == elementOid } as? PgType.Array
            } else null
        }

        if (arrayType == null) {
            throw OctaviusTypeException(
                TypeExceptionMessage.TYPE_NOT_FOUND,
                details = "Cannot infer array type for the primitive array. Use explicit typing (e.g. .withPgType(...))."
            )
        }

        val elementOid = arrayType.elementOid

        val convertedElements: Array<Any?> = when (source) {
            is IntArray -> Array(source.size) { context.convert(source[it], elementOid) }
            is DoubleArray -> Array(source.size) { context.convert(source[it], elementOid) }
            is FloatArray -> Array(source.size) { context.convert(source[it], elementOid) }
            is LongArray -> Array(source.size) { context.convert(source[it], elementOid) }
            is ShortArray -> Array(source.size) { context.convert(source[it], elementOid) }
            is BooleanArray -> Array(source.size) { context.convert(source[it], elementOid) }
            is CharArray -> Array(source.size) { context.convert(source[it], elementOid) }
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
