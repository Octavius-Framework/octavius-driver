package io.github.octaviusframework.deserialization

import io.github.octaviusframework.container.PgComposite
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import io.github.octaviusframework.types.PgType

class MapCompositeConverter : PgConverter<Map<String, Any?>> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType?): Boolean {
        if (source !is PgComposite) return false
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == Map::class
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType?): Map<String, Any?> {
        source as PgComposite
        val valueType = expectedType.arguments.getOrNull(1)?.type ?: typeOf<Any?>()
        
        val result = mutableMapOf<String, Any?>()
        for ((index, attributeName) in source.attributeNames.withIndex()) {
            val rawValue = source.get<Any>(index)
            val oid = source.type.attributes.values.toList().getOrNull(index)
            val type = if (oid != null) source.typeRegistry.types[oid] else null
            result[attributeName] = if (rawValue == null) null else context.convert<Any>(rawValue, valueType, type)
        }
        return result
    }
}
