package io.github.octaviusframework.jdbc

import io.github.octaviusframework.network.PgStream
import io.github.octaviusframework.query.QueryExecutor
import io.github.octaviusframework.query.get
import io.github.octaviusframework.types.GlobalTypeRegistry
import java.sql.*
import java.util.Properties
import java.util.concurrent.Executor

class OctaviusConnection(private val stream: PgStream, private val url: String) : Connection {
    val typeRegistry = GlobalTypeRegistry.getRegistry(url)
    val queryExecutor = QueryExecutor(stream, typeRegistry)

    init {
        GlobalTypeRegistry.ensureLoaded(url, queryExecutor)
    }

    private var isClosedFlag: Boolean = false
    private var readOnlyFlag: Boolean = false


    private fun checkClosed() {
        if (isClosedFlag) throw SQLException("Connection is closed")
    }

    fun reloadTypes() {
        GlobalTypeRegistry.reload(url, queryExecutor)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> unwrap(iface: Class<T>): T {
        if (iface.isInstance(this)) {
            return this as T
        }
        throw SQLException("Cannot unwrap to ${iface.name}")
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
    
    override fun isValid(timeout: Int): Boolean = TODO("Not yet implemented") // required by Hikari
    override fun setClientInfo(name: String?, value: String?) = unsupported()
    override fun setClientInfo(properties: Properties?) = unsupported()
    override fun getClientInfo(name: String?): String = unsupported()
    override fun getClientInfo(): Properties = Properties()

    
    override fun abort(executor: Executor?) = unsupported()
    override fun setNetworkTimeout(executor: Executor?, milliseconds: Int) = TODO("Not yet implemented") // required by Hikari
    override fun getNetworkTimeout(): Int = TODO("Not yet implemented") // required by Hikari

    //--------------------------------------------READ ONLY-------------------------------------------------------------

    override fun setReadOnly(readOnly: Boolean) { // required by Hikari
        checkClosed()
        if (this.readOnlyFlag != readOnly) {
            val modeStr = if (readOnly) "READ ONLY" else "READ WRITE"
            queryExecutor.execute("SET SESSION CHARACTERISTICS AS TRANSACTION $modeStr")
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
            }
        }
    }

    override fun getAutoCommit(): Boolean { // required by Hikari
        checkClosed()
        return autoCommitFlag
    }

    override fun commit() {
        checkClosed()
        if (autoCommitFlag) throw SQLException("Connection is in auto-commit mode")
        queryExecutor.execute("COMMIT")
    }

    override fun rollback() { // required by Hikari
        checkClosed()
        if (autoCommitFlag) throw SQLException("Connection is in auto-commit mode")
        queryExecutor.execute("ROLLBACK")
    }

    override fun setTransactionIsolation(level: Int) { // required by Hikari
        checkClosed()
        val levelStr = when (level) {
            Connection.TRANSACTION_READ_UNCOMMITTED -> "READ UNCOMMITTED"
            Connection.TRANSACTION_READ_COMMITTED -> "READ COMMITTED"
            Connection.TRANSACTION_REPEATABLE_READ -> "REPEATABLE READ"
            Connection.TRANSACTION_SERIALIZABLE -> "SERIALIZABLE"
            else -> throw SQLException("Unsupported transaction isolation level")
        }
        queryExecutor.execute("SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL $levelStr")
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
        if (autoCommitFlag) throw SQLException("Cannot set a savepoint when auto-commit is enabled")
        val sp = OctaviusSavepoint(savepointIdCounter++)
        queryExecutor.execute("SAVEPOINT ${sp.pgName}")
        return sp
    }

    override fun setSavepoint(name: String?): Savepoint {
        checkClosed()
        if (autoCommitFlag) throw SQLException("Cannot set a savepoint when auto-commit is enabled")
        if (name == null) throw SQLException("Savepoint name cannot be null")
        val sp = OctaviusSavepoint(name)
        queryExecutor.execute("SAVEPOINT ${sp.pgName}")
        return sp
    }

    override fun rollback(savepoint: Savepoint?) {
        checkClosed()
        if (autoCommitFlag) throw SQLException("Cannot rollback to a savepoint when auto-commit is enabled")
        if (savepoint !is OctaviusSavepoint) throw SQLException("Unsupported savepoint type")
        queryExecutor.execute("ROLLBACK TO SAVEPOINT ${savepoint.pgName}")
    }

    override fun releaseSavepoint(savepoint: Savepoint?) {
        checkClosed()
        if (autoCommitFlag) throw SQLException("Cannot release a savepoint when auto-commit is enabled")
        if (savepoint !is OctaviusSavepoint) throw SQLException("Unsupported savepoint type")
        queryExecutor.execute("RELEASE SAVEPOINT ${savepoint.pgName}")
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
        val searchPath = result[0].get<String>(0)
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

    //-------------------------------------NOT IMPLEMENTED--------------------------------------------------------------
    private fun unsupported(): Nothing = throw SQLFeatureNotSupportedException("This feature is not supported by Octavius JDBC Driver")
    // zastąpione za pomocą io.github.octaviusframework.container.ContainerFactory.kt
    override fun createArrayOf(typeName: String?, elements: Array<out Any>?): java.sql.Array = unsupported()
    override fun createStruct(typeName: String?, attributes: Array<out Any>?): Struct = unsupported()

    // Postgres nie posiada takich typów
    override fun createClob(): Clob = unsupported()
    override fun createBlob(): Blob = unsupported()
    override fun createNClob(): NClob = unsupported()

    // Brak wsparcia dla standardowych Statement, wymuszają one result set
    override fun createStatement(): Statement = unsupported() // Used by hikari when connectionTestQuery is set
    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int): Statement = unsupported()
    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): Statement = unsupported()
    override fun prepareStatement(sql: String?): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, resultSetType: Int, resultSetConcurrency: Int): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, autoGeneratedKeys: Int): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, columnIndexes: IntArray?): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, columnNames: Array<out String>?): PreparedStatement = unsupported()
    override fun prepareCall(sql: String?): CallableStatement = unsupported()
    override fun prepareCall(sql: String?, resultSetType: Int, resultSetConcurrency: Int): CallableStatement = unsupported()
    override fun prepareCall(sql: String?, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): CallableStatement = unsupported()

    // Brak wsparcia dla ResultSet
    override fun setHoldability(holdability: Int) = unsupported()
    override fun getHoldability(): Int = unsupported()
    // Oparte o TypeRegistry
    override fun getTypeMap(): MutableMap<String, Class<*>> = unsupported()
    override fun setTypeMap(map: MutableMap<String, Class<*>>?) = unsupported()
}
