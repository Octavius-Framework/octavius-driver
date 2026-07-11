package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeManager
import kotlin.reflect.typeOf

class NamedParameterQuery(
    sql: String,
    queryExecutor: QueryExecutor,
    typeManager: TypeManager
) : OctaviusQuery<NamedParameterQuery>(sql, queryExecutor, typeManager) {

    @PublishedApi
    internal fun prepareNamedQuery(params: Map<String, Any?>): Pair<String, List<Any?>> {
        val parsed = SqlParameterParser.parse(sql)
        val listParams = parsed.paramNames.map {
            if (!params.containsKey(it)) {
                throw IllegalArgumentException("Missing parameter: $it")
            }
            params[it]
        }
        return Pair(parsed.transformedSql, listParams)
    }

    fun fetchAll(params: Map<String, Any?>): List<Row> {
        val (transformedSql, listParams) = prepareNamedQuery(params)
        return withQueryContext(sql, { params }, { transformedSql }) {
            queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper)
        }
    }

    fun fetchOne(params: Map<String, Any?>): Row {
        val rows = fetchAll(params)
        check(rows.size == 1) { "Expected exactly one row, but got ${rows.size}" }
        return rows.first()
    }

    fun fetchOneOrNull(params: Map<String, Any?>): Row? {
        val rows = fetchAll(params)
        check(rows.size <= 1) { "Expected 0 or 1 row, but got ${rows.size}" }
        return rows.firstOrNull()
    }

    fun update(params: Map<String, Any?>): Long {
        val (transformedSql, listParams) = prepareNamedQuery(params)
        return withQueryContext(sql, { params }, { transformedSql }) {
            queryExecutor.update(transformedSql, listParams, parameterSerializer)
        }
    }

    fun fetchAll(vararg params: Pair<String, Any?>): List<Row> = fetchAll(params.toMap())

    fun fetchOne(vararg params: Pair<String, Any?>): Row = fetchOne(params.toMap())

    fun fetchOneOrNull(vararg params: Pair<String, Any?>): Row? = fetchOneOrNull(params.toMap())

    fun update(vararg params: Pair<String, Any?>): Long = update(params.toMap())
    
    fun execute() {
        withQueryContext(sql, { emptyMap() }) {
            queryExecutor.execute(sql)
        }
    }

    inline fun <reified T : Any> fetchListOf(params: Map<String, Any?>): List<T> {
        val (transformedSql, listParams) = prepareNamedQuery(params)
        val targetType = typeOf<T>()
        val recordType = PgType.Record(2249, "record", "pg_catalog")
        return withQueryContext(sql, { params }, { transformedSql }) {
            queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper) {
                resultMapper.deserialize(it, targetType, recordType)
            }
        }
    }

    inline fun <reified T : Any> fetchSingleOf(params: Map<String, Any?>): T {
        val row = fetchOne(params)
        return row.resultMapper.deserialize(row, typeOf<T>(), PgType.Record(2249, "record", "pg_catalog"))
    }

    inline fun <reified T : Any> fetchSingleOfOrNull(params: Map<String, Any?>): T? {
        val row = fetchOneOrNull(params) ?: return null
        return resultMapper.deserialize(row, typeOf<T>(), PgType.Record(2249, "record", "pg_catalog"))
    }

    inline fun <reified T> fetchField(params: Map<String, Any?>): T {
        return fetchOne(params).get<T>(0)
    }

    inline fun <reified T> fetchColumn(params: Map<String, Any?>): List<T> {
        val (transformedSql, listParams) = prepareNamedQuery(params)
        val targetType = typeOf<T>()
        return withQueryContext(sql, { params }, { transformedSql }) {
            queryExecutor.query(transformedSql, listParams, parameterSerializer, resultMapper) { it.get(0, targetType) }
        }
    }

    inline fun <reified T : Any> fetchListOf(vararg params: Pair<String, Any?>): List<T> = fetchListOf(params.toMap())

    inline fun <reified T : Any> fetchSingleOf(vararg params: Pair<String, Any?>): T = fetchSingleOf(params.toMap())

    inline fun <reified T : Any> fetchSingleOfOrNull(vararg params: Pair<String, Any?>): T? = fetchSingleOfOrNull(params.toMap())

    inline fun <reified T> fetchField(vararg params: Pair<String, Any?>): T = fetchField(params.toMap())

    inline fun <reified T> fetchColumn(vararg params: Pair<String, Any?>): List<T> = fetchColumn(params.toMap())
}
