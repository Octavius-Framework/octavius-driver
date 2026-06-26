package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.type.TypeRegistry

class NativeQuery(
    sql: String,
    queryExecutor: QueryExecutor,
    typeRegistry: TypeRegistry
) : OctaviusQuery<NativeQuery>(sql, queryExecutor, typeRegistry) {

    fun fetchAll(vararg params: Any?): List<Row> {
        val (types, values) = serializeParameters(params.toList())
        return queryExecutor.query(sql, types, values, localDeserializer)
    }

    fun fetchOne(vararg params: Any?): Row? {
        val rows = fetchAll(params)
        return rows.firstOrNull()
    }

    fun executeUpdate(vararg params: Any?): Long {
        val (types, values) = serializeParameters(params.toList())
        return queryExecutor.update(sql, types, values)
    }

    fun execute(vararg params: Any?) {
        val (types, values) = serializeParameters(params.toList())
        queryExecutor.update(sql, types, values)
    }
}
