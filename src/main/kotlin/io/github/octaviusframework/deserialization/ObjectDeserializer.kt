package io.github.octaviusframework.deserialization

import kotlin.reflect.KType
import kotlin.reflect.typeOf

class ObjectDeserializer(
    private val globalRegistry: ConverterRegistry
) {
    fun <T> deserialize(source: Any?, expectedType: KType, localRegistry: ConverterRegistry? = null): T? {
        if (source == null) return null

        val context = DefaultDeserializationContext(
            localRegistry ?: ConverterRegistry(globalRegistry)
        )
        return context.convert(source, expectedType)
    }
}

internal class DefaultDeserializationContext(
    private val registry: ConverterRegistry
) : DeserializationContext {
    override fun <T> convert(source: Any?, expectedType: KType): T? {
        if (source == null) return null

        val converter = registry.findConverter(source, expectedType)
        if (converter != null) {
            @Suppress("UNCHECKED_CAST")
            return converter.convert(source, expectedType, this) as T?
        }

        // Fallback: jeśli źródło jest już odpowiedniego typu, po prostu rzutujemy
        // np. String -> String
        val kClass = expectedType.classifier as? kotlin.reflect.KClass<*>
        if (kClass != null && kClass.isInstance(source)) {
            @Suppress("UNCHECKED_CAST")
            return source as T
        }

        throw IllegalArgumentException("No converter found for source ${source::class} and expected type $expectedType")
    }
}
