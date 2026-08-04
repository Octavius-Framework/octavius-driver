package io.github.octaviusframework.driver.converter.result.mapper

import io.github.octaviusframework.driver.exception.MappingExceptionMessage
import io.github.octaviusframework.driver.exception.MappingException

import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KType

import io.github.octaviusframework.driver.type.TypeManager

class ResultMapper(
    registry: ResultConverterRegistry,
    typeManager: TypeManager
) {
    internal val context = DefaultDeserializationContext(registry, typeManager)
    fun <T> deserialize(source: Any?, expectedType: KType, sourceType: PgType): T {
        return context.convert(source, expectedType, sourceType)
    }
}

internal class DefaultDeserializationContext(
    private val registry: ResultConverterRegistry,
    override val typeManager: TypeManager
) : DeserializationContext {
    override fun <T> convert(source: Any?, expectedType: KType, sourceType: PgType, pathSegment: String?): T {
        try {
        if (source == null) {
            if (!expectedType.isMarkedNullable) {
                throw IllegalArgumentException("Cannot deserialize null to non-nullable type $expectedType")
            }
            @Suppress("UNCHECKED_CAST")
            return null as T
        }

        val converter = registry.findConverter(source::class, expectedType, sourceType, this)
        if (converter != null) {
            @Suppress("UNCHECKED_CAST")
            return converter.convert(source, expectedType, sourceType, this) as T
        }

        // Fallback: if the source is already of the appropriate type, just cast it
        // np. String -> String
        val kClass = expectedType.classifier as? KClass<*>
        if (kClass != null && kClass.isInstance(source)) {
            @Suppress("UNCHECKED_CAST")
            return source as T
        }

            throw MappingException(MappingExceptionMessage.NO_CONVERTER_FOUND, "No converter found for source ${source::class} and expected type $expectedType")
        } catch (e: MappingException) {
            if (pathSegment != null) e.path.add(pathSegment)
            throw e
        }
    }

    override fun findConverter(sourceClass: KClass<*>, expectedType: KType, sourceType: PgType): ResultConverter<Any, *>? {
        return registry.findConverter(sourceClass, expectedType, sourceType, this)
    }
}
