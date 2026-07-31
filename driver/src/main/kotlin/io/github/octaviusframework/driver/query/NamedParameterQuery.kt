package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.exception.IncorrectResultSizeException
import io.github.octaviusframework.driver.exception.StatementException
import io.github.octaviusframework.driver.exception.StatementExceptionReason

import io.github.octaviusframework.driver.row.Row
import io.github.octaviusframework.driver.row.get
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeManager
import kotlin.reflect.typeOf

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

    //--------------------------------------------Row-based Methods-----------------------------------------------------

    fun fetchAll(params: Map<String, Any?>): List<Row> {
        val (transformedSql, listParams) = prepareNamedQuery(params)
        return withQueryContext(sql, { params }, { transformedSql }) {
            queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper)
        }
    }

    fun fetchAll(vararg params: Pair<String, Any?>): List<Row> = fetchAll(params.toMap())

    fun fetchOne(params: Map<String, Any?>): Row? {
        val (transformedSql, listParams) = prepareNamedQuery(params)
        val rows = withQueryContext(sql, { params }, { transformedSql }) {
            queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper, maxRows = 2)
        }
        if (rows.size > 1) throw IncorrectResultSizeException(1, rows.size)
        return rows.firstOrNull()
    }

    fun fetchOne(vararg params: Pair<String, Any?>): Row? = fetchOne(params.toMap())

    fun fetchOneStrict(params: Map<String, Any?>): Row {
        val (transformedSql, listParams) = prepareNamedQuery(params)
        val rows = withQueryContext(sql, { params }, { transformedSql }) {
            queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper, maxRows = 2)
        }
        if (rows.size != 1) throw IncorrectResultSizeException(1, rows.size)
        return rows.first()
    }

    fun fetchOneStrict(vararg params: Pair<String, Any?>): Row = fetchOneStrict(params.toMap())

    //----------------------------------------Object Mapping Methods----------------------------------------------------

    inline fun <reified T : Any> fetchListOf(params: Map<String, Any?>): List<T> {
        val (transformedSql, listParams) = prepareNamedQuery(params)
        val targetType = typeOf<T>()
        val recordType = PgType.Record
        return withQueryContext(sql, { params }, { transformedSql }) {
            queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper) {
                resultMapper.deserialize(it, targetType, recordType)
            }
        }
    }

    inline fun <reified T : Any> fetchListOf(vararg params: Pair<String, Any?>): List<T> = fetchListOf(params.toMap())

    inline fun <reified T> fetchSingleOf(params: Map<String, Any?>): T {
        val (transformedSql, listParams) = prepareNamedQuery(params)
        val targetType = typeOf<T>()
        val recordType = PgType.Record
        val rows = withQueryContext(sql, { params }, { transformedSql }) {
            queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper, maxRows = 2) {
                resultMapper.deserialize<T>(it, targetType, recordType)
            }
        }
        if (rows.size > 1) throw IncorrectResultSizeException(1, rows.size)
        if (rows.isEmpty() && !targetType.isMarkedNullable) throw IncorrectResultSizeException(1, 0)
        return rows.firstOrNull() as T
    }

    inline fun <reified T> fetchSingleOf(vararg params: Pair<String, Any?>): T = fetchSingleOf(params.toMap())

    //-----------------------------------------Single Column Methods----------------------------------------------------

    inline fun <reified T> fetchColumn(params: Map<String, Any?>): List<T> {
        val (transformedSql, listParams) = prepareNamedQuery(params)
        val targetType = typeOf<T>()
        return withQueryContext(sql, { params }, { transformedSql }) {
            queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper) { it.get(0, targetType) }
        }
    }

    inline fun <reified T> fetchColumn(vararg params: Pair<String, Any?>): List<T> = fetchColumn(params.toMap())

    inline fun <reified T> fetchField(params: Map<String, Any?>): T? {
        val (transformedSql, listParams) = prepareNamedQuery(params)
        val targetType = typeOf<T>()
        val rows = withQueryContext(sql, { params }, { transformedSql }) {
            queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper, maxRows = 2) {
                it.get<T>(
                    0,
                    targetType
                )
            }
        }
        if (rows.size > 1) throw IncorrectResultSizeException(1, rows.size)
        return rows.firstOrNull()
    }

    inline fun <reified T> fetchField(vararg params: Pair<String, Any?>): T? = fetchField(params.toMap())

    inline fun <reified T> fetchFieldStrict(params: Map<String, Any?>): T {
        val (transformedSql, listParams) = prepareNamedQuery(params)
        val targetType = typeOf<T>()
        val rows = withQueryContext(sql, { params }, { transformedSql }) {
            queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper, maxRows = 2) {
                it.get<T>(
                    0,
                    targetType
                )
            }
        }
        if (rows.size != 1) throw IncorrectResultSizeException(1, rows.size)
        return rows.first()
    }

    inline fun <reified T> fetchFieldStrict(vararg params: Pair<String, Any?>): T = fetchFieldStrict(params.toMap())


    fun update(params: Map<String, Any?>): Long {
        val (transformedSql, listParams) = prepareNamedQuery(params)
        return withQueryContext(sql, { params }, { transformedSql }) {
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
