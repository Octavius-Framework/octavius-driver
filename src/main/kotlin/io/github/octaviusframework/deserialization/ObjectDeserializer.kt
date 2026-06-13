package io.github.octaviusframework.deserialization

import kotlin.reflect.KType
import kotlin.reflect.typeOf

import io.github.octaviusframework.types.PgType
import kotlin.reflect.KClass

class ObjectDeserializer(
    private val registry: ConverterRegistry
) {
    fun <T> deserialize(source: Any?, expectedType: KType, sourceType: PgType): T {
        val context = DefaultDeserializationContext(registry)
        return context.convert(source, expectedType, sourceType)
    }
}

internal class DefaultDeserializationContext(
    private val registry: ConverterRegistry
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

        // Fallback: jeśli źródło jest już odpowiedniego typu, po prostu rzutujemy
        // np. String -> String
        val kClass = expectedType.classifier as? KClass<*>
        if (kClass != null && kClass.isInstance(source)) {
            @Suppress("UNCHECKED_CAST")
            return source as T
        }

        throw IllegalArgumentException("No converter found for source ${source::class} and expected type $expectedType")
    }
}
