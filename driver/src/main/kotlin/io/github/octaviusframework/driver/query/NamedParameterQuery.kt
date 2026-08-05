package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.exception.StatementException
import io.github.octaviusframework.driver.exception.StatementExceptionReason

import io.github.octaviusframework.driver.row.Row
import io.github.octaviusframework.driver.row.get
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeManager
import kotlin.reflect.typeOf

/**
 * Represents a query that accepts named parameters (e.g., `@name`, `@id`).
 *
 * `NamedParameterQuery` parses the SQL to extract named parameters and maps them to positional
 * parameters before delegating execution to the underlying [QueryExecutor]. 
 * It provides various execution methods to fetch rows, map results to Kotlin objects, 
 * extract single fields, or perform data modifications (updates).
 */
class NamedParameterQuery internal constructor(
    sql: String,
    queryExecutor: QueryExecutor,
    typeManager: TypeManager
) : OctaviusQuery<NamedParameterQuery>(sql, queryExecutor, typeManager) {

    @PublishedApi
    internal fun prepareNamedQuery(params: Map<String, Any?>): Pair<String, List<Any?>> {
        val parsed = SqlParameterParser.parse(sql)
        val listParams = parsed.paramNames.map {
            if (!params.containsKey(it)) {
                throw StatementException(StatementExceptionReason.MISSING_NAMED_PARAMETER, "Missing parameter: $it")
            }
            params[it]
        }
        return Pair(parsed.transformedSql, listParams)
    }

    @PublishedApi
    internal inline fun <R> withPreparedQuery(
        params: Map<String, Any?>,
        block: (String, List<Any?>) -> R
    ): R {
        var transformedSql: String? = null
        var listParams: List<Any?>? = null
        return withQueryContext(sql, { params }, { transformedSql }, { listParams }) {
            val (tSql, lParams) = prepareNamedQuery(params)
            transformedSql = tSql
            listParams = lParams
            block(tSql, lParams)
        }
    }

    //--------------------------------------------Row-based Methods-----------------------------------------------------

    fun fetchRows(params: Map<String, Any?>): List<Row> {
        return withPreparedQuery(params) { transformedSql, listParams ->
            queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper)
        }
    }

    fun fetchRows(vararg params: Pair<String, Any?>): List<Row> = fetchRows(params.toMap())

    fun fetchRow(params: Map<String, Any?>): Row? {
        return withPreparedQuery(params) { transformedSql, listParams ->
            val rows = queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper, maxRows = 2)
            if (rows.size > 1) throw StatementException(
                StatementExceptionReason.INCORRECT_RESULT_SIZE,
                details = "Expected 0 or 1, got at least 2 rows."
            )
            rows.firstOrNull()
        }
    }

    fun fetchRow(vararg params: Pair<String, Any?>): Row? = fetchRow(params.toMap())

    fun fetchRowStrict(params: Map<String, Any?>): Row {
        return withPreparedQuery(params) { transformedSql, listParams ->
            val rows = queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper, maxRows = 2)
            if (rows.isEmpty()) throw StatementException(
                StatementExceptionReason.INCORRECT_RESULT_SIZE,
                details = "Expected 1, got 0 rows."
            )
            if (rows.size > 1) throw StatementException(
                StatementExceptionReason.INCORRECT_RESULT_SIZE,
                details = "Expected 1, got at least 2 rows."
            )
            rows.first()
        }
    }

    fun fetchRowStrict(vararg params: Pair<String, Any?>): Row = fetchRowStrict(params.toMap())

    fun forEachRow(params: Map<String, Any?>, fetchSize: Int, block: (Row) -> Unit) {
        withPreparedQuery(params) { transformedSql, listParams ->
            queryExecutor.queryForEach(transformedSql, listParams, parameterSerializer, resultMapper, fetchSize, { it }, block)
        }
    }

    fun forEachRow(vararg params: Pair<String, Any?>, fetchSize: Int, block: (Row) -> Unit) = forEachRow(params.toMap(), fetchSize, block)

    //----------------------------------------Object Mapping Methods----------------------------------------------------

    inline fun <reified T : Any> fetchObjects(params: Map<String, Any?>): List<T> {
        val targetType = typeOf<T>()
        val recordType = PgType.Record
        return withPreparedQuery(params) { transformedSql, listParams ->
            queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper) {
                resultMapper.deserialize(it, targetType, recordType)
            }
        }
    }

    inline fun <reified T : Any> fetchObjects(vararg params: Pair<String, Any?>): List<T> = fetchObjects(params.toMap())

    inline fun <reified T: Any> fetchObject(params: Map<String, Any?>): T? {
        val targetType = typeOf<T>()
        val recordType = PgType.Record
        return withPreparedQuery(params) { transformedSql, listParams ->
            val rows = queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper, maxRows = 2) {
                resultMapper.deserialize<T>(it, targetType, recordType)
            }
            if (rows.size > 1) throw StatementException(
                StatementExceptionReason.INCORRECT_RESULT_SIZE,
                details = "Expected 0 or 1, got at least 2 rows."
            )
            rows.firstOrNull()
        }
    }

    inline fun <reified T: Any> fetchObject(vararg params: Pair<String, Any?>): T? = fetchObject(params.toMap())

    inline fun <reified T : Any> fetchObjectStrict(params: Map<String, Any?>): T {
        val targetType = typeOf<T>()
        val recordType = PgType.Record
        return withPreparedQuery(params) { transformedSql, listParams ->
            val rows = queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper, maxRows = 2) {
                resultMapper.deserialize<T>(it, targetType, recordType)
            }
            if (rows.size > 1) throw StatementException(
                StatementExceptionReason.INCORRECT_RESULT_SIZE,
                details = "Expected 1, got at least 2 rows."
            )
            if (rows.isEmpty()) throw StatementException(
                StatementExceptionReason.INCORRECT_RESULT_SIZE,
                details = "Expected 1, got 0 rows."
            )
            rows.first()
        }
    }

    inline fun <reified T: Any> fetchObjectStrict(vararg params: Pair<String, Any?>): T = fetchObjectStrict(params.toMap())
    
    inline fun <reified T : Any> forEachObject(params: Map<String, Any?>, fetchSize: Int, crossinline block: (T) -> Unit) {
        val targetType = typeOf<T>()
        val recordType = PgType.Record
        withPreparedQuery(params) { transformedSql, listParams ->
            queryExecutor.queryForEach(transformedSql, listParams, parameterSerializer, resultMapper, fetchSize, {
                resultMapper.deserialize<T>(it, targetType, recordType)
            }, { block(it) })
        }
    }

    inline fun <reified T : Any> forEachObject(vararg params: Pair<String, Any?>, fetchSize: Int, crossinline block: (T) -> Unit) = forEachObject(params.toMap(), fetchSize, block)

    //-----------------------------------------Single Column Methods----------------------------------------------------

    inline fun <reified T> fetchFields(params: Map<String, Any?>): List<T> {
        val targetType = typeOf<T>()
        return withPreparedQuery(params) { transformedSql, listParams ->
            queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper) { it.get(0, targetType) }
        }
    }

    inline fun <reified T> fetchFields(vararg params: Pair<String, Any?>): List<T> = fetchFields(params.toMap())

    inline fun <reified T> fetchField(params: Map<String, Any?>): T? {
        val targetType = typeOf<T>()
        return withPreparedQuery(params) { transformedSql, listParams ->
            val rows = queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper, maxRows = 2) {
                it.get<T>(
                    0,
                    targetType
                )
            }
            if (rows.size > 1) throw StatementException(
                StatementExceptionReason.INCORRECT_RESULT_SIZE,
                details = "Expected 0 or 1, got at least 2 rows."
            )
            rows.firstOrNull()
        }
    }

    inline fun <reified T> fetchField(vararg params: Pair<String, Any?>): T? = fetchField(params.toMap())

    inline fun <reified T> fetchFieldStrict(params: Map<String, Any?>): T {
        val targetType = typeOf<T>()
        return withPreparedQuery(params) { transformedSql, listParams ->
            val rows = queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper, maxRows = 2) {
                it.get<T>(
                    0,
                    targetType
                )
            }
            if (rows.isEmpty()) throw StatementException(
                StatementExceptionReason.INCORRECT_RESULT_SIZE,
                details = "Expected 1, got 0 rows."
            )
            if (rows.size > 1) throw StatementException(
                StatementExceptionReason.INCORRECT_RESULT_SIZE,
                details = "Expected 1, got at least two rows."
            )
            rows.first()
        }
    }

    inline fun <reified T> fetchFieldStrict(vararg params: Pair<String, Any?>): T = fetchFieldStrict(params.toMap())

    inline fun <reified T> forEachField(params: Map<String, Any?>, fetchSize: Int, crossinline block: (T) -> Unit) {
        val targetType = typeOf<T>()
        withPreparedQuery(params) { transformedSql, listParams ->
            queryExecutor.queryForEach(transformedSql, listParams, parameterSerializer, resultMapper, fetchSize, {
                it.get<T>(0, targetType)
            }, { block(it) })
        }
    }

    inline fun <reified T> forEachField(vararg params: Pair<String, Any?>, fetchSize: Int, crossinline block: (T) -> Unit) = forEachField(params.toMap(), fetchSize, block)

    fun update(params: Map<String, Any?>): Long {
        return withPreparedQuery(params) { transformedSql, listParams ->
            queryExecutor.update(transformedSql, listParams, parameterSerializer)
        }
    }


    fun update(vararg params: Pair<String, Any?>): Long = update(params.toMap())

    fun execute() {
        withQueryContext(sql, { emptyMap() }) {
            queryExecutor.execute(sql)
        }
    }
}
