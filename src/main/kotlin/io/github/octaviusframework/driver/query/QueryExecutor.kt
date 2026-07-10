package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.converter.result.mapper.ResultMapper
import io.github.octaviusframework.driver.io.PgStream
import io.github.octaviusframework.driver.message.backend.*
import io.github.octaviusframework.driver.message.frontend.*
import io.github.octaviusframework.driver.registry.TypeRegistry
import io.github.octaviusframework.driver.exception.OctaviusException

class QueryExecutor(
    private val stream: PgStream,
    private val typeRegistry: TypeRegistry
) {

    var transactionStatus: Char = 'I'
        private set

    /**
     * Uses Simple Query Protocol (Q). 
     * Intended for calls that do not return results or where results are ignored (e.g., SET TIME ZONE, BEGIN).
     */
    fun execute(sql: String) {
        stream.sendMessage(SimpleQueryMessage(sql))
        stream.flush()

        var errorMessage: String? = null
        while (true) {
            val msg = stream.receiveMessage()
            when (msg) {
                is ErrorResponseMessage -> errorMessage = msg.message
                is ReadyForQueryMessage -> {
                    transactionStatus = msg.transactionStatus
                    break
                }
                is RowDescriptionMessage, is DataRowMessage -> {
                    if (errorMessage == null) {
                        errorMessage = "Method execute() received result rows. Use query() for DQL queries."
                    }
                }
                is CommandCompleteMessage, is EmptyQueryResponseMessage -> { /* Ignore - expected */ }
                else -> { /* Ignore */ }
            }
        }

        if (errorMessage != null) {
            throw OctaviusException("Database error during query execution: $errorMessage")
        }
    }

    /**
     * Uses Extended Query Protocol (Parse, Bind, Execute, Sync).
     * Intended for DML (INSERT, UPDATE, DELETE). Expects no rows returned.
     * Returns the number of updated rows.
     */
    fun update(sql: String, paramTypes: List<Int> = emptyList(), paramValues: List<ByteArray?> = emptyList()): Long {
        val statementName = ""
        val portalName = ""
        
        stream.sendMessage(ParseMessage(statementName, sql, paramTypes))
        stream.sendMessage(BindMessage(portalName, statementName, paramValues, listOf(1), listOf(1)))
        stream.sendMessage(DescribeMessage('P', portalName))
        stream.sendMessage(ExecuteMessage(portalName, 0))
        stream.sendMessage(SyncMessage())
        
        stream.flush()
        
        var rowsAffected = 0L
        var errorMessage: String? = null
        
        while (true) {
            val msg = stream.receiveMessage()
            when (msg) {
                is ParseCompleteMessage, is BindCompleteMessage, is NoDataMessage -> { /* Expected */ }
                is CommandCompleteMessage -> {
                    // tag format is e.g., "INSERT 0 1", "UPDATE 5", "DELETE 2"
                    val parts = msg.tag.split(" ")
                    if (parts.size >= 2) {
                        rowsAffected = parts.last().toLongOrNull() ?: 0L
                    }
                }
                is DataRowMessage, is RowDescriptionMessage -> {
                    if (errorMessage == null) errorMessage = "Method update() received result rows. Use query() for DQL queries."
                }
                is ErrorResponseMessage -> {
                    if (errorMessage == null) errorMessage = "Database error during query execution (update): ${msg.message}"
                }
                is ReadyForQueryMessage -> {
                    transactionStatus = msg.transactionStatus
                    break
                }
                else -> { /* Ignore */ }
            }
        }

        if (errorMessage != null) {
            throw OctaviusException(errorMessage)
        }
        
        return rowsAffected
    }

    /**
     * Uses Extended Query Protocol.
     * Intended for DQL (SELECT).
     * Returns a parsed list of rows (Row) immediately.
     */
    fun query(sql: String, paramTypes: List<Int> = emptyList(), paramValues: List<ByteArray?> = emptyList(), mapper: ResultMapper): List<Row> {
        return query(sql, paramTypes, paramValues, mapper) { it }
    }

    /**
     * Uses Extended Query Protocol.
     * Intended for DQL (SELECT).
     * Returns a parsed list of elements using the provided transform function immediately.
     */
    fun <R> query(sql: String, paramTypes: List<Int> = emptyList(), paramValues: List<ByteArray?> = emptyList(), mapper: ResultMapper, transform: (Row) -> R): List<R> {
        val statementName = ""
        val portalName = ""
        
        stream.sendMessage(ParseMessage(statementName, sql, paramTypes))
        stream.sendMessage(BindMessage(portalName, statementName, paramValues, listOf(1), listOf(1)))
        stream.sendMessage(DescribeMessage('P', portalName))
        stream.sendMessage(ExecuteMessage(portalName, 0))
        stream.sendMessage(SyncMessage())
        
        stream.flush()
        
        val rows = mutableListOf<R>()
        var rowDescription: RowDescriptionMessage? = null
        var errorMessage: String? = null
        
        while (true) {
            val msg = stream.receiveMessage()
            when (msg) {
                is ParseCompleteMessage, is BindCompleteMessage -> { /* Expected */ }
                is RowDescriptionMessage -> rowDescription = msg
                is NoDataMessage -> { /* Expected if query returns no rows */ }
                is DataRowMessage -> {
                    if (rowDescription == null) {
                        errorMessage = "Received DataRow before RowDescription"
                    } else {
                        rows.add(transform(OctaviusRow(msg.rawData, msg.columnOffsets, msg.columnLengths, rowDescription.fields, typeRegistry, mapper)))
                    }
                }
                is CommandCompleteMessage -> { /* Ignored in DQL queries */ }
                is ErrorResponseMessage -> {
                    if (errorMessage == null) errorMessage = "Database error during query execution (query): ${msg.message}"
                }
                is ReadyForQueryMessage -> {
                    transactionStatus = msg.transactionStatus
                    break
                }
                else -> { /* Ignore */ }
            }
        }
        
        if (errorMessage != null) {
            throw OctaviusException(errorMessage)
        }

        return rows
    }
}

