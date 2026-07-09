package io.github.octaviusframework.driver.converter.parameter.array

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.PgTyped
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.container.ArrayDimension
import io.github.octaviusframework.driver.container.PgArray

class CollectionArrayParameterConverter : ParameterConverter<Any> {
    override fun canConvert(source: Any, expectedOid: Int?, typeManager: TypeManager): Boolean {
        return source is Collection<*> || source is Array<*>
    }

    private fun getDimensionsAndFlatten(source: Any): Pair<List<ArrayDimension>, Array<Any?>> {
        val dimensions = mutableListOf<Int>()
        var current: Any? = source

        while (current is Collection<*> || current is Array<*>) {
            val size = if (current is Collection<*>) current.size else (current as Array<*>).size
            dimensions.add(size)
            current = if (current is Collection<*>) current.firstOrNull() else (current as Array<*>).firstOrNull()
        }

        val expectedSize = dimensions.fold(1) { acc, i -> acc * i }
        val arrayDimensions = dimensions.map { ArrayDimension(it, 1) }

        val flatBuffer = arrayOfNulls<Any?>(expectedSize)
        var index = 0

        fun flattenInto(item: Any?) {
            when (item) {
                is Collection<*> -> item.forEach { flattenInto(it) }
                is Array<*> -> item.forEach { flattenInto(it) }
                else -> flatBuffer[index++] = item
            }
        }

        flattenInto(source)

        if (dimensions.isNotEmpty() && dimensions.first() > 0 && index != expectedSize) {
            throw IllegalArgumentException("Multidimensional arrays must be rectangular")
        }

        return arrayDimensions to flatBuffer
    }

    override fun convert(source: Any, expectedOid: Int?, context: SerializationContext, typeManager: TypeManager): Any? {
        val typeRegistry = typeManager.registry
        val (dimensions, flatList) = getDimensionsAndFlatten(source)

        val arrayType = if (expectedOid != null) {
            typeRegistry.types[expectedOid] as? PgType.Array
        } else {
            val firstNonNull = flatList.firstOrNull { it != null }
            if (firstNonNull != null) {
                val converted = context.convert(firstNonNull, null)
                val elementOid = if (converted is PgTyped) {
                    typeManager.resolveOid(
                        converted.pgType.name,
                        converted.pgType.schema,
                        converted.pgType.isArray
                    ).first
                } else if (converted != null) {
                    typeRegistry.getCodecByClass(converted::class)?.oid
                } else null

                if (elementOid != null) {
                    typeRegistry.types.values.firstOrNull { it is PgType.Array && it.elementOid == elementOid } as? PgType.Array
                } else null
            } else null
        }

        if (arrayType == null) {
            throw OctaviusTypeException(
                TypeExceptionMessage.TYPE_NOT_FOUND,
                details = "Cannot infer array type for the collection. The collection is empty, contains only nulls, or the element type is unknown. Use explicit typing (e.g. .withPgType(...))."
            )
        }

        val elementOid = arrayType.elementOid

        val convertedElements = Array(flatList.size) { i ->
            val element = flatList[i]
            if (element != null) {
                context.convert(element, elementOid)
            } else null
        }

        return PgArray(
            arrayOid = arrayType.oid,
            elementOid = elementOid,
            dimensions = dimensions,
            elements = convertedElements,
            typeRegistry = typeRegistry
        )
    }
}
