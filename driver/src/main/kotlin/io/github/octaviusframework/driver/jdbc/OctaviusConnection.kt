package io.github.octaviusframework.driver.jdbc

import io.github.octaviusframework.driver.exception.JdbcExceptionMessage
import io.github.octaviusframework.driver.exception.OctaviusJdbcException
import io.github.octaviusframework.driver.identifier.quoteAsPgIdentifier
import io.github.octaviusframework.driver.io.PgStream
import io.github.octaviusframework.driver.message.frontend.CancelRequestMessage
import io.github.octaviusframework.driver.query.QueryExecutor
import io.github.octaviusframework.driver.query.SqlParameterParser
import io.github.octaviusframework.driver.registry.GlobalTypeRegistry
import io.github.octaviusframework.driver.session.TransactionState
import java.sql.*
import java.util.*
import java.util.concurrent.Executor

/**
 * Represents a connection to a database within the Octavius Framework.
 * It implements the standard JDBC [Connection] interface but overrides
 * certain behaviors to fit the framework's architecture.
 */
class OctaviusConnection(internal val stream: PgStream, internal val url: String) : Connection {
    val typeRegistry = GlobalTypeRegistry.getRegistry(url)

    val queryExecutor = QueryExecutor(stream, typeRegistry)

    init {
        GlobalTypeRegistry.ensureLoaded(url, queryExecutor, getSearchPath())
    }

    internal var isClosedFlag: Boolean = false

    private var lastSearchPathString: String? = null
    private var cachedSearchPath: List<String>? = null


