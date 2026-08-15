package io.github.octaviusframework.driver.converter.result.mapper

import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.registry.TypeManager
import kotlin.reflect.KClass
import kotlin.reflect.KType

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
                throw MappingException(MappingExceptionReason.REQUIRED_ATTRIBUTE_MISSING, "Cannot deserialize null to non-nullable type $expectedType")
            }
            @Suppress("UNCHECKED_CAST")
            return null as T
        }

        val kClass = expectedType.classifier as? KClass<*>

        val converter = registry.findConverter(source::class, expectedType, sourceType, this)
        if (converter != null) {
            val converted = converter.convert(source, expectedType, sourceType, this)

            // The cast below is erased, so a converter that answered canConvert() and then produced
            // something else would sail through here and blow up as a ClassCastException in the
            // caller's own frame - with nothing in the stack naming the converter responsible.
            // Checking here is what turns that into an exception that can say who did it.
            if (converted != null && kClass != null && !kClass.isInstance(converted)) {
                throw MappingException(
                    MappingExceptionReason.CONVERSION_ERROR,
                    details = "Converter ${converter::class.qualifiedName ?: converter::class} returned " +
                            "${converted::class.qualifiedName ?: converted::class} but $expectedType was expected. " +
                            "A converter whose canConvert() accepts more than it can produce is the usual cause."
                )
            }

            @Suppress("UNCHECKED_CAST")
            return converted as T
        }

        // Fallback: if the source is already of the appropriate type, just cast it
        // np. String -> String
        if (kClass != null && kClass.isInstance(source)) {
            @Suppress("UNCHECKED_CAST")
            return source as T
        }

            throw MappingException(MappingExceptionReason.NO_CONVERTER_FOUND, "No converter found for source ${source::class} and expected type $expectedType")
        } catch (e: MappingException) {
            if (pathSegment != null) e.path.add(pathSegment)
            throw e
        } catch (e: Exception) {
            val ex = MappingException(
                MappingExceptionReason.CONVERSION_ERROR,
                details = "Error during result deserialization: ${e.message}", 
                cause = e
            )
            if (pathSegment != null) ex.path.add(pathSegment)
            throw ex
        }
    }

    override fun findConverter(sourceClass: KClass<*>, expectedType: KType, sourceType: PgType): ResultConverter<Any, *>? {
        return registry.findConverter(sourceClass, expectedType, sourceType, this)
    }
}
