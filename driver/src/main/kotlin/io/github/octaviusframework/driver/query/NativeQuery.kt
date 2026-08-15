package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.exception.StatementException
import io.github.octaviusframework.driver.exception.StatementExceptionReason
import io.github.octaviusframework.driver.execution.QueryExecutor

import io.github.octaviusframework.driver.row.Row
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.registry.TypeManager
import kotlin.reflect.typeOf

/**
 * Represents a query that uses standard PostgreSQL positional parameters (e.g., `$1`, `$2`).
 *
 * `NativeQuery` passes the provided SQL string and parameters directly to the underlying
 * [QueryExecutor] without any intermediate parsing or modification of the SQL statement.
 * It provides various execution methods to fetch rows, map results to Kotlin objects, 
 * extract single fields, or perform data modifications (updates).
 */
class NativeQuery internal constructor(
    sql: String,
    queryExecutor: QueryExecutor,
    typeManager: TypeManager
) : OctaviusQuery<NativeQuery>(sql, queryExecutor, typeManager) {

    //--------------------------------------------Row-based Methods-----------------------------------------------------
    fun fetchRows(vararg params: Any?): List<Row> {
        return withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.query(sql, params, parameterSerializer, resultMapper)
        }
    }

    fun fetchRow(vararg params: Any?): Row? {
        return withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            val rows = queryExecutor.query(sql, params, parameterSerializer, resultMapper, maxRows = 2)
            if (rows.size > 1) throw StatementException(
                StatementExceptionReason.INCORRECT_RESULT_SIZE,
                details = "Expected 0 or 1, got at least 2 rows."
            )
            rows.firstOrNull()
        }
    }

    fun fetchRowStrict(vararg params: Any?): Row {
        return withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            val rows = queryExecutor.query(sql, params, parameterSerializer, resultMapper, maxRows = 2)
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

    fun forEachRow(vararg params: Any?, fetchSize: Int, block: (Row) -> Unit) {
        withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.queryForEach(sql, params, parameterSerializer, resultMapper, fetchSize, { it }, block)
        }
    }

    //----------------------------------------Object Mapping Methods----------------------------------------------------

    inline fun <reified T : Any> fetchObjects(vararg params: Any?): List<T> {
        val targetType = typeOf<T>()
        val recordType = PgType.Record
        return withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.query(sql, params, parameterSerializer, resultMapper) {
                resultMapper.deserialize(it, targetType, recordType)
            }
        }
    }

    inline fun <reified T : Any> fetchObject(vararg params: Any?): T? {
        val targetType = typeOf<T>()
        val recordType = PgType.Record
        return withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            val rows = queryExecutor.query(sql, params, parameterSerializer, resultMapper, maxRows = 2) {
                resultMapper.deserialize<T>(it, targetType, recordType)
            }
            if (rows.size > 1) throw StatementException(
                StatementExceptionReason.INCORRECT_RESULT_SIZE,
                details = "Expected 0 or 1, got at least 2 rows."
            )
            rows.firstOrNull()
        }
    }

    inline fun <reified T : Any> fetchObjectStrict(vararg params: Any?): T {
        val targetType = typeOf<T>()
        val recordType = PgType.Record
        return withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            val rows = queryExecutor.query(sql, params, parameterSerializer, resultMapper, maxRows = 2) {
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

    inline fun <reified T : Any> forEachObject(vararg params: Any?, fetchSize: Int, crossinline block: (T) -> Unit) {
        val targetType = typeOf<T>()
        val recordType = PgType.Record
        withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.queryForEach(sql, params, parameterSerializer, resultMapper, fetchSize, {
                resultMapper.deserialize<T>(it, targetType, recordType)
            }, { block(it) })
        }
    }

    //-----------------------------------------Single Column Methods----------------------------------------------------

    inline fun <reified T> fetchFields(vararg params: Any?): List<T> {
        val targetType = typeOf<T>()
        return withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.query(sql, params, parameterSerializer, resultMapper) { it.get(0, targetType) }
        }
    }

    inline fun <reified T> fetchField(vararg params: Any?): T? {
        val targetType = typeOf<T>()
        return withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            val rows = queryExecutor.query(sql, params, parameterSerializer, resultMapper, maxRows = 2) {
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

    inline fun <reified T> fetchFieldStrict(vararg params: Any?): T {
        val targetType = typeOf<T>()
        return withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            val rows = queryExecutor.query(sql, params, parameterSerializer, resultMapper, maxRows = 2) {
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
                details = "Expected 1, got at least 2 rows."
            )
            rows.first()
        }
    }

    inline fun <reified T> forEachField(vararg params: Any?, fetchSize: Int, crossinline block: (T) -> Unit) {
        val targetType = typeOf<T>()
        withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.queryForEach(sql, params, parameterSerializer, resultMapper, fetchSize, {
                it.get<T>(0, targetType)
            }, { block(it) })
        }
    }

    //------------------------------------------Modification methods----------------------------------------------------

    fun update(vararg params: Any?): Long {
        return withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.update(sql, params, parameterSerializer)
        }
    }

    fun execute() {
        withQueryContext(sql, { emptyMap() }) {
            queryExecutor.execute(sql)
        }
    }
}
