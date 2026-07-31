package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.exception.IncorrectResultSizeException

import io.github.octaviusframework.driver.row.Row
import io.github.octaviusframework.driver.row.get
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeManager
import kotlin.reflect.typeOf

class NativeQuery internal constructor(
    sql: String,
    queryExecutor: QueryExecutor,
    typeManager: TypeManager
) : OctaviusQuery<NativeQuery>(sql, queryExecutor, typeManager) {

    //--------------------------------------------Row-based Methods-----------------------------------------------------
    fun fetchAll(vararg params: Any?): List<Row> {
        return withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.query(sql, params.toList(), parameterSerializer, resultMapper)
        }
    }

    fun fetchOne(vararg params: Any?): Row? {
        val rows = withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.query(sql, params.toList(), parameterSerializer, resultMapper, maxRows = 2)
        }
        if (rows.size > 1) throw IncorrectResultSizeException(1, rows.size)
        return rows.firstOrNull()
    }

    fun fetchOneStrict(vararg params: Any?): Row {
        val rows = withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.query(sql, params.toList(), parameterSerializer, resultMapper, maxRows = 2)
        }
        if (rows.size != 1) throw IncorrectResultSizeException(1, rows.size)
        return rows.first()
    }

    //----------------------------------------Object Mapping Methods----------------------------------------------------

    inline fun <reified T : Any> fetchListOf(vararg params: Any?): List<T> {
        val targetType = typeOf<T>()
        val recordType = PgType.Record
        return withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.query(sql, params.toList(), parameterSerializer, resultMapper) {
                resultMapper.deserialize(it, targetType, recordType)
            }
        }
    }

    inline fun <reified T> fetchSingleOf(vararg params: Any?): T {
        val targetType = typeOf<T>()
        val recordType = PgType.Record
        val rows = withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.query(sql, params.toList(), parameterSerializer, resultMapper, maxRows = 2) {
                resultMapper.deserialize<T>(it, targetType, recordType)
            }
        }
        if (rows.size > 1) throw IncorrectResultSizeException(1, rows.size)
        if (rows.isEmpty() && !targetType.isMarkedNullable) throw IncorrectResultSizeException(1, 0)
        return rows.firstOrNull() as T
    }

    //-----------------------------------------Single Column Methods----------------------------------------------------

    inline fun <reified T> fetchColumn(vararg params: Any?): List<T> {
        val targetType = typeOf<T>()
        return withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.query(sql, params.toList(), parameterSerializer, resultMapper) { it.get(0, targetType) }
        }
    }

    inline fun <reified T> fetchField(vararg params: Any?): T? {
        val targetType = typeOf<T>()
        val rows = withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.query(sql, params.toList(), parameterSerializer, resultMapper, maxRows = 2) {
                it.get<T>(
                    0,
                    targetType
                )
            }
        }
        if (rows.size > 1) throw IncorrectResultSizeException(1, rows.size)
        return rows.firstOrNull()
    }

    inline fun <reified T> fetchFieldStrict(vararg params: Any?): T {
        val targetType = typeOf<T>()
        val rows = withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.query(sql, params.toList(), parameterSerializer, resultMapper, maxRows = 2) {
                it.get<T>(
                    0,
                    targetType
                )
            }
        }
        if (rows.size != 1) throw IncorrectResultSizeException(1, rows.size)
        return rows.first()
    }

    //------------------------------------------Modification methods----------------------------------------------------

    fun update(vararg params: Any?): Long {
        return withQueryContext(
            sql,
            { params.mapIndexed { i, p -> (i + 1).toString() to p }.toMap() },
            { sql },
            { params.toList() }) {
            queryExecutor.update(sql, params.toList(), parameterSerializer)
        }
    }

    fun execute() {
        withQueryContext(sql, { emptyMap() }) {
            queryExecutor.execute(sql)
        }
    }
}
