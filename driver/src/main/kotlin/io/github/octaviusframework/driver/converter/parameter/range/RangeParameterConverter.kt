package io.github.octaviusframework.driver.converter.parameter.range

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.exception.TypeException
import io.github.octaviusframework.driver.exception.TypeExceptionReason
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.Range
import io.github.octaviusframework.driver.type.UNRESOLVED_OID
import io.github.octaviusframework.driver.type.isKnownOid
import kotlin.reflect.KClass

internal object RangeParameterConverter : ParameterConverter<Range<*>> {

    override val supportedClass: KClass<Range<*>> = Range::class

    override fun convert(source: Range<*>, expectedOid: Int, context: SerializationContext): Any {
        val typeManager = context.typeManager

        val pgType = if (expectedOid.isKnownOid) {
            context.typeManager.typeDictionary.getPgType(expectedOid) as? PgType.Range
        } else {
            val elementOid = context.findConverterByClass(source.elementClass, UNRESOLVED_OID)?.getDefaultTypeName(context)
                ?.let { context.typeManager.resolveOid(it.name, it.schema, it.isArray) }
                ?.takeIf { it.isKnownOid }
                ?: typeManager.codecDictionary.getCodecByClass(source.elementClass)?.let { typeManager.codecDictionary.getOidForCodec(it) ?: typeManager.resolveOid(it.pgTypeName, it.pgSchema) }

            if (elementOid != null && elementOid.isKnownOid) {
                context.typeManager.typeDictionary.getRangeType(elementOid)
            } else null
        }

        if (pgType == null) {
            throw TypeException(
                TypeExceptionReason.TYPE_NOT_FOUND,
                details = "Cannot infer range type. The range is empty or bounds are null. Use explicit typing (e.g. .withPgType(...))."
            )
        }

        val elementOid = pgType.subtypeOid
        val boundConverter = context.findConverterByClass(source.elementClass, elementOid)

        val convertedLower = source.lowerBound?.let { boundConverter?.convert(it, elementOid, context) ?: it }
        val convertedUpper = source.upperBound?.let { boundConverter?.convert(it, elementOid, context) ?: it }

        if (source.isEmpty) {
            return context.typeManager.containers.createEmptyRange(pgType.oid)
        }

        return context.typeManager.containers.createRange(
            oid = pgType.oid,
            lower = convertedLower,
            upper = convertedUpper,
            isLowerInclusive = source.isLowerInclusive,
            isUpperInclusive = source.isUpperInclusive,
            isLowerInfinite = source.isLowerInfinite,
            isUpperInfinite = source.isUpperInfinite,
            isLowerNull = source.isLowerNull,
            isUpperNull = source.isUpperNull
        )
    }
}
