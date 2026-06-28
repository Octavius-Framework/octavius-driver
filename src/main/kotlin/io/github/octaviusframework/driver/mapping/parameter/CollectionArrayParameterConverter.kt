package io.github.octaviusframework.driver.mapping.parameter

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeRegistry
import io.github.octaviusframework.driver.type.containter.ArrayDimension
import io.github.octaviusframework.driver.type.containter.PgArray
import io.github.octaviusframework.driver.type.containter.PgContainer

class CollectionArrayParameterConverter : ParameterConverter<Any> {
    override fun canConvert(source: Any, expectedOid: UInt?, typeRegistry: TypeRegistry): Boolean {
        return source is Collection<*> || source is Array<*>
    }

    override fun convert(source: Any, expectedOid: UInt?, context: SerializationContext, typeRegistry: TypeRegistry): Any? {
        val list = when (source) {
            is Collection<*> -> source.toList()
            is Array<*> -> source.toList()
            else -> return source
        }

        val arrayType = if (expectedOid != null) {
            typeRegistry.types[expectedOid] as? PgType.Array
        } else {
            // Try to infer from first non-null element
            val firstNonNull = list.firstOrNull { it != null }
            if (firstNonNull != null) {
                val elementOid = typeRegistry.getCodecByClass(firstNonNull::class)?.oid
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
        val dimensions = listOf(ArrayDimension(list.size, 1))
        
        // Convert elements recursively
        val convertedElements = list.map { element ->
            if (element != null) {
                context.convert(element, elementOid)
            } else null
        }

        // We can't easily build PgArray with internal raw arrays in ContainerCodec, but we can construct it.
        // Actually, PgArray has values, containers, windows.
        // Let's populate values and containers.
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