    internal fun checkClosed() {
        if (isClosedFlag) throw OctaviusJdbcException(JdbcExceptionMessage.CONNECTION_CLOSED)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> unwrap(iface: Class<T>): T {
        if (iface.isInstance(this)) {
            return this as T
        }
        throw OctaviusJdbcException(JdbcExceptionMessage.UNWRAP_ERROR, details = "Cannot unwrap to ${iface.name}")
    }

    override fun isWrapperFor(iface: Class<*>): Boolean = iface.isInstance(this)

    override fun nativeSQL(sql: String?): String = sql?.let { SqlParameterParser.parse(sql).transformedSql } ?: ""

    override fun close() {
        if (!isClosedFlag) {
            isClosedFlag = true
            stream.close()
        }
    }

    override fun isClosed(): Boolean = isClosedFlag // required by Hikari

    override fun getMetaData(): DatabaseMetaData = unsupported()

    override fun getWarnings(): SQLWarning? = null
    override fun clearWarnings() {} // required by Hikari


    override fun isValid(timeout: Int): Boolean { // required by Hikari
        if (timeout < 0) throw OctaviusJdbcException(JdbcExceptionMessage.INVALID_TIMEOUT)
        if (isClosedFlag) return false

        val originalTimeout = stream.networkTimeout
        return try {
            // In JDBC, the timeout for isValid is in seconds (0 means no limit)
            stream.networkTimeout = timeout * 1000
            queryExecutor.execute("")
            true
        } catch (e: Exception) {
            false
        } finally {
            try {
                stream.networkTimeout = originalTimeout
            } catch (ignore: Exception) {
            }
        }
    }

    override fun setClientInfo(name: String?, value: String?) = unsupported()
    override fun setClientInfo(properties: Properties?) = unsupported()
    override fun getClientInfo(name: String?): String = unsupported()
    override fun getClientInfo(): Properties = Properties()


    override fun abort(executor: Executor?) {
        if (executor == null) throw OctaviusJdbcException(
            JdbcExceptionMessage.FEATURE_NOT_SUPPORTED,
            details = "Executor cannot be null"
        )
        executor.execute {
            if (!isClosedFlag) {
                isClosedFlag = true
                try {
                    stream.close()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        // Signal for Hikari to evict Connection
        throw SQLException("Connection explicitly aborted by Octavius", "08000")
    }

    override fun setNetworkTimeout(executor: Executor?, milliseconds: Int) { // required by Hikari
        checkClosed()
        if (milliseconds < 0) throw OctaviusJdbcException(
            JdbcExceptionMessage.INVALID_TIMEOUT,
            details = "Network timeout cannot be negative"
        )
        stream.networkTimeout = milliseconds
    }

    override fun getNetworkTimeout(): Int { // required by Hikari
        checkClosed()
        return stream.networkTimeout
    }

    internal fun cancelQuery() {
        checkClosed()
        try {
            val cancelStream = PgStream(stream.host, stream.port)
            cancelStream.sendMessage(CancelRequestMessage(stream.processId, stream.secretKey))
            cancelStream.flush()
            cancelStream.close()
        } catch (e: Exception) {
            // Ignore errors during cancellation
        }
    }

    //------------------------------------------SEARCH PATH-------------------------------------------------------------

    /**
     * Parses a PostgreSQL search_path parameter string which may contain quoted identifiers
     * and commas within quotes (e.g., '"$user", public', '"schema,with,comma"').
     */
    private fun parseSearchPath(param: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < param.length) {
            val c = param[i]
            if (c == '"') {
                if (inQuotes && i + 1 < param.length && param[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trimEnd())
                current.clear()
            } else if (c.isWhitespace() && !inQuotes && current.isEmpty()) {
                // skip leading whitespace
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString().trimEnd())
        return result.filter { it.isNotEmpty() }
    }

    /**
     * Retrieves the current search path. Since we enforce PostgreSQL 18+, this value
     * is always kept up-to-date automatically via ParameterStatus messages from the server.
     *
     * @return A list of schema names representing the current search path.
     */
    internal fun getSearchPath(): List<String> {
        checkClosed()
        val paramSearchPath = stream.parameters["search_path"]
        if (paramSearchPath != null) {
            if (paramSearchPath == lastSearchPathString && cachedSearchPath != null) {
                return cachedSearchPath!!
            }
            val parsed = parseSearchPath(paramSearchPath)
            lastSearchPathString = paramSearchPath
            cachedSearchPath = parsed
            return parsed
        }
        // Fallback in rare cases (e.g., mocked test server)
        return listOf("public")
    }

    /**
     * Sets the current search path for this connection.
     *
     * The framework relies on PostgreSQL 18+ ParameterStatus to automatically track the search path,
     * so executing SET search_path will implicitly update the driver's state. This method is a convenient helper.
     *
     * @param schemas An array of schema names to be set as the new search path.
     *                If empty, the search path is reset to DEFAULT.
     */
    internal fun setSearchPath(vararg schemas: String) {
        checkClosed()
        if (schemas.isEmpty()) {
            queryExecutor.execute("SET search_path TO DEFAULT")
        } else {
            val pathStr = schemas.joinToString(", ") { it.quoteAsPgIdentifier() }
            queryExecutor.execute("SET search_path TO $pathStr")
        }
    }

    //--------------------------------------------READ ONLY-------------------------------------------------------------
    private var readOnlyFlag: Boolean = false

    override fun setReadOnly(readOnly: Boolean) { // required by Hikari
        checkClosed()
        if (this.readOnlyFlag != readOnly) {
            val modeStr = if (readOnly) "READ ONLY" else "READ WRITE"
            val query = buildString {
                append("SET SESSION CHARACTERISTICS AS TRANSACTION $modeStr")
                if (transactionState == TransactionState.IN_TRANSACTION) {
                    append("; SET TRANSACTION $modeStr")
                }
            }
            queryExecutor.execute(query)
            this.readOnlyFlag = readOnly
        }
    }

    override fun isReadOnly(): Boolean { // required by Hikari
        checkClosed()
        return readOnlyFlag
    }

    //-----------------------------------------TRANSACTIONS-------------------------------------------------------------

    private var autoCommitFlag: Boolean = true

    private var transactionIsolationLevel: Int = Connection.TRANSACTION_READ_COMMITTED

    val transactionState: TransactionState
        get() = TransactionState.fromChar(queryExecutor.transactionStatus)


    override fun setAutoCommit(autoCommit: Boolean) { // required by Hikari
        checkClosed()
        if (this.autoCommitFlag != autoCommit) {
            this.autoCommitFlag = autoCommit
            if (autoCommit) {
                queryExecutor.execute("COMMIT")
            } else {
                queryExecutor.execute("BEGIN")
            }
        }
    }

    override fun getAutoCommit(): Boolean { // required by Hikari
        checkClosed()
        return autoCommitFlag
    }

    override fun commit() {
        checkClosed()
        if (autoCommitFlag) throw OctaviusJdbcException(JdbcExceptionMessage.AUTO_COMMIT_VIOLATION)
        queryExecutor.execute("COMMIT; BEGIN")
    }

    override fun rollback() { // required by Hikari
        checkClosed()
        if (autoCommitFlag) throw OctaviusJdbcException(JdbcExceptionMessage.AUTO_COMMIT_VIOLATION)
        queryExecutor.execute("ROLLBACK; BEGIN")
    }

    override fun setTransactionIsolation(level: Int) { // required by Hikari
        checkClosed()
        val levelStr = when (level) {
            Connection.TRANSACTION_READ_UNCOMMITTED -> "READ UNCOMMITTED"
            Connection.TRANSACTION_READ_COMMITTED -> "READ COMMITTED"
            Connection.TRANSACTION_REPEATABLE_READ -> "REPEATABLE READ"
            Connection.TRANSACTION_SERIALIZABLE -> "SERIALIZABLE"
            else -> throw OctaviusJdbcException(JdbcExceptionMessage.UNSUPPORTED_ISOLATION_LEVEL)
        }
        val query = buildString {
            append("SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL $levelStr")
            if (transactionState == TransactionState.IN_TRANSACTION) {
                append("; SET TRANSACTION ISOLATION LEVEL $levelStr")
            }
        }
        queryExecutor.execute(query)
        this.transactionIsolationLevel = level
    }

    override fun getTransactionIsolation(): Int { // required by Hikari
        checkClosed()
        return transactionIsolationLevel
    }

    //------------------------------------------SCHEMA AND CATALOG------------------------------------------------------
    override fun setSchema(schema: String?) {
        checkClosed()
    }

    override fun getSchema(): String {
        checkClosed(); return "public"
    } // required by Hikari

    override fun setCatalog(catalog: String?) {
        checkClosed()
    } // required by Hikari

    override fun getCatalog(): String {
        checkClosed(); return "octavius"
    }  // required by Hikari

    //-------------------------------------------------SAVEPOINTS-------------------------------------------------------
    override fun setSavepoint(): Savepoint = unsupported()
    override fun setSavepoint(name: String?): Savepoint = unsupported()
    override fun rollback(savepoint: Savepoint?) = unsupported()
    override fun releaseSavepoint(savepoint: Savepoint?) = unsupported()

    //--------------------------STATEMENT (SUPPORTED ONLY UPDATE AND EXECUTE)-------------------------------------------
    // Support for basic Statement is needed for connection pools (e.g., HikariCP connectionInitSql)
    override fun createStatement(): Statement {
        checkClosed()
        return OctaviusStatement(this)
    }

    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int): Statement {
        checkClosed()
        return OctaviusStatement(this)
    }

    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): Statement {
        checkClosed()
        return OctaviusStatement(this)
    }

    //-------------------------------------NOT IMPLEMENTED--------------------------------------------------------------
    private fun unsupported(): Nothing =
        throw OctaviusJdbcException(JdbcExceptionMessage.FEATURE_NOT_SUPPORTED)

    // Replaced by typeManager
    override fun createArrayOf(typeName: String?, elements: Array<out Any>?): java.sql.Array = unsupported()
    override fun createStruct(typeName: String?, attributes: Array<out Any>?): Struct = unsupported()

    override fun createSQLXML(): SQLXML = unsupported()

    // Postgres does not have these types
    override fun createClob(): Clob = unsupported()
    override fun createBlob(): Blob = unsupported()
    override fun createNClob(): NClob = unsupported()

    override fun prepareStatement(sql: String?): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, resultSetType: Int, resultSetConcurrency: Int): PreparedStatement =
        unsupported()

    override fun prepareStatement(
        sql: String?,
        resultSetType: Int,
        resultSetConcurrency: Int,
        resultSetHoldability: Int
    ): PreparedStatement = unsupported()

    override fun prepareStatement(sql: String?, autoGeneratedKeys: Int): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, columnIndexes: IntArray?): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, columnNames: Array<out String>?): PreparedStatement = unsupported()
    override fun prepareCall(sql: String?): CallableStatement = unsupported()
    override fun prepareCall(sql: String?, resultSetType: Int, resultSetConcurrency: Int): CallableStatement =
        unsupported()

    override fun prepareCall(
        sql: String?,
        resultSetType: Int,
        resultSetConcurrency: Int,
        resultSetHoldability: Int
    ): CallableStatement = unsupported()

    // No support for ResultSet
    override fun setHoldability(holdability: Int) = unsupported()
    override fun getHoldability(): Int = unsupported()

    // Based on TypeRegistry
    override fun getTypeMap(): MutableMap<String, Class<*>> = unsupported()
    override fun setTypeMap(map: MutableMap<String, Class<*>>?) = unsupported()
}
