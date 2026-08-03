package io.github.octaviusframework.driver.converter.parameter.range

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.exception.TypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.MultiRange
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.UNRESOLVED_OID
import io.github.octaviusframework.driver.type.isKnownOid
import kotlin.reflect.KClass

class MultiRangeParameterConverter : ParameterConverter<Any> {

    override val supportedClass: KClass<Any> = Any::class

    override fun canConvert(sourceClass: KClass<*>, expectedOid: Int, context: SerializationContext): Boolean {
        return MultiRange::class.java.isAssignableFrom(sourceClass.java)
    }

    override fun convert(source: Any, expectedOid: Int, context: SerializationContext): Any {
        val multiRange = source as MultiRange<*>
        val typeManager = context.typeManager
        val typeRegistry = typeManager.registry

        val pgType = if (expectedOid.isKnownOid) {
            context.typeManager.typeDictionary.getPgType(expectedOid) as? PgType.Multirange
        } else {
            val elementOid = context.findConverterByClass(multiRange.elementClass, UNRESOLVED_OID)?.getDefaultOid(context)
                ?.takeIf { it.isKnownOid }
                ?: typeRegistry.getCodecByClass(multiRange.elementClass)?.let { typeRegistry.getOidForCodec(it) ?: typeManager.resolveOid(it.pgTypeName, it.pgSchema) }

            if (elementOid != null && elementOid.isKnownOid) {
                val rangeType = context.typeManager.typeDictionary.getRangeType(elementOid)
                context.typeManager.typeDictionary.getMultirangeType(rangeType.oid)
            } else null
        }

        if (pgType == null) {
            throw TypeException(
                TypeExceptionMessage.TYPE_NOT_FOUND,
                details = "Cannot infer multirange type. The multirange is empty or bounds are null. Use explicit typing (e.g. .withPgType(...))."
            )
        }

        val rangeOid = pgType.rangeOid
        val rangePgType = context.typeManager.typeDictionary.getPgType(rangeOid) as PgType.Range
        val elementOid = rangePgType.subtypeOid
        val boundConverter = context.findConverterByClass(multiRange.elementClass, elementOid)

        val pgRanges = multiRange.ranges.map { range ->
            val convertedLower = range.lowerBound?.let { boundConverter?.convert(it, elementOid, context) ?: it }
            val convertedUpper = range.upperBound?.let { boundConverter?.convert(it, elementOid, context) ?: it }

            if (range.isEmpty) {
                context.typeManager.createEmptyRange(rangeOid)
            } else {
                context.typeManager.createRange(
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

        return context.typeManager.createMultirange(pgType.oid, *pgRanges.toTypedArray())
    }
}
