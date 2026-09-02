package io.github.octaviusframework.driver.converter.result.composite

import io.github.octaviusframework.driver.container.PgComposite
import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf


internal object MapCompositeConverter : ResultConverter<PgComposite, Map<String, Any?>> {

    override val supportedSourceClass = PgComposite::class

    override fun canConvert(sourceClass: KClass<*>, expectedType: KType, sourceType: PgType, context: DeserializationContext): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == Map::class
    }

    override fun convert(source: PgComposite, expectedType: KType, sourceType: PgType, context: DeserializationContext): Map<String, Any?> {
        val valueType = expectedType.arguments.getOrNull(1)?.type ?: typeOf<Any?>()
        return source.attributesAsMap(valueType, context)
    }
}

/**
 * Every attribute of this composite, keyed by name, each one converted to [valueType].
 *
 * The loop both map-producing composite converters run. Nested values go through
 * [DeserializationContext.convert] rather than being read out by hand, which is what puts the attribute's
 * name on the `path` of anything raised below it - and what makes the recursion in [compositesAsMaps] fall
 * out of the value type rather than having to be written.
 *
 * @param valueType The Kotlin type each attribute is converted to.
 * @param context The context to convert nested values through.
 * @return The attributes, in the order the type declares them.
 */
internal fun PgComposite.attributesAsMap(valueType: KType, context: DeserializationContext): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()
    for ((index, attributeName) in attributeNames.withIndex()) {
        val rawValue = get<Any?>(index)
        result[attributeName] = if (rawValue == null) null else context.convert(rawValue, valueType, getAttributeOid(index), attributeName)
    }
    return result
}