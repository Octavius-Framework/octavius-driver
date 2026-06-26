package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.mapping.parameter.ParameterConverter
import io.github.octaviusframework.driver.mapping.parameter.ParameterConverterRegistry
import io.github.octaviusframework.driver.mapping.result.ResultConverter
import io.github.octaviusframework.driver.mapping.result.ResultConverterRegistry
import io.github.octaviusframework.driver.mapping.result.ResultMapper
import io.github.octaviusframework.driver.type.TypeRegistry

/**
 * Bazowa klasa do wykonywania zapytań z parametrami.
 */
@Suppress("UNCHECKED_CAST")
abstract class OctaviusQuery<T : OctaviusQuery<T>>(
    protected val sql: String,
    protected val queryExecutor: QueryExecutor,
    val typeRegistry: TypeRegistry
) {
    val resultConverterRegistry = ResultConverterRegistry(parent = typeRegistry.converterRegistry)
    val parameterConverterRegistry = ParameterConverterRegistry(parent = typeRegistry.parameterConverterRegistry)
    protected val localDeserializer = ResultMapper(resultConverterRegistry)
    protected val parameterSerializer = ParameterSerializer(typeRegistry, parameterConverterRegistry)

    fun registerResultConverter(converter: ResultConverter<*>): T {
        resultConverterRegistry.addConverter(converter)
        return this as T
    }

    fun registerParameterConverter(converter: ParameterConverter<*>): T {
        parameterConverterRegistry.addConverter(converter)
        return this as T
    }

    protected fun serializeParameters(params: List<Any?>): Pair<List<UInt>, List<ByteArray?>> {
        return parameterSerializer.serializeAll(params)
    }
}
