package io.github.octaviusframework.driver.converter.result.mapper

import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KType

class ResultMapper(
    registry: ResultConverterRegistry
) {
    internal val context = DefaultDeserializationContext(registry)
    fun <T> deserialize(source: Any?, expectedType: KType, sourceType: PgType): T {
        return context.convert(source, expectedType, sourceType)
    }
}

internal class DefaultDeserializationContext(
    private val registry: ResultConverterRegistry
) : DeserializationContext {
    override fun <T> convert(source: Any?, expectedType: KType, sourceType: PgType): T {
        if (source == null) {
            if (!expectedType.isMarkedNullable) {
                throw IllegalArgumentException("Cannot deserialize null to non-nullable type $expectedType")
            }
            @Suppress("UNCHECKED_CAST")
            return null as T
        }

        val converter = registry.findConverter(source, expectedType, sourceType)
        if (converter != null) {
            @Suppress("UNCHECKED_CAST")
            return converter.convert(source, expectedType, this, sourceType) as T
        }

        // Fallback: if the source is already of the appropriate type, just cast it
        // np. String -> String
        val kClass = expectedType.classifier as? KClass<*>
        if (kClass != null && kClass.isInstance(source)) {
            @Suppress("UNCHECKED_CAST")
            return source as T
        }

        throw IllegalArgumentException("No converter found for source ${source::class} and expected type $expectedType")
    }

    override fun findConverter(source: Any, expectedType: KType, sourceType: PgType): ResultConverter<Any, *>? {
        return registry.findConverter(source, expectedType, sourceType)
    }
}
