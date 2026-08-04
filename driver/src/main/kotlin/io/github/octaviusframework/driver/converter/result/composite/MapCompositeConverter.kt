package io.github.octaviusframework.driver.converter.result.composite

import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.container.PgComposite
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf


class MapCompositeConverter : ResultConverter<PgComposite, Map<String, Any?>> {

    override val supportedSourceClass = PgComposite::class

    override fun canConvert(sourceClass: KClass<*>, expectedType: KType, sourceType: PgType, context: DeserializationContext): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == Map::class
    }

    override fun convert(source: PgComposite, expectedType: KType, sourceType: PgType, context: DeserializationContext): Map<String, Any?> {
        val valueType = expectedType.arguments.getOrNull(1)?.type ?: typeOf<Any?>()

        val result = mutableMapOf<String, Any?>()
        for ((index, attributeName) in source.attributeNames.withIndex()) {
            val rawValue = source.get<Any?>(index)
            val type = source.getAttributeType(index)
            result[attributeName] = if (rawValue == null) null else context.convert(rawValue, valueType, type, attributeName)
        }
        return result
    }
}