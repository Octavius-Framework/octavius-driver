package io.github.octaviusframework.driver.converter.parameter.range

import io.github.octaviusframework.driver.type.UNRESOLVED_OID
import io.github.octaviusframework.driver.type.isKnownOid

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.exception.OctaviusInternalException
import io.github.octaviusframework.driver.exception.TypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.type.MultiRange

import kotlin.reflect.KClass

class MultiRangeParameterConverter : ParameterConverter<Any> {
    override val supportedClass: KClass<Any> = Any::class

    override fun canConvert(sourceClass: KClass<*>, expectedOid: Int, typeManager: TypeManager): Boolean {
        return MultiRange::class.java.isAssignableFrom(sourceClass.java)
    }

    override fun convert(source: Any, expectedOid: Int, context: SerializationContext, typeManager: TypeManager): Any {
        val multiRange = source as MultiRange<*>
        val typeRegistry = typeManager.registry

        val pgType = if (expectedOid.isKnownOid) {
            typeRegistry.types[expectedOid] as? PgType.Multirange
        } else {
            val elementOid = context.findConverterByClass(multiRange.elementClass, UNRESOLVED_OID)?.getDefaultOid(typeManager)
                ?.takeIf { it.isKnownOid }
                ?: typeRegistry.getCodecByClass(multiRange.elementClass)?.let { typeRegistry.getOidForCodec(it) ?: typeManager.resolveOid(it.pgTypeName, it.pgSchema) }

            if (elementOid != null && elementOid.isKnownOid) {
                val rangeType = typeRegistry.types.values.firstOrNull { it is PgType.Range && it.subtypeOid == elementOid } as? PgType.Range
                if (rangeType != null) {
                    typeRegistry.types.values.firstOrNull { it is PgType.Multirange && it.rangeOid == rangeType.oid } as? PgType.Multirange
                } else null
            } else null
        }

        if (pgType == null) {
            throw TypeException(
                TypeExceptionMessage.TYPE_NOT_FOUND,
                details = "Cannot infer multirange type. The multirange is empty or bounds are null. Use explicit typing (e.g. .withPgType(...))."
            )
        }

        val rangeOid = pgType.rangeOid
        val rangePgType = typeRegistry.types[rangeOid] as? PgType.Range ?: throw OctaviusInternalException()
        val elementOid = rangePgType.subtypeOid
        val boundConverter = context.findConverterByClass(multiRange.elementClass, elementOid)

        val pgRanges = multiRange.ranges.map { range ->
            val convertedLower = range.lowerBound?.let { boundConverter?.convert(it, elementOid, context, typeManager) ?: it }
            val convertedUpper = range.upperBound?.let { boundConverter?.convert(it, elementOid, context, typeManager) ?: it }

            if (range.isEmpty) {
                typeManager.createEmptyRange(rangeOid)
            } else {
                typeManager.createRange(
                    oid = rangeOid,
                    lower = convertedLower,
                    upper = convertedUpper,
                    isLowerInclusive = range.isLowerInclusive,
                    isUpperInclusive = range.isUpperInclusive,
                    isLowerInfinite = range.isLowerInfinite,
                    isUpperInfinite = range.isUpperInfinite,
                    isLowerNull = range.isLowerNull,
                    isUpperNull = range.isUpperNull
                )
            }
        }

        return typeManager.createMultirange(pgType.oid, *pgRanges.toTypedArray())
    }
}
