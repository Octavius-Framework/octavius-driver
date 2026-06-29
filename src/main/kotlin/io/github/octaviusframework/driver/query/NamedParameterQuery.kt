package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.type.TypeRegistry

class NamedParameterQuery(
    sql: String,
    queryExecutor: QueryExecutor,
    typeRegistry: TypeRegistry
) : OctaviusQuery<NamedParameterQuery>(sql, queryExecutor, typeRegistry) {

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

    fun fetchOne(params: Map<String, Any?>): Row? {
        val rows = fetchAll(params)
        return rows.firstOrNull()
    }

    fun execute(params: Map<String, Any?>): Long {
        val (transformedSql, types, values) = prepareNamedQuery(params)
        return queryExecutor.update(transformedSql, types, values)
    }

    fun fetchAll(vararg params: Pair<String, Any?>): List<Row> = fetchAll(params.toMap())

    fun fetchOne(vararg params: Pair<String, Any?>): Row? = fetchOne(params.toMap())

    fun execute(vararg params: Pair<String, Any?>): Long = execute(params.toMap())
}
