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
    internal fun prepareNamedQuery(params: Map<String, Any?>): Triple<String, List<Int>, List<ByteArray?>> {
        val parsed = SqlParameterParser.parse(sql)
        val listParams = parsed.paramNames.map {
            if (!params.containsKey(it)) {
                throw IllegalArgumentException("Missing parameter: $it")
            }
            params[it]
        }
        val (types, values) = serializeParameters(listParams)
        return Triple(parsed.transformedSql, types, values)
    }

    fun fetchAll(params: Map<String, Any?>): List<Row> {
        val (transformedSql, types, values) = prepareNamedQuery(params)
        return queryExecutor.query(transformedSql, types, values, localDeserializer)
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
        val (transformedSql, types, values) = prepareNamedQuery(params)
        return queryExecutor.update(transformedSql, types, values)
    }

    fun fetchAll(vararg params: Pair<String, Any?>): List<Row> = fetchAll(params.toMap())

    fun fetchOne(vararg params: Pair<String, Any?>): Row = fetchOne(params.toMap())

    fun fetchOneOrNull(vararg params: Pair<String, Any?>): Row? = fetchOneOrNull(params.toMap())

    fun update(vararg params: Pair<String, Any?>): Long = update(params.toMap())
    
    fun execute() {
        queryExecutor.execute(sql)
    }

    inline fun <reified T : Any> fetchListOf(params: Map<String, Any?>): List<T> {
        val (transformedSql, types, values) = prepareNamedQuery(params)
        val targetType = typeOf<T>()
        val recordType = PgType.Record(2249, "record", "pg_catalog")
        return queryExecutor.query(transformedSql, types, values, localDeserializer) {
            it.resultMapper.deserialize(it, targetType, recordType)
        }
    }

    inline fun <reified T : Any> fetchSingleOf(params: Map<String, Any?>): T {
        val row = fetchOne(params)
        return row.resultMapper.deserialize(row, typeOf<T>(), PgType.Record(2249, "record", "pg_catalog"))
    }

    inline fun <reified T : Any> fetchSingleOfOrNull(params: Map<String, Any?>): T? {
        val row = fetchOneOrNull(params) ?: return null
        return row.resultMapper.deserialize(row, typeOf<T>(), PgType.Record(2249, "record", "pg_catalog"))
    }

    inline fun <reified T> fetchField(params: Map<String, Any?>): T {
        return fetchOne(params).get<T>(0)
    }

    inline fun <reified T> fetchColumn(params: Map<String, Any?>): List<T> {
        val (transformedSql, types, values) = prepareNamedQuery(params)
        val targetType = typeOf<T>()
        return queryExecutor.query(transformedSql, types, values, localDeserializer) { it.get<T>(0, targetType) }
    }

    inline fun <reified T : Any> fetchListOf(vararg params: Pair<String, Any?>): List<T> = fetchListOf(params.toMap())

    inline fun <reified T : Any> fetchSingleOf(vararg params: Pair<String, Any?>): T = fetchSingleOf(params.toMap())

    inline fun <reified T : Any> fetchSingleOfOrNull(vararg params: Pair<String, Any?>): T? = fetchSingleOfOrNull(params.toMap())

    inline fun <reified T> fetchField(vararg params: Pair<String, Any?>): T = fetchField(params.toMap())

    inline fun <reified T> fetchColumn(vararg params: Pair<String, Any?>): List<T> = fetchColumn(params.toMap())
}
