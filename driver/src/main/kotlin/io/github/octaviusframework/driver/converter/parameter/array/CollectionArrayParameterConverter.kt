package io.github.octaviusframework.driver.converter.parameter.array

import io.github.octaviusframework.driver.container.ArrayDimension
import io.github.octaviusframework.driver.container.PgArray
import io.github.octaviusframework.driver.container.PgContainer
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.exception.TypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.PgTyped
import io.github.octaviusframework.driver.type.UNRESOLVED_OID
import io.github.octaviusframework.driver.type.isKnownOid
import kotlin.reflect.KClass

class CollectionArrayParameterConverter : ParameterConverter<Any> {

    override val supportedClass: KClass<Any> = Any::class

    override fun canConvert(sourceClass: KClass<*>, expectedOid: Int, context: SerializationContext): Boolean {
        return Collection::class.java.isAssignableFrom(sourceClass.java) ||
               (sourceClass.java.isArray && sourceClass.java.componentType?.isPrimitive == false)
    }

    private fun getDimensionsAndFlatten(source: Any): Pair<List<ArrayDimension>, MutableList<Any?>> {
        val dimensions = mutableListOf<Int>()
        var current: Any? = source

        while (current is Collection<*> || current is Array<*>) {
            val size = if (current is Collection<*>) current.size else (current as Array<*>).size
            dimensions.add(size)
            current = if (current is Collection<*>) current.firstOrNull() else (current as Array<*>).firstOrNull()
        }

        val expectedSize = dimensions.fold(1) { acc, i -> acc * i }
        val arrayDimensions = dimensions.map { ArrayDimension(it, 1) }

        val flatList = ArrayList<Any?>(expectedSize)

        fun flattenInto(item: Any?) {
            when (item) {
                is Collection<*> -> item.forEach { flattenInto(it) }
                is Array<*> -> item.forEach { flattenInto(it) }
                else -> flatList.add(item)
            }
        }

        flattenInto(source)

        if (dimensions.isNotEmpty() && dimensions.first() > 0 && flatList.size != expectedSize) {
            throw IllegalArgumentException("Multidimensional arrays must be rectangular")
        }

        return arrayDimensions to flatList
    }

    override fun convert(source: Any, expectedOid: Int, context: SerializationContext): Any {
        val typeRegistry = context.typeManager.registry
        val (dimensions, list) = getDimensionsAndFlatten(source)

        val arrayType = if (expectedOid.isKnownOid) {
            typeRegistry.types[expectedOid] as? PgType.Array
        } else {
            // Try to infer from first non-null element
            val firstNonNull = list.firstOrNull { it != null }
            if (firstNonNull != null) {
                val converted = context.convert(firstNonNull, UNRESOLVED_OID)
                val elementOid = if (converted is PgTyped) {
                    context.typeManager.resolveOid(
                        converted.pgType.name,
                        converted.pgType.schema,
                        converted.pgType.isArray
                    )
                } else if (converted is PgContainer) {
                    converted.containerOid
                } else if (converted != null) {
                    typeRegistry.getCodecByClass(converted::class)?.oid
                } else null

                if (elementOid != null) {
                    typeRegistry.getArrayTypeByElementOid(elementOid)
                } else null
            } else null
        }

        if (arrayType == null) {
            throw TypeException(
                TypeExceptionMessage.TYPE_NOT_FOUND,
                details = "Cannot infer array type for the collection. The collection is empty, contains only nulls, or the element type is unknown. Use explicit typing (e.g. .withPgType(...))."
            )
        }

        val elementOid = arrayType.elementOid

        val convertedElements = list.map { element ->
            if (element != null) {
                context.convert(element, elementOid)
            } else null
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
