package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverterRegistry
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterMapper
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverterRegistry
import io.github.octaviusframework.driver.converter.result.mapper.ResultMapper
import io.github.octaviusframework.driver.exception.OctaviusException
import io.github.octaviusframework.driver.exception.QueryContext
import io.github.octaviusframework.driver.type.TypeManager

/**
 * Base class for executing queries with parameters.
 *
 * This class provides the foundational state and utilities needed for managing 
 * type conversion registries and mappings localized to a single query instance.
 *
 * @param T The concrete type of the query (used for fluent API return types).
 */
@Suppress("UNCHECKED_CAST")
abstract class OctaviusQuery<T : OctaviusQuery<T>> internal constructor(
    @PublishedApi internal val sql: String,
    @PublishedApi internal val queryExecutor: QueryExecutor,
    val typeManager: TypeManager
) {
    val resultConverterRegistry = ResultConverterRegistry(parent = typeManager.converterRegistry.resultConverterRegistry)
    val parameterConverterRegistry = ParameterConverterRegistry(parent = typeManager.converterRegistry.parameterConverterRegistry)
    @PublishedApi internal val resultMapper = ResultMapper(resultConverterRegistry, typeManager)
    protected val parameterMapper = ParameterMapper(parameterConverterRegistry, typeManager)
    @PublishedApi internal val parameterSerializer = ParameterSerializer(typeManager, parameterMapper)

    fun registerResultConverter(converter: ResultConverter<*, *>): T {
        resultConverterRegistry.addConverter(converter)
        return this as T
    }

    fun registerParameterConverter(converter: ParameterConverter<*>): T {
        parameterConverterRegistry.addConverter(converter)
        return this as T
    }

    @PublishedApi
    internal inline fun <R> withQueryContext(
        sql: String,
        crossinline paramsProvider: () -> Map<String, Any?>,
        crossinline dbSqlProvider: () -> String? = { null },
        crossinline dbParamsProvider: () -> List<Any?>? = { null },
        block: () -> R
    ): R {
        try {
            return block()
        } catch (e: OctaviusException) {
            if (e.queryContext == null) {
                e.queryContext = QueryContext(sql, paramsProvider(), dbSqlProvider(), dbParamsProvider())
            }
            throw e
        }
    }
}

