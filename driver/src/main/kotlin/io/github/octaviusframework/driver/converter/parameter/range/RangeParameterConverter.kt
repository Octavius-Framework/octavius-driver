package io.github.octaviusframework.driver.converter.parameter.range

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.exception.TypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.Range
import io.github.octaviusframework.driver.type.UNRESOLVED_OID
import io.github.octaviusframework.driver.type.isKnownOid
import kotlin.reflect.KClass

class RangeParameterConverter : ParameterConverter<Any> {

    override val supportedClass: KClass<Any> = Any::class

    override fun canConvert(sourceClass: KClass<*>, expectedOid: Int, context: SerializationContext): Boolean {
        return Range::class.java.isAssignableFrom(sourceClass.java)
    }

    override fun convert(source: Any, expectedOid: Int, context: SerializationContext): Any {
        val range = source as Range<*>
        val typeRegistry = context.typeManager.registry

        val pgType = if (expectedOid.isKnownOid) {
            typeRegistry.types[expectedOid] as? PgType.Range
        } else {
            val elementOid = context.findConverterByClass(range.elementClass, UNRESOLVED_OID)?.getDefaultOid(context)
                ?.takeIf { it.isKnownOid }
                ?: typeRegistry.getCodecByClass(range.elementClass)?.let { typeRegistry.getOidForCodec(it) ?: context.typeManager.resolveOid(it.pgTypeName, it.pgSchema) }

            if (elementOid != null && elementOid.isKnownOid) {
                typeRegistry.types.values.firstOrNull { it is PgType.Range && it.subtypeOid == elementOid } as? PgType.Range
            } else null
        }

        if (pgType == null) {
            throw TypeException(
                TypeExceptionMessage.TYPE_NOT_FOUND,
                details = "Cannot infer range type. The range is empty or bounds are null. Use explicit typing (e.g. .withPgType(...))."
            )
        }

        val elementOid = pgType.subtypeOid
        val boundConverter = context.findConverterByClass(range.elementClass, elementOid)

        val convertedLower = range.lowerBound?.let { boundConverter?.convert(it, elementOid, context) ?: it }
        val convertedUpper = range.upperBound?.let { boundConverter?.convert(it, elementOid, context) ?: it }

        if (range.isEmpty) {
            return context.typeManager.createEmptyRange(pgType.oid)
        }

        return context.typeManager.createRange(
            oid = pgType.oid,
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
