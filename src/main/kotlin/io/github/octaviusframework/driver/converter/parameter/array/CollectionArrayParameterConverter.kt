package io.github.octaviusframework.driver.converter.parameter.array

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.PgTyped
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.type.containter.ArrayDimension
import io.github.octaviusframework.driver.type.containter.PgArray
import io.github.octaviusframework.driver.type.containter.PgContainer

class CollectionArrayParameterConverter : ParameterConverter<Any> {
    override fun canConvert(source: Any, expectedOid: UInt?, typeManager: TypeManager): Boolean {
        if (source is ByteArray) return false
        return source is Collection<*> || source is Array<*> || source::class.java.isArray
    }

    private fun getDimensionsAndFlatten(source: Any): Pair<List<ArrayDimension>, List<Any?>> {
        val dimensions = mutableListOf<Int>()
        var current: Any? = source
        while (current is Collection<*> || (current != null && current::class.java.isArray && current !is ByteArray)) {
            val list = when (current) {
                is Collection<*> -> current.toList()
                is Array<*> -> current.toList()
                is IntArray -> current.toList()
                is DoubleArray -> current.toList()
                is FloatArray -> current.toList()
                is LongArray -> current.toList()
                is ShortArray -> current.toList()
                is BooleanArray -> current.toList()
                is CharArray -> current.toList()
                else -> break
            }
            dimensions.add(list.size)
            current = list.firstOrNull()
        }

        val arrayDimensions = dimensions.map { ArrayDimension(it, 1) }
        val flatList = flatten(source)

        val expectedSize = dimensions.fold(1) { acc, i -> acc * i }
        if (dimensions.isNotEmpty() && dimensions.first() > 0 && flatList.size != expectedSize) {
            throw IllegalArgumentException("Multidimensional arrays must be rectangular")
        }

        return arrayDimensions to flatList
    }

    private fun flatten(source: Any?): List<Any?> {
        when (source) {
            is Collection<*> -> return source.flatMap { flatten(it) }
            is Array<*> -> return source.flatMap { flatten(it) }
            is IntArray -> return source.flatMap { flatten(it) }
            is DoubleArray -> return source.flatMap { flatten(it) }
            is FloatArray -> return source.flatMap { flatten(it) }
            is LongArray -> return source.flatMap { flatten(it) }
            is ShortArray -> return source.flatMap { flatten(it) }
            is BooleanArray -> return source.flatMap { flatten(it) }
            is CharArray -> return source.flatMap { flatten(it) }
            else -> return listOf(source)
        }
    }

    override fun convert(source: Any, expectedOid: UInt?, context: SerializationContext, typeManager: TypeManager): Any? {
        val typeRegistry = typeManager.registry
        val (dimensions, list) = getDimensionsAndFlatten(source)

        val arrayType = if (expectedOid != null) {
            typeRegistry.types[expectedOid] as? PgType.Array
        } else {
            // Try to infer from first non-null element
            val firstNonNull = list.firstOrNull { it != null }
            if (firstNonNull != null) {
                val converted = context.convert(firstNonNull, null)
                val elementOid = if (converted is PgTyped) {
                    typeRegistry.resolveOid(converted.pgType.name, converted.pgType.schema, emptyList(), converted.pgType.isArray).first
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

        // Convert elements recursively
        val convertedElements = list.map { element ->
            if (element != null) {
                context.convert(element, elementOid)
            } else null
        }

        val values = mutableListOf<Any?>()
        val containers = mutableListOf<PgContainer?>()

        for (element in convertedElements) {
            if (element is PgContainer) {
                values.add(null)
                containers.add(element)
            } else {
                values.add(element)
                containers.add(null)
            }
        }

        return PgArray(
            arrayOid = arrayType.oid,
            elementOid = elementOid,
            dimensions = dimensions,
            values = values,
            containers = containers,
            windows = null,
            typeRegistry = typeRegistry
        )
    }
}