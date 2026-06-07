package io.github.octaviusframework.container

import io.github.octaviusframework.types.PgType
import io.github.octaviusframework.jdbc.OctaviusConnection
import io.github.octaviusframework.exceptions.OctaviusTypeException
import io.github.octaviusframework.exceptions.TypeExceptionMessage

/**
 * A factory for manually creating empty (or pre-initialized) containers
 * from scratch, allowing the conversion of domain objects into Postgres structures before sending them to the database.
 */

/**
 * Creates a completely new, empty composite based on its type name (and optionally schema).
 */
fun OctaviusConnection.createComposite(typeName: String, schema: String = ""): PgComposite {
    val typeRegistry = this.typeRegistry
    val searchPath = this.getSearchPath()
    
    val (resolvedOid, _) = typeRegistry.resolveOid(typeName, schema, searchPath)
    
    val pgType = typeRegistry.types[resolvedOid] as? PgType.Composite
        ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = resolvedOid.toInt(), details = "Type $typeName is not a composite")
    
    val fields = pgType.attributes.map { 
        ContainerField(rawValue = null, container = null, value = null) 
    }
    return PgComposite(pgType, fields, typeRegistry)
}

/**
 * Creates a completely new, empty composite based on its OID.
 */
fun OctaviusConnection.createComposite(oid: UInt): PgComposite {
    val typeRegistry = this.typeRegistry
    val pgType = typeRegistry.types[oid] as? PgType.Composite
        ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = oid.toInt(), details = "Type is not a composite or does not exist in TypeRegistry")
    
    val fields = pgType.attributes.map { 
        ContainerField(rawValue = null, container = null, value = null) 
    }
    return PgComposite(pgType, fields, typeRegistry)
}

/**
 * Creates a new array based on the array type name (and optionally schema).
 */
fun OctaviusConnection.createArray(typeName: String, schema: String = "", vararg dimensionSizes: Int): PgArray {
    val (resolvedOid, _) = this.typeRegistry.resolveOid(typeName, schema, this.getSearchPath())
    return createArray(resolvedOid, *dimensionSizes)
}

/**
 * Creates a new array based on its OID.
 */
fun OctaviusConnection.createArray(oid: UInt, vararg dimensionSizes: Int): PgArray {
    require(dimensionSizes.isNotEmpty()) { "Array must have at least 1 dimension" }
    
    val typeRegistry = this.typeRegistry
    val arrayType = typeRegistry.types[oid] as? PgType.Array
        ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = oid.toInt(), details = "Type is not an array or does not exist in TypeRegistry")
        
    val dimensions = dimensionSizes.map { ArrayDimension(it, 1) }
    val totalSize = dimensionSizes.fold(1) { acc, size -> acc * size }
    val values = MutableList<Any?>(totalSize) { null }

    return PgArray(arrayType.oid, arrayType.elementOid, dimensions, true, null, null, values, typeRegistry)
}

/**
 * Creates a new range based on the range type name.
 */
fun OctaviusConnection.createRange(typeName: String, schema: String = "", lower: Any?, upper: Any?, flags: Byte): PgRange {
    val (resolvedOid, _) = this.typeRegistry.resolveOid(typeName, schema, this.getSearchPath())
    return createRange(resolvedOid, lower, upper, flags)
}

/**
 * Creates a new range based on its OID.
 */
fun OctaviusConnection.createRange(oid: UInt, lower: Any?, upper: Any?, flags: Byte): PgRange {
    val typeRegistry = this.typeRegistry
    val rangeType = typeRegistry.types[oid] as? PgType.Range
        ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = oid.toInt(), details = "Type is not a range or does not exist in TypeRegistry")
        
    val lowerField = lower?.let { ContainerField(null, if (it is PgContainer) it else null, if (it !is PgContainer) it else null) }
    val upperField = upper?.let { ContainerField(null, if (it is PgContainer) it else null, if (it !is PgContainer) it else null) }
    
    return PgRange(rangeType.oid, rangeType.subtypeOid, flags, lowerField, upperField, typeRegistry)
}

/**
 * Creates a new multirange based on the multirange type name.
 */
fun OctaviusConnection.createMultirange(typeName: String, schema: String = "", vararg ranges: PgRange): PgMultirange {
    val (resolvedOid, _) = this.typeRegistry.resolveOid(typeName, schema, this.getSearchPath())
    return createMultirange(resolvedOid, *ranges)
}

/**
 * Creates a new multirange based on its OID.
 */
fun OctaviusConnection.createMultirange(oid: UInt, vararg ranges: PgRange): PgMultirange {
    val typeRegistry = this.typeRegistry
    val multirangeType = typeRegistry.types[oid] as? PgType.Multirange
        ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = oid.toInt(), details = "Type is not a multirange or does not exist in TypeRegistry")

    return PgMultirange(multirangeType.oid, multirangeType.rangeOid, ranges.toList())
}
