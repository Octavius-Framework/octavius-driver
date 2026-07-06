package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverterRegistry
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterMapper
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverterRegistry
import io.github.octaviusframework.driver.converter.result.mapper.ResultMapper
import io.github.octaviusframework.driver.type.TypeManager

/**
 * Base class for executing queries with parameters.
 */
@Suppress("UNCHECKED_CAST")
abstract class OctaviusQuery<T : OctaviusQuery<T>>(
    @PublishedApi internal val sql: String,
    @PublishedApi internal val queryExecutor: QueryExecutor,
    val typeManager: TypeManager
) {
    val typeRegistry = typeManager.registry
    val resultConverterRegistry = ResultConverterRegistry(parent = typeRegistry.converterRegistry)
    val parameterConverterRegistry = ParameterConverterRegistry(parent = typeRegistry.parameterConverterRegistry)
    @PublishedApi internal val localDeserializer = ResultMapper(resultConverterRegistry)
    protected val parameterMapper = ParameterMapper(parameterConverterRegistry, typeManager)
    protected val parameterSerializer = ParameterSerializer(typeManager, parameterMapper)

    fun registerResultConverter(converter: ResultConverter<*, *>): T {
        resultConverterRegistry.addConverter(converter)
        return this as T
    }

    fun registerParameterConverter(converter: ParameterConverter<*>): T {
        parameterConverterRegistry.addConverter(converter)
        return this as T
    }

    @PublishedApi internal fun serializeParameters(params: List<Any?>): Pair<List<Int>, List<ByteArray?>> {
        return parameterSerializer.serializeAll(params)
    }
}

