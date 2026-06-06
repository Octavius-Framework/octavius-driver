package io.github.octaviusframework.jdbc

import io.github.octaviusframework.network.PgStream
import io.github.octaviusframework.query.QueryExecutor
import io.github.octaviusframework.query.OctaviusQuery
import io.github.octaviusframework.query.get
import io.github.octaviusframework.types.GlobalTypeRegistry
import io.github.octaviusframework.exceptions.OctaviusJdbcException
import io.github.octaviusframework.exceptions.JdbcExceptionMessage
import io.github.octaviusframework.types.TypeSerializer
import io.github.octaviusframework.types.quoteAsPgIdentifier
import java.sql.*
import java.util.Properties
import java.util.concurrent.Executor

/**
 * Represents a connection to a database within the Octavius Framework.
 * It implements the standard JDBC [Connection] interface but overrides
 * certain behaviors to fit the framework's architecture.
 */
class OctaviusConnection(private val stream: PgStream, private val url: String) : Connection {
    val typeRegistry = GlobalTypeRegistry.getRegistry(url)
    val queryExecutor = QueryExecutor(stream, typeRegistry)

    init {
        GlobalTypeRegistry.ensureLoaded(url, queryExecutor)
    }

    private var isClosedFlag: Boolean = false
    private var readOnlyFlag: Boolean = false


    private fun checkClosed() {
        if (isClosedFlag) throw OctaviusJdbcException(JdbcExceptionMessage.CONNECTION_CLOSED)
    }

    fun reloadTypes() {
        GlobalTypeRegistry.reload(url, queryExecutor)
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
    private var savedSearchPath: List<String>? = null

    /**
     * Retrieves the current search path. If it hasn't been cached yet,
     * it queries the database to fetch the currently active schemas.
     *
     * @return A list of schema names representing the current search path.
     */
    fun getSearchPath(): List<String> {
        checkClosed()
        if (savedSearchPath == null) {
            val result = queryExecutor.query("SELECT unnest(current_schemas(false))")
            savedSearchPath = result.map { it.get<String>(0) }
        }
        return savedSearchPath!!
    }

    /**
     * Sets the current search path for this connection.
     *
     * The search path should be updated using this method instead of executing a raw SQL query.
     * The framework caches this value and uses it internally for resolving OIDs when a database
     * schema is not explicitly provided.
     *
     * @param schemas An array of schema names to be set as the new search path.
     *                If empty, the search path is reset to DEFAULT.
     */
    fun setSearchPath(vararg schemas: String) {
        checkClosed()
        if (schemas.isEmpty()) {
            queryExecutor.execute("SET search_path TO DEFAULT")
            this.savedSearchPath = null
        } else {
            val pathStr = schemas.joinToString(", ") { it.quoteAsPgIdentifier() }
            queryExecutor.execute("SET search_path TO $pathStr")
            this.savedSearchPath = schemas.toList()
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
        val result = queryExecutor.query("SELECT current_schema()")
        val searchPath = result[0].get<String?>(0)
        return searchPath
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
        typeRegistry.registerSerializer(serializer)
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
