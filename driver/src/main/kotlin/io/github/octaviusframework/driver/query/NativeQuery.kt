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
        return withQueryContext(sql, { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() }, { sql }, { params.toList() }) {
            queryExecutor.query(sql, params.toList(), parameterSerializer, resultMapper)
        }
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
        return withQueryContext(sql, { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() }, { sql }, { params.toList() }) {
            queryExecutor.update(sql, params.toList(), parameterSerializer)
        }
    }

    fun execute() {
        withQueryContext(sql, { emptyMap() }) {
            queryExecutor.execute(sql)
        }
    }

    inline fun <reified T : Any> fetchListOf(vararg params: Any?): List<T> {
        val targetType = typeOf<T>()
        val recordType = PgType.Record(2249, "record", "pg_catalog")
        return withQueryContext(sql, { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() }, { sql }, { params.toList() }) {
            queryExecutor.query(sql, params.toList(), parameterSerializer, resultMapper) {
                resultMapper.deserialize(it, targetType, recordType)
            }
        }
    }

    inline fun <reified T : Any> fetchSingleOf(vararg params: Any?): T {
        val row = fetchOne(*params)
        return resultMapper.deserialize(row, typeOf<T>(), PgType.Record(2249, "record", "pg_catalog"))
    }

    inline fun <reified T : Any> fetchSingleOfOrNull(vararg params: Any?): T? {
        val row = fetchOneOrNull(*params) ?: return null
        return resultMapper.deserialize(row, typeOf<T>(), PgType.Record(2249, "record", "pg_catalog"))
    }

    inline fun <reified T> fetchField(vararg params: Any?): T {
        return fetchOne(*params).get<T>(0)
    }

    inline fun <reified T> fetchColumn(vararg params: Any?): List<T> {
        val targetType = typeOf<T>()
        return withQueryContext(sql, { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() }, { sql }, { params.toList() }) {
            queryExecutor.query(sql, params.toList(), parameterSerializer, resultMapper) { it.get(0, targetType) }
        }
    }
}
