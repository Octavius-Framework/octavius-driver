package io.github.octaviusframework.driver.converter.parameter.array

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.PgTyped
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.type.container.ArrayDimension
import io.github.octaviusframework.driver.type.container.PgArray
import io.github.octaviusframework.driver.type.container.PgContainer

class PrimitiveArrayParameterConverter : ParameterConverter<Any> {
    override fun canConvert(source: Any, expectedOid: Int?, typeManager: TypeManager): Boolean {
        if (source is ByteArray) return false
        return source.javaClass.isArray && source.javaClass.componentType?.isPrimitive == true
    }

    override fun convert(source: Any, expectedOid: Int?, context: SerializationContext, typeManager: TypeManager): Any? {
        val typeRegistry = typeManager.registry

        val list = when (source) {
            is IntArray -> source.toList()
            is DoubleArray -> source.toList()
            is FloatArray -> source.toList()
            is LongArray -> source.toList()
            is ShortArray -> source.toList()
            is BooleanArray -> source.toList()
            is CharArray -> source.toList()
            else -> throw IllegalArgumentException("Unsupported primitive array type")
        }

        val dimensions = listOf(ArrayDimension(list.size, 1))

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
        val convertedElements = list.map { element ->
            context.convert(element, elementOid)
        }

        return PgArray(
            arrayOid = arrayType.oid,
            elementOid = elementOid,
            dimensions = dimensions,
            elements = convertedElements.toMutableList(),
            typeRegistry = typeRegistry
        )
    }
}

