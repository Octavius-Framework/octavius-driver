package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason
import io.github.octaviusframework.driver.registry.TypeManager
import kotlin.reflect.KClass

/**
 * Entry point of the write conversion chain: hands a Kotlin value to the converters.
 *
 * One of these belongs to each query, over that query's own [ParameterConverterRegistry]. It is what
 * the parameter serializer calls for every value bound to a statement.
 *
 * @param registry The converters to consult, chained to the session's.
 * @param typeManager The session's type manager.
 */
internal class ParameterMapper(
    private val registry: ParameterConverterRegistry,
    private val typeManager: TypeManager
) {
    /**
     * Converts a Kotlin value into something a codec can encode.
     *
     * @param source The value being sent; `null` passes straight through.
     * @param expectedOid The OID the server expects, or `0` when it is not known.
     * @return A value a registered codec can encode, or `null`.
     * @throws MappingException
     *   `NO_CONVERTER_FOUND` if nothing claims the value and the target codec cannot accept its class,
     *   `CONVERSION_ERROR` if a converter failed.
     */
    fun convert(source: Any?, expectedOid: Int): Any? {
        if (source == null) return null
        val context = DefaultSerializationContext(registry, typeManager)
        return context.convert(source, expectedOid)
    }
}

internal class DefaultSerializationContext(
    private val registry: ParameterConverterRegistry,
    override val typeManager: TypeManager
) : SerializationContext {
    override fun convert(source: Any, expectedOid: Int, pathSegment: String?): Any {
        try {
            return registry.convert(source, expectedOid, this)
        } catch (e: MappingException) {
            if (pathSegment != null) e.path.add(pathSegment)
            throw e
        } catch (e: Exception) {
            val ex = MappingException(
                MappingExceptionReason.CONVERSION_ERROR,
                details = "Error during parameter serialization: ${e.message}", 
                cause = e
            )
            if (pathSegment != null) ex.path.add(pathSegment)
            throw ex
        }
    }

    override fun findConverter(source: Any, expectedOid: Int): ParameterConverter<Any>? {
        return registry.findConverter(source, expectedOid, this)
    }

    override fun findConverterByClass(sourceClass: KClass<*>, expectedOid: Int): ParameterConverter<Any>? {
        return registry.findConverterByClass(sourceClass, expectedOid, this)
    }
}

