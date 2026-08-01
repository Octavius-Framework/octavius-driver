package io.github.octaviusframework.driver.converter.result.range

import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.container.PgMultirange
import io.github.octaviusframework.driver.exception.OctaviusInternalException
import io.github.octaviusframework.driver.type.MultiRange
import io.github.octaviusframework.driver.type.Range
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class MultiRangeResultConverter : ResultConverter<PgMultirange, MultiRange<*>> {
    override val supportedSourceClass = PgMultirange::class

    override fun canConvert(source: PgMultirange, expectedType: KType, sourceType: PgType): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == MultiRange::class || kClass == Any::class
    }

    override fun convert(source: PgMultirange, expectedType: KType, context: DeserializationContext, sourceType: PgType): MultiRange<*> {
        val ktElementType = expectedType.arguments.firstOrNull()?.type 
            ?: typeOf<Any>()

        if (source.ranges.isEmpty()) {
            return MultiRange<Any>(emptyList())
        }

        val typeRegistry = source.ranges.first().typeRegistry
        val elementOid = source.ranges.first().elementOid
        val pgElementType = typeRegistry.types[elementOid]
            ?: throw OctaviusInternalException()

        val convertedRanges = source.ranges.map { pgRange ->
            val lower = pgRange.lowerBound?.let { context.convert<Any>(it, ktElementType, pgElementType) }
            val upper = pgRange.upperBound?.let { context.convert<Any>(it, ktElementType, pgElementType) }

            Range(
                lowerBound = lower,
                upperBound = upper,
                isLowerInclusive = pgRange.isLowerInclusive,
                isUpperInclusive = pgRange.isUpperInclusive,
                isLowerInfinite = pgRange.isLowerInfinite,
                isUpperInfinite = pgRange.isUpperInfinite,
                isLowerNull = pgRange.isLowerNull,
                isUpperNull = pgRange.isUpperNull,
                isEmpty = pgRange.isEmpty
            )
        }

        return MultiRange(convertedRanges)
    }
}
