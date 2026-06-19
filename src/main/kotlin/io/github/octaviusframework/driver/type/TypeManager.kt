package io.github.octaviusframework.driver.type

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.jdbc.OctaviusConnection
import io.github.octaviusframework.driver.mapping.parameter.ParameterConverter
import io.github.octaviusframework.driver.mapping.result.ResultConverter
import io.github.octaviusframework.driver.type.containter.ArrayDimension
import io.github.octaviusframework.driver.type.containter.ContainerField
import io.github.octaviusframework.driver.type.containter.PgArray
import io.github.octaviusframework.driver.type.containter.PgComposite
import io.github.octaviusframework.driver.type.containter.PgContainer
import io.github.octaviusframework.driver.type.containter.PgMultirange
import io.github.octaviusframework.driver.type.containter.PgRange

class TypeManager(private val connection: OctaviusConnection) {
    val registry: TypeRegistry
        get() = connection.typeRegistry

    fun registerResultConverter(converter: ResultConverter<*>) = registry.registerResultConverter(converter)
    fun registerParameterConverter(converter: ParameterConverter<*>) = registry.registerParameterConverter(converter)

    fun createComposite(typeName: String, schema: String = ""): PgComposite {
        val (resolvedOid, _) = registry.resolveOid(typeName, schema, connection.getSearchPath())
        return createComposite(resolvedOid)
    }

    fun createComposite(oid: UInt): PgComposite {
        val pgType = registry.types[oid] as? PgType.Composite
            ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = oid, details = "Type is not a composite or does not exist in TypeRegistry")
        val fields = pgType.attributes.map {
            ContainerField(rawValue = null, container = null, value = null)
        }
        return PgComposite(pgType, fields, registry)
    }

    fun createArray(typeName: String, schema: String = "", vararg dimensionSizes: Int): PgArray {
        val (resolvedOid, _) = registry.resolveOid(typeName, schema, connection.getSearchPath())
        return createArray(resolvedOid, *dimensionSizes)
    }

    fun createArray(oid: UInt, vararg dimensionSizes: Int): PgArray {
        require(dimensionSizes.isNotEmpty()) { "Array must have at least 1 dimension" }
        val arrayType = registry.types[oid] as? PgType.Array
            ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = oid, details = "Type is not an array or does not exist in TypeRegistry")
            
        val dimensions = dimensionSizes.map { ArrayDimension(it, 1) }
        val totalSize = dimensionSizes.fold(1) { acc, size -> acc * size }
        val values = MutableList<Any?>(totalSize) { null }
        return PgArray(arrayType.oid, arrayType.elementOid, dimensions, null, null, values, registry)
    }

    fun createRange(typeName: String, schema: String = "", lower: Any?, upper: Any?, flags: Byte): PgRange {
        val (resolvedOid, _) = registry.resolveOid(typeName, schema, connection.getSearchPath())
        return createRange(resolvedOid, lower, upper, flags)
    }

    fun createRange(oid: UInt, lower: Any?, upper: Any?, flags: Byte): PgRange {
        val rangeType = registry.types[oid] as? PgType.Range
            ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = oid, details = "Type is not a range or does not exist in TypeRegistry")
            
        val lowerField = lower?.let {
            ContainerField(null, it as? PgContainer, if (it !is PgContainer) it else null)
        }
        val upperField = upper?.let {
            ContainerField(null, it as? PgContainer, if (it !is PgContainer) it else null)
        }
        return PgRange(rangeType.oid, rangeType.subtypeOid, flags, lowerField, upperField, registry)
    }

    fun createMultirange(typeName: String, schema: String = "", vararg ranges: PgRange): PgMultirange {
        val (resolvedOid, _) = registry.resolveOid(typeName, schema, connection.getSearchPath())
        return createMultirange(resolvedOid, *ranges)
    }

    fun createMultirange(oid: UInt, vararg ranges: PgRange): PgMultirange {
        val multirangeType = registry.types[oid] as? PgType.Multirange
            ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = oid, details = "Type is not a multirange or does not exist in TypeRegistry")
        return PgMultirange(multirangeType.oid, multirangeType.rangeOid, ranges.toList())
    }
}
