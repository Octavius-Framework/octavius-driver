package io.github.octaviusframework.jdbc

import io.github.octaviusframework.network.PgStream
import io.github.octaviusframework.query.QueryExecutor
import io.github.octaviusframework.query.OctaviusQuery
import io.github.octaviusframework.query.get
import io.github.octaviusframework.types.GlobalTypeRegistry
import io.github.octaviusframework.exceptions.OctaviusJdbcException
import io.github.octaviusframework.exceptions.JdbcExceptionMessage
import io.github.octaviusframework.io.virtualDispatcher
import io.github.octaviusframework.types.TypeSerializer
import io.github.octaviusframework.types.quoteAsPgIdentifier
import java.sql.*
import java.util.Properties
import java.util.concurrent.Executor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharedFlow
import io.github.octaviusframework.network.messages.NotificationResponseMessage
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.SocketException

/**
 * Represents a connection to a database within the Octavius Framework.
 * It implements the standard JDBC [Connection] interface but overrides
 * certain behaviors to fit the framework's architecture.
 */
class OctaviusConnection(private val stream: PgStream, private val url: String) : Connection {
    val typeRegistry = GlobalTypeRegistry.getRegistry(url)
    val queryExecutor = QueryExecutor(stream, typeRegistry)

    init {
        GlobalTypeRegistry.ensureLoaded(url, queryExecutor, getSearchPath())
    }

    private var isClosedFlag: Boolean = false
    private var readOnlyFlag: Boolean = false

    private var lastSearchPathString: String? = null
    private var cachedSearchPath: List<String>? = null


    private fun checkClosed() {
        if (isClosedFlag) throw OctaviusJdbcException(JdbcExceptionMessage.CONNECTION_CLOSED)
    }

    fun reloadTypes() {
        GlobalTypeRegistry.reload(url, queryExecutor, getSearchPath())
    }

