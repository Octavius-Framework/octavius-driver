package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeManager
import kotlin.reflect.typeOf

class NativeQuery(
    sql: String,
    queryExecutor: QueryExecutor,
    typeManager: TypeManager
) : OctaviusQuery<NativeQuery>(sql, queryExecutor, typeManager) {

    fun fetchAll(vararg params: Any?): List<Row> {
        val (types, values) = serializeParameters(params.toList())
        return queryExecutor.query(sql, types, values, localDeserializer)
    }

    fun fetchOne(vararg params: Any?): Row {
        val rows = fetchAll(*params)
        check(rows.size == 1) { "Expected exactly one row, but got ${rows.size}" }
        return rows.first()
    }

    fun fetchOneOrNull(vararg params: Any?): Row? {
        val rows = fetchAll(*params)
        check(rows.size <= 1) { "Expected 0 or 1 row, but got ${rows.size}" }
        return rows.firstOrNull()
    }

    fun update(vararg params: Any?): Long {
        val (types, values) = serializeParameters(params.toList())
        return queryExecutor.update(sql, types, values)
    }

    fun execute() {
        queryExecutor.execute(sql)
    }

    inline fun <reified T : Any> fetchListOf(vararg params: Any?): List<T> {
        val (types, values) = serializeParameters(params.toList())
        val targetType = typeOf<T>()
        val recordType = PgType.Record(2249, "record", "pg_catalog")
        return queryExecutor.query(sql, types, values, localDeserializer) {
            it.resultMapper.deserialize(it, targetType, recordType)
        }
    }

    inline fun <reified T : Any> fetchSingleOf(vararg params: Any?): T {
        val row = fetchOne(*params)
        return row.resultMapper.deserialize(row, typeOf<T>(), PgType.Record(2249, "record", "pg_catalog"))
    }

    inline fun <reified T : Any> fetchSingleOfOrNull(vararg params: Any?): T? {
        val row = fetchOneOrNull(*params) ?: return null
        return row.resultMapper.deserialize(row, typeOf<T>(), PgType.Record(2249, "record", "pg_catalog"))
    }

    inline fun <reified T> fetchField(vararg params: Any?): T {
        return fetchOne(*params).get<T>(0)
    }

    inline fun <reified T> fetchColumn(vararg params: Any?): List<T> {
        val (types, values) = serializeParameters(params.toList())
        val targetType = typeOf<T>()
        return queryExecutor.query(sql, types, values, localDeserializer) { it.get<T>(0, targetType) }
    }
}
