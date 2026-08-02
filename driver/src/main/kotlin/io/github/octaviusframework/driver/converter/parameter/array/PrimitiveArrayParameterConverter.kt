package io.github.octaviusframework.driver.converter.parameter.array

import io.github.octaviusframework.driver.container.ArrayDimension
import io.github.octaviusframework.driver.container.PgArray
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.exception.OctaviusInternalException
import io.github.octaviusframework.driver.exception.TypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.isKnownOid
import kotlin.reflect.KClass

class PrimitiveArrayParameterConverter : ParameterConverter<Any> {

    override val supportedClass: KClass<Any> = Any::class

    override fun canConvert(sourceClass: KClass<*>, expectedOid: Int, context: SerializationContext): Boolean {
        if (sourceClass == ByteArray::class) return false
        return sourceClass.java.isArray && sourceClass.java.componentType?.isPrimitive == true
    }

    override fun convert(source: Any, expectedOid: Int, context: SerializationContext): Any {
        val typeRegistry = context.typeManager.registry

        val arrayType = if (expectedOid.isKnownOid) {
            typeRegistry.types[expectedOid] as? PgType.Array
        } else {
            val componentType = source.javaClass.componentType?.kotlin
            if (componentType != null) {
                val elementOid = typeRegistry.getCodecByClass(componentType)?.oid
                if (elementOid != null) {
                    typeRegistry.getArrayTypeByElementOid(elementOid)
                } else null
            } else null
        }

        if (arrayType == null) {
            throw TypeException(
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
            else -> throw OctaviusInternalException()
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

