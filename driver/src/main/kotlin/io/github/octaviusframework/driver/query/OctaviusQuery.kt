package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverterRegistry
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterMapper
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverterRegistry
import io.github.octaviusframework.driver.converter.result.mapper.ResultMapper
import io.github.octaviusframework.driver.exception.OctaviusException
import io.github.octaviusframework.driver.execution.QueryExecutor
import io.github.octaviusframework.driver.execution.ParameterSerializer
import io.github.octaviusframework.driver.registry.TypeManager

/**
 * Base class for executing queries with parameters.
 *
 * This class provides the foundational state and utilities needed for managing
 * type conversion registries and mappings localized to a single query instance.
 *
 * Each query gets converter registries of its own, chained to the session's. A converter registered
 * here is consulted before the session's and is discarded with the query, which is what makes a
 * one-off mapping possible without disturbing anything else on the connection.
 *
 * @param T The concrete type of the query (used for fluent API return types).
 * @property typeManager The session's type manager, resolving OIDs and holding the parent registries.
 */
@Suppress("UNCHECKED_CAST")
abstract class OctaviusQuery<T : OctaviusQuery<T>> internal constructor(
    @PublishedApi internal val sql: String,
    @PublishedApi internal val queryExecutor: QueryExecutor,
    internal val typeManager: TypeManager
) {
    /**
     * Result converters local to this query, chained to the session's. Converters here are tried first.
     */
    val resultConverterRegistry = ResultConverterRegistry(parent = typeManager.converterRegistry.resultConverterRegistry)

    /**
     * Parameter converters local to this query, chained to the session's. Converters here are tried first.
     */
    val parameterConverterRegistry = ParameterConverterRegistry(parent = typeManager.converterRegistry.parameterConverterRegistry)
    @PublishedApi internal val resultMapper = ResultMapper(resultConverterRegistry, typeManager)
    internal val parameterMapper = ParameterMapper(parameterConverterRegistry, typeManager)
    @PublishedApi internal val parameterSerializer = ParameterSerializer(typeManager, parameterMapper)

    /**
     * Registers a [ResultConverter] for this query only, ahead of any the session already holds.
     *
     * Later registrations take priority over earlier ones.
     *
     * @param converter The converter to register.
     * @return This query, for chaining.
     */
    fun registerResultConverter(converter: ResultConverter<*, *>): T {
        resultConverterRegistry.addConverter(converter)
        return this as T
    }

    /**
     * Registers a [ParameterConverter] for this query only, ahead of any the session already holds.
     *
     * Later registrations take priority over earlier ones.
     *
     * @param converter The converter to register.
     * @return This query, for chaining.
     */
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

    /**
     * Executes a statement with no result and no row count — DDL, `SET`, administrative commands.
     *
     * This method speaks the Simple Query Protocol and takes **no parameters at all**: the SQL is sent
     * exactly as written, so any placeholders (like `$1` or `@name`) in it reach the server as literal text 
     * rather than being substituted. It accepts a whole script of statements separated by `;` in a single 
     * round trip, which PostgreSQL wraps in an implicit transaction.
     *
     * @throws io.github.octaviusframework.driver.exception.InvalidOperationException `UNEXPECTED_RESULT` if any statement in the SQL returned rows.
     */
    fun execute() {
        withQueryContext(sql, { emptyMap() }) {
            queryExecutor.execute(sql)
        }
    }
}

