package io.github.octaviusframework.driver.converter.result.mapper

import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.registry.TypeManager
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Entry point of the read conversion chain: hands a decoded value to the converters and checks the result.
 *
 * One of these belongs to each query, over that query's own [ResultConverterRegistry]. It is what
 * [Row.get][io.github.octaviusframework.driver.row.Row.get] and the `fetchObject*` family call.
 *
 * @param registry The converters to consult, chained to the session's.
 * @param typeManager The session's type manager.
 */
class ResultMapper(
    registry: ResultConverterRegistry,
    typeManager: TypeManager
) {
    internal val context = DefaultDeserializationContext(registry, typeManager)

    /**
     * Converts a decoded database value to [expectedType].
     *
     * A `null` source is returned as `null` when [expectedType] allows it. Where no converter claims the
     * value, it is returned unchanged if it is already an instance of [expectedType]; otherwise this
     * fails rather than casting blindly. What a converter produces is checked against [expectedType]
     * too, so a converter that over-claims is named in the exception instead of surfacing as a
     * `ClassCastException` in the caller's own frame.
     *
     * @param T The type to return.
     * @param source The decoded value, or `null` for SQL `NULL`.
     * @param expectedType The Kotlin type wanted, generic arguments included.
     * @param sourceType The PostgreSQL type of the value.
     * @return The converted value.
     * @throws io.github.octaviusframework.driver.exception.MappingException
     *   `REQUIRED_ATTRIBUTE_MISSING` if [source] is `null` and [expectedType] is not nullable,
     *   `NO_CONVERTER_FOUND` if nothing can produce [expectedType],
     *   `CONVERSION_ERROR` if a converter failed or returned the wrong type.
     */
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
            if (kClass != null && !kClass.isInstance(converted)) {
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
