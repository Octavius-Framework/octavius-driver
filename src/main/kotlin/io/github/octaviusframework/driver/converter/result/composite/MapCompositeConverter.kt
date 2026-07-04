package io.github.octaviusframework.driver.converter.result.composite

import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.container.PgComposite
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf


class MapCompositeConverter : ResultConverter<Map<String, Any?>> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        if (source !is PgComposite) return false
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == Map::class
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType): Map<String, Any?> {
        source as PgComposite
        val valueType = expectedType.arguments.getOrNull(1)?.type ?: typeOf<Any?>()

        val result = mutableMapOf<String, Any?>()
        for ((index, attributeName) in source.attributeNames.withIndex()) {
            val rawValue = source.get<Any?>(index)
            val oid = source.type.attributes.values.toList().getOrNull(index)!!
            val type = source.typeRegistry.types[oid]!!
            result[attributeName] = if (rawValue == null) null else context.convert<Any?>(rawValue, valueType, type)
        }
        return result
    }
}