    fun createQuery(sql: String): OctaviusQuery {
        checkClosed()
        return OctaviusQuery(sql, queryExecutor, typeRegistry)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> unwrap(iface: Class<T>): T {
        if (iface.isInstance(this)) {
            return this as T
        }
        throw OctaviusJdbcException(JdbcExceptionMessage.UNWRAP_ERROR, details = "Cannot unwrap to ${iface.name}")
    }

    override fun isWrapperFor(iface: Class<*>): Boolean = iface.isInstance(this)

    override fun nativeSQL(sql: String?): String = sql ?: ""

    override fun close() {
        if (!isClosedFlag) {
            isClosedFlag = true
            stream.close()
        }
    }

    override fun isClosed(): Boolean = isClosedFlag // required by Hikari
    
    override fun getMetaData(): DatabaseMetaData = unsupported()

    override fun getWarnings(): SQLWarning? = TODO("Not yet implemented")
    override fun clearWarnings() = TODO("Not yet implemented") // required by Hikari

    override fun createSQLXML(): SQLXML = unsupported()

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
            } catch (ignore: Exception) {}
        }
    }
    override fun setClientInfo(name: String?, value: String?) = unsupported()
    override fun setClientInfo(properties: Properties?) = unsupported()
    override fun getClientInfo(name: String?): String = unsupported()
    override fun getClientInfo(): Properties = Properties()

    
    override fun abort(executor: Executor?) = unsupported()

    override fun setNetworkTimeout(executor: Executor?, milliseconds: Int) { // required by Hikari
        checkClosed()
        if (milliseconds < 0) throw OctaviusJdbcException(JdbcExceptionMessage.INVALID_TIMEOUT, details = "Network timeout cannot be negative")
        stream.networkTimeout = milliseconds
    }
    
    override fun getNetworkTimeout(): Int { // required by Hikari
        checkClosed()
        return stream.networkTimeout
    }

    //------------------------------------------LISTEN/NOTIFY-----------------------------------------------------------
    val notifications: SharedFlow<NotificationResponseMessage>
        get() = stream.notifications

    suspend fun startBlockingListenerLoop(pollTimeoutMs: Int = 500, dispatcher: CoroutineDispatcher? = null) {
        if (isClosedFlag) return

        withContext(dispatcher ?: virtualDispatcher) {
            val originalTimeout = stream.networkTimeout
            try {
                stream.networkTimeout = pollTimeoutMs

                while (currentCoroutineContext().isActive && !isClosedFlag) {
                    try {
                        stream.receiveMessage()
                    } catch (e: SocketTimeoutException) {
                        // Timeout is expected, loop continues and checks isActive
                    } catch (e: SocketException) {
                        // Socket was closed from the outside
                        break
                    } catch (e: IOException) {
                        // Connection dropped by network, server, or closed explicitly
                        break
                    }
                }
            } finally {
                try {
                    if (!isClosedFlag) stream.networkTimeout = originalTimeout
                } catch (ignore: Exception) {}
            }
        }
    }

    //--------------------------------------------READ ONLY-------------------------------------------------------------

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

    enum class TransactionState {
        IDLE,
        IN_TRANSACTION,
        FAILED,
        UNKNOWN;

        companion object {
            fun fromChar(c: Char): TransactionState = when (c) {
                'I' -> IDLE
                'T' -> IN_TRANSACTION
                'E' -> FAILED
                else -> UNKNOWN
            }
        }
    }

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

    //-------------------------------------------------SAVEPOINTS-------------------------------------------------------
    private var savepointIdCounter: Int = 1

    override fun setSavepoint(): Savepoint {
        checkClosed()
        if (autoCommitFlag) throw OctaviusJdbcException(JdbcExceptionMessage.AUTO_COMMIT_VIOLATION, details = "Cannot set a savepoint when auto-commit is enabled")
        val sp = OctaviusSavepoint(savepointIdCounter++)
        queryExecutor.execute("SAVEPOINT ${sp.pgName}")
        return sp
    }

    override fun setSavepoint(name: String?): Savepoint {
        checkClosed()
        if (autoCommitFlag) throw OctaviusJdbcException(JdbcExceptionMessage.AUTO_COMMIT_VIOLATION, details = "Cannot set a savepoint when auto-commit is enabled")
        if (name == null) throw OctaviusJdbcException(JdbcExceptionMessage.UNWRAP_ERROR, details = "Savepoint name cannot be null")
        val sp = OctaviusSavepoint(name)
        queryExecutor.execute("SAVEPOINT ${sp.pgName}")
        return sp
    }

    override fun rollback(savepoint: Savepoint?) {
        checkClosed()
        if (autoCommitFlag) throw OctaviusJdbcException(JdbcExceptionMessage.AUTO_COMMIT_VIOLATION, details = "Cannot rollback to a savepoint when auto-commit is enabled")
        if (savepoint !is OctaviusSavepoint) throw OctaviusJdbcException(JdbcExceptionMessage.UNWRAP_ERROR, details = "Unsupported savepoint type")
        queryExecutor.execute("ROLLBACK TO SAVEPOINT ${savepoint.pgName}")
    }

    override fun releaseSavepoint(savepoint: Savepoint?) {
        checkClosed()
        if (autoCommitFlag) throw OctaviusJdbcException(JdbcExceptionMessage.AUTO_COMMIT_VIOLATION, details = "Cannot release a savepoint when auto-commit is enabled")
        if (savepoint !is OctaviusSavepoint) throw OctaviusJdbcException(JdbcExceptionMessage.UNWRAP_ERROR, details = "Unsupported savepoint type")
        queryExecutor.execute("RELEASE SAVEPOINT ${savepoint.pgName}")
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
    fun getSearchPath(): List<String> {
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
        // Fallback w rzadkim przypadku (np. mockowany serwer testowy)
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
    fun setSearchPath(vararg schemas: String) {
        checkClosed()
        if (schemas.isEmpty()) {
            queryExecutor.execute("SET search_path TO DEFAULT")
        } else {
            val pathStr = schemas.joinToString(", ") { it.quoteAsPgIdentifier() }
            queryExecutor.execute("SET search_path TO $pathStr")
        }
    }

    //------------------------------------------SCHEMA AND CATALOG------------------------------------------------------
    private var catalogName: String? = null

    override fun setSchema(schema: String?) {
        checkClosed()
        // no-op
    }

    override fun getSchema(): String? {
        checkClosed()
        return getSearchPath().firstOrNull()
    } // required by Hikari

    override fun setCatalog(catalog: String?) {  /* no-op */ } // required by Hikari

    override fun getCatalog(): String {
        checkClosed()
        if (catalogName == null) {
            val result = queryExecutor.query("SELECT current_database()")
            catalogName = result[0].get<String>("current_database")
        }
        return catalogName!!
    } // required by Hikari

    //---------------------------------------------------TYPES----------------------------------------------------------

    fun registerGlobalSerializer(serializer: TypeSerializer<*>) {
        typeRegistry.registerSerializer(serializer, getSearchPath())
    }

    fun registerGlobalConverter() {
        
    }


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
    private fun unsupported(): Nothing = throw SQLFeatureNotSupportedException("This feature is not supported by Octavius JDBC Driver")
    // Replaced by io.github.octaviusframework.container.ContainerFactory.kt
    override fun createArrayOf(typeName: String?, elements: Array<out Any>?): java.sql.Array = unsupported()
    override fun createStruct(typeName: String?, attributes: Array<out Any>?): Struct = unsupported()

    // Postgres does not have these types
    override fun createClob(): Clob = unsupported()
    override fun createBlob(): Blob = unsupported()
    override fun createNClob(): NClob = unsupported()

    override fun prepareStatement(sql: String?): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, resultSetType: Int, resultSetConcurrency: Int): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, autoGeneratedKeys: Int): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, columnIndexes: IntArray?): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, columnNames: Array<out String>?): PreparedStatement = unsupported()
    override fun prepareCall(sql: String?): CallableStatement = unsupported()
    override fun prepareCall(sql: String?, resultSetType: Int, resultSetConcurrency: Int): CallableStatement = unsupported()
    override fun prepareCall(sql: String?, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): CallableStatement = unsupported()

    // No support for ResultSet
    override fun setHoldability(holdability: Int) = unsupported()
    override fun getHoldability(): Int = unsupported()
    // Based on TypeRegistry
    override fun getTypeMap(): MutableMap<String, Class<*>> = unsupported()
    override fun setTypeMap(map: MutableMap<String, Class<*>>?) = unsupported()
}

inline fun <reified T: Any> Connection.unwrap(): T {
    return this.unwrap(T::class.java)
}