package io.github.octaviusframework.driver.converter.parameter.range

import io.github.octaviusframework.driver.type.UNRESOLVED_OID
import io.github.octaviusframework.driver.type.isKnownOid

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.exception.TypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.type.Range

import kotlin.reflect.KClass

class RangeParameterConverter : ParameterConverter<Any> {
    override val supportedClass: KClass<Any> = Any::class

    override fun canConvert(sourceClass: KClass<*>, expectedOid: Int, typeManager: TypeManager): Boolean {
        return Range::class.java.isAssignableFrom(sourceClass.java)
    }

    override fun convert(source: Any, expectedOid: Int, context: SerializationContext, typeManager: TypeManager): Any {
        val range = source as Range<*>
        val typeRegistry = typeManager.registry

        val pgType = if (expectedOid.isKnownOid) {
            typeRegistry.types[expectedOid] as? PgType.Range
        } else {
            val nonNullBound = range.lowerBound ?: range.upperBound
            if (nonNullBound != null) {
                val converted = context.convert(nonNullBound, UNRESOLVED_OID)
                val elementOid = typeRegistry.getCodecByClass(converted?.let { it::class } ?: Any::class)?.oid
                if (elementOid != null) {
                    typeRegistry.types.values.firstOrNull { it is PgType.Range && it.subtypeOid == elementOid } as? PgType.Range
                } else null
            } else null
        }

        if (pgType == null) {
            throw TypeException(
                TypeExceptionMessage.TYPE_NOT_FOUND,
                details = "Cannot infer range type. The range is empty or bounds are null. Use explicit typing (e.g. .withPgType(...))."
            )
        }

        val elementOid = pgType.subtypeOid

        val convertedLower = range.lowerBound?.let { context.convert(it, elementOid) }
        val convertedUpper = range.upperBound?.let { context.convert(it, elementOid) }

        if (range.isEmpty) {
            return typeManager.createEmptyRange(pgType.oid)
        }

        return typeManager.createRange(
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
