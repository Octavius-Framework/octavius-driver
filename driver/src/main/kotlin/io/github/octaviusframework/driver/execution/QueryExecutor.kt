package io.github.octaviusframework.driver.execution

import io.github.octaviusframework.driver.converter.result.mapper.ResultMapper
import io.github.octaviusframework.driver.exception.*
import io.github.octaviusframework.driver.io.PgStream
import io.github.octaviusframework.driver.message.translator.ExceptionTranslator
import io.github.octaviusframework.driver.message.backend.*
import io.github.octaviusframework.driver.message.frontend.*
import io.github.octaviusframework.driver.registry.TypeRegistry
import io.github.octaviusframework.driver.row.Row
import io.github.octaviusframework.driver.row.RowMetadata
import io.github.octaviusframework.driver.io.PgByteWriter
import kotlin.concurrent.withLock

/**
 * Handles the low-level execution of PostgreSQL queries over the wire protocol.
 *
 * This executor manages communication with the database using both the Simple Query Protocol
 * (for utility statements) and the Extended Query Protocol (for parameterized DML and DQL).
 * It is responsible for parsing responses, managing the transaction state flag, and mapping
 * results back into usable domain objects.
 */
class QueryExecutor internal constructor(
    private val stream: PgStream,
    private val typeRegistry: TypeRegistry,
    maxParameterWriterCapacity: Int? = null,
    initialParameterWriterCapacity: Int? = null
) {
    private val parameterWriter = PgByteWriter(
        initialCapacity = initialParameterWriterCapacity ?: 1024,
        maxCapacity = maxParameterWriterCapacity ?: 65536
    )
    
    var transactionStatus: Char = 'I'
        private set

    /**
     * Runs one exchange with the server: takes the connection, marks it busy, and releases it
     * whatever happens.
     *
     * The busy mark is what makes a reentrant call fail cleanly instead of interleaving its
     * messages into an exchange already in flight - the lock alone cannot, being reentrant.
     */
    private inline fun <T> exchange(block: () -> T): T = stream.lock.withLock {
        stream.beginExchange()
        try {
            block()
        } finally {
            stream.endExchange()
        }
    }

    /**
     * Uses Simple Query Protocol (Q). 
     * Intended for calls that do not return results or where results are ignored (e.g., SET TIME ZONE, BEGIN).
     */
    fun execute(sql: String) = exchange {
        stream.sendMessage(SimpleQueryMessage(sql))
        stream.flush()

        var errorResponse: ErrorResponseMessage? = null
        var executionError: OctaviusException? = null
        while (true) {
            val msg = stream.receiveMessage()
            when (msg) {
                is ErrorResponseMessage -> errorResponse = msg
                is ReadyForQueryMessage -> {
                    transactionStatus = msg.transactionStatus
                    break
                }
                is RowDescriptionMessage, is DataRowMessage -> {
                    if (errorResponse == null && executionError == null) {
                        executionError = InvalidOperationException(
                            InvalidOperationExceptionReason.UNEXPECTED_RESULT,
                            "Method execute() received result rows. Use query() for DQL queries."
                        )
                    }
                }
                is CommandCompleteMessage, is EmptyQueryResponseMessage -> { /* Ignore - expected */ }
                else -> { /* Ignore */ }
            }
        }

        if (errorResponse != null) {
            throw ExceptionTranslator.translate(errorResponse)
        } else if (executionError != null) {
            throw executionError
        }
    }

    /**
     * Uses Extended Query Protocol (Parse, Bind, Execute, Sync).
     * Intended for DML (INSERT, UPDATE, DELETE). Expects no rows returned.
     * Returns the number of updated rows.
     */
    fun update(
        sql: String,
        params: Array<out Any?> = emptyArray(),
        parameterSerializer: ParameterSerializer? = null
    ): Long = exchange {
        val paramTypes = parameterSerializer?.serializeAll(params, parameterWriter) ?: IntArray(0)
        val paramValues = if (parameterSerializer != null) parameterWriter.data else ByteArray(0)
        val paramValuesLength = if (parameterSerializer != null) parameterWriter.position else 0
        val statementName = ""
        val portalName = ""
        
        stream.sendMessage(ParseMessage(statementName, sql, paramTypes))
        stream.sendMessage(BindMessage(portalName, statementName, params.size, paramValues, listOf(1), listOf(1), paramValuesLength))
        stream.sendMessage(DescribeMessage('P', portalName))
        stream.sendMessage(ExecuteMessage(portalName, 0))
        stream.sendMessage(SyncMessage())
        
        stream.flush()
        
        var rowsAffected = 0L
        var errorResponse: ErrorResponseMessage? = null
        var executionError: OctaviusException? = null
        
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
                    if (errorResponse == null && executionError == null) {
                        executionError = InvalidOperationException(
                            InvalidOperationExceptionReason.UNEXPECTED_RESULT,
                            "Method update() received result rows. Use query() for DQL queries."
                        )
                    }
                }
                is ErrorResponseMessage -> {
                    if (errorResponse == null) errorResponse = msg
                }
                is ReadyForQueryMessage -> {
                    transactionStatus = msg.transactionStatus
                    break
                }
                else -> { /* Ignore */ }
            }
        }

        if (errorResponse != null) {
            throw ExceptionTranslator.translate(errorResponse)
        } else if (executionError != null) {
            throw executionError
        }
        
        return rowsAffected
    }

    /**
     * Uses Extended Query Protocol.
     * Intended for DQL (SELECT).
     * Returns a parsed list of rows (Row) immediately.
     */
    fun query(
        sql: String,
        params: Array<out Any?> = emptyArray(),
        parameterSerializer: ParameterSerializer? = null,
        mapper: ResultMapper,
        maxRows: Int = 0
    ): List<Row> = query(sql, params, parameterSerializer, mapper, maxRows) { it }

    /**
     * Uses Extended Query Protocol.
     * Intended for DQL (SELECT).
     * Returns a parsed list of elements using the provided transform function immediately.
     */
    fun <R> query(
        sql: String,
        params: Array<out Any?> = emptyArray(),
        parameterSerializer: ParameterSerializer?,
        mapper: ResultMapper,
        maxRows: Int = 0,
        transform: (Row) -> R
    ): List<R> = exchange {
        val paramTypes = parameterSerializer?.serializeAll(params, parameterWriter) ?: IntArray(0)
        val paramValues = if (parameterSerializer != null) parameterWriter.data else ByteArray(0)
        val paramValuesLength = if (parameterSerializer != null) parameterWriter.position else 0
        val statementName = ""
        val portalName = ""
        
        stream.sendMessage(ParseMessage(statementName, sql, paramTypes))
        stream.sendMessage(BindMessage(portalName, statementName, params.size, paramValues, listOf(1), listOf(1), paramValuesLength))
        stream.sendMessage(DescribeMessage('P', portalName))
        stream.sendMessage(ExecuteMessage(portalName, maxRows))
        stream.sendMessage(SyncMessage())
        
        stream.flush()
        
        val rows = mutableListOf<R>()
        var rowMetadata: RowMetadata? = null
        var errorResponse: ErrorResponseMessage? = null
        var executionError: OctaviusException? = null
        
        while (true) {
            val msg = stream.receiveMessage()
            when (msg) {
                is ParseCompleteMessage, is BindCompleteMessage, is PortalSuspendedMessage -> { /* Expected */ }
                is RowDescriptionMessage -> rowMetadata = RowMetadata(msg.fields)
                is NoDataMessage -> { /* Expected if query returns no rows */ }
                is DataRowMessage -> {
                    if (rowMetadata == null) {
                        if (executionError == null) {
                            executionError = InvalidOperationException(
                                InvalidOperationExceptionReason.UNEXPECTED_RESULT,
                                "Received DataRow before RowDescription"
                            )
                        }
                    } else if (executionError == null && errorResponse == null) {
                        try {
                            rows.add(transform(
                                Row(
                                    msg.rawData,
                                    msg.columnOffsets,
                                    msg.columnLengths,
                                    rowMetadata,
                                    typeRegistry,
                                    mapper
                                )
                            ))
                        } catch (e: OctaviusException) {
                            executionError = e
                        } catch (e: Exception) {
                            executionError = MappingException(
                                MappingExceptionReason.CONVERSION_ERROR,
                                "Exception in row mapping: ${e.message}",
                                e
                            )
                        }
                    }
                }
                is CommandCompleteMessage -> { /* Ignored in DQL queries */ }
                is ErrorResponseMessage -> {
                    if (errorResponse == null) errorResponse = msg
                }
                is ReadyForQueryMessage -> {
                    transactionStatus = msg.transactionStatus
                    break
                }
                else -> { /* Ignore */ }
            }
        }
        
        if (errorResponse != null) {
            throw ExceptionTranslator.translate(errorResponse)
        } else if (executionError != null) {
            throw executionError
        }

        return rows
    }

    /**
     * Uses Extended Query Protocol.
     * Intended for DQL (SELECT).
     * Iterates over rows in batches of fetchSize and applies the given transform and block.
     */
    fun <R> queryForEach(
        sql: String,
        params: Array<out Any?> = emptyArray(),
        parameterSerializer: ParameterSerializer,
        mapper: ResultMapper,
        fetchSize: Int,
        transform: (Row) -> R,
        block: (R) -> Unit
    ) = exchange {
        val paramTypes = parameterSerializer.serializeAll(params, parameterWriter)
        val paramValues = parameterWriter.data
        val paramValuesLength = parameterWriter.position
        val statementName = ""
        val portalName = ""
        
        stream.sendMessage(ParseMessage(statementName, sql, paramTypes))
        stream.sendMessage(BindMessage(portalName, statementName, params.size, paramValues, listOf(1), listOf(1), paramValuesLength))
        stream.sendMessage(DescribeMessage('P', portalName))
        
        var rowMetadata: RowMetadata? = null
        var errorResponse: ErrorResponseMessage? = null
        var executionError: OctaviusException? = null

        fetchLoop@ while (true) {
            stream.sendMessage(ExecuteMessage(portalName, fetchSize))
            stream.sendMessage(FlushMessage())
            stream.flush()

            msgLoop@ while (true) {
                when (val msg = stream.receiveMessage()) {
                    is ParseCompleteMessage, is BindCompleteMessage -> { /* Expected */ }
                    is RowDescriptionMessage -> rowMetadata = RowMetadata(msg.fields)
                    is DataRowMessage -> {
                        if (rowMetadata == null) {
                            executionError = InvalidOperationException(
                                InvalidOperationExceptionReason.UNEXPECTED_RESULT,
                                "Received DataRow before RowDescription"
                            )
                            break@fetchLoop
                        } else {
                            try {
                                block(transform(Row(msg.rawData, msg.columnOffsets, msg.columnLengths, rowMetadata, typeRegistry, mapper)))
                            } catch (e: OctaviusException) {
                                executionError = e
                                break@fetchLoop
                            } catch (e: Exception) {
                                executionError = MappingException(
                                    MappingExceptionReason.CONVERSION_ERROR,
                                    "Exception in block: ${e.message}",
                                    e
                                )
                                break@fetchLoop
                            }
                        }
                    }
                    is PortalSuspendedMessage -> {
                        break@msgLoop
                    }
                    is NoDataMessage, is CommandCompleteMessage -> {
                        break@fetchLoop
                    }
                    is ErrorResponseMessage -> {
                        errorResponse = msg
                        break@fetchLoop
                    }
                    else -> { /* Ignore */ }
                }
            }
        }

        stream.sendMessage(SyncMessage())
        stream.flush()
        
        while (true) {
            val msg = stream.receiveMessage()
            if (msg is ReadyForQueryMessage) {
                transactionStatus = msg.transactionStatus
                break
            } else if (msg is ErrorResponseMessage) {
                if (errorResponse == null) errorResponse = msg
            }
        }

        if (errorResponse != null) throw ExceptionTranslator.translate(errorResponse)
        if (executionError != null) throw executionError
    }
}

