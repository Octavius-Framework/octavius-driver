package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.type.TypeManager

class NamedParameterQuery(
    sql: String,
    queryExecutor: QueryExecutor,
    typeManager: TypeManager
) : OctaviusQuery<NamedParameterQuery>(sql, queryExecutor, typeManager) {

    private fun prepareNamedQuery(params: Map<String, Any?>): Triple<String, List<UInt>, List<ByteArray?>> {
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
}
