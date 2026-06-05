package io.github.octaviusframework.jdbc

import io.github.octaviusframework.network.PgStream
import io.github.octaviusframework.query.QueryExecutor
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

    private var isClosedFlag: Boolean = false
    private var autoCommitFlag: Boolean = true
    private var transactionIsolationLevel: Int = Connection.TRANSACTION_READ_COMMITTED

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

    private fun unsupported(): Nothing = throw SQLFeatureNotSupportedException("This feature is not supported by Octavius JDBC Driver")

    override fun createStatement(): Statement = unsupported()
    override fun prepareStatement(sql: String?): PreparedStatement = unsupported()
    override fun prepareCall(sql: String?): CallableStatement = unsupported()
    override fun nativeSQL(sql: String?): String = sql ?: ""
    
    override fun setAutoCommit(autoCommit: Boolean) {
        checkClosed()
        if (this.autoCommitFlag != autoCommit) {
            this.autoCommitFlag = autoCommit
            if (autoCommit) {
                queryExecutor.execute("COMMIT")
            }
        }
    }

    override fun getAutoCommit(): Boolean {
        checkClosed()
        return autoCommitFlag
    }

    override fun commit() {
        checkClosed()
        if (autoCommitFlag) throw SQLException("Connection is in auto-commit mode")
        queryExecutor.execute("COMMIT")
    }

    override fun rollback() {
        checkClosed()
        if (autoCommitFlag) throw SQLException("Connection is in auto-commit mode")
        queryExecutor.execute("ROLLBACK")
    }

    override fun close() {
        if (!isClosedFlag) {
            isClosedFlag = true
            stream.close()
        }
    }

    override fun isClosed(): Boolean = isClosedFlag
    
    override fun getMetaData(): DatabaseMetaData = TODO("Not yet implemented")
    override fun setReadOnly(readOnly: Boolean) = TODO("Not yet implemented")
    override fun isReadOnly(): Boolean = TODO("Not yet implemented")
    override fun setCatalog(catalog: String?) {  /* no-op */ }
    override fun getCatalog(): String = TODO("Not yet implemented")
    override fun setTransactionIsolation(level: Int) {
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

    override fun getTransactionIsolation(): Int {
        checkClosed()
        return transactionIsolationLevel
    }
    override fun getWarnings(): SQLWarning? = TODO("Not yet implemented")
    override fun clearWarnings() = TODO("Not yet implemented")
    
    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int): Statement = unsupported()
    override fun prepareStatement(sql: String?, resultSetType: Int, resultSetConcurrency: Int): PreparedStatement = unsupported()
    override fun prepareCall(sql: String?, resultSetType: Int, resultSetConcurrency: Int): CallableStatement = unsupported()
    
    override fun getTypeMap(): MutableMap<String, Class<*>> = unsupported()
    override fun setTypeMap(map: MutableMap<String, Class<*>>?) = unsupported()
    override fun setHoldability(holdability: Int) = unsupported()
    override fun getHoldability(): Int = unsupported()
    override fun setSavepoint(): Savepoint = unsupported()
    override fun setSavepoint(name: String?): Savepoint = unsupported()
    override fun rollback(savepoint: Savepoint?) = unsupported()
    override fun releaseSavepoint(savepoint: Savepoint?) = unsupported()
    
    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): Statement = unsupported()
    override fun prepareStatement(sql: String?, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): PreparedStatement = unsupported()
    override fun prepareCall(sql: String?, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): CallableStatement = unsupported()
    override fun prepareStatement(sql: String?, autoGeneratedKeys: Int): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, columnIndexes: IntArray?): PreparedStatement = unsupported()
    override fun prepareStatement(sql: String?, columnNames: Array<out String>?): PreparedStatement = unsupported()
    
    override fun createClob(): Clob = unsupported()
    override fun createBlob(): Blob = unsupported()
    override fun createNClob(): NClob = unsupported()
    override fun createSQLXML(): SQLXML = unsupported()
    
    override fun isValid(timeout: Int): Boolean = TODO("Not yet implemented")
    override fun setClientInfo(name: String?, value: String?) = unsupported()
    override fun setClientInfo(properties: Properties?) = unsupported()
    override fun getClientInfo(name: String?): String = unsupported()
    override fun getClientInfo(): Properties = Properties()
    
    override fun createArrayOf(typeName: String?, elements: Array<out Any>?): java.sql.Array = unsupported()
    override fun createStruct(typeName: String?, attributes: Array<out Any>?): Struct = unsupported()
    
    override fun setSchema(schema: String?) = TODO("Not yet implemented")
    override fun getSchema(): String = TODO("Not yet implemented")
    
    override fun abort(executor: Executor?) = unsupported()
    override fun setNetworkTimeout(executor: Executor?, milliseconds: Int) = TODO("Not yet implemented")
    override fun getNetworkTimeout(): Int = TODO("Not yet implemented")
}
