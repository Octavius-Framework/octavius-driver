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

class MultiRangeParameterConverter : ParameterConverter<Any> {
    override fun canConvert(source: Any, expectedOid: Int, typeManager: TypeManager): Boolean {
        return source is MultiRange<*>
    }

    override fun convert(source: Any, expectedOid: Int, context: SerializationContext, typeManager: TypeManager): Any {
        val multiRange = source as MultiRange<*>
        val typeRegistry = typeManager.registry

        val pgType = if (expectedOid.isKnownOid) {
            typeRegistry.types[expectedOid] as? PgType.Multirange
        } else {
            val firstRange = multiRange.ranges.firstOrNull { it.lowerBound != null || it.upperBound != null }
            val nonNullBound = firstRange?.lowerBound ?: firstRange?.upperBound
            if (nonNullBound != null) {
                val converted = context.convert(nonNullBound, UNRESOLVED_OID)
                val elementOid = typeRegistry.getCodecByClass(converted?.let { it::class } ?: Any::class)?.oid
                if (elementOid != null) {
                    val rangeType = typeRegistry.types.values.firstOrNull { it is PgType.Range && it.subtypeOid == elementOid } as? PgType.Range
                    if (rangeType != null) {
                        typeRegistry.types.values.firstOrNull { it is PgType.Multirange && it.rangeOid == rangeType.oid } as? PgType.Multirange
                    } else null
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

        val pgRanges = multiRange.ranges.map { range ->
            val convertedLower = range.lowerBound?.let { context.convert(it, elementOid) }
            val convertedUpper = range.upperBound?.let { context.convert(it, elementOid) }

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
