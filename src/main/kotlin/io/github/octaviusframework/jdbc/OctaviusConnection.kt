package io.github.octaviusframework.jdbc

import io.github.octaviusframework.network.PgStream
import java.sql.*
import java.util.Properties
import java.util.concurrent.Executor

class OctaviusConnection(private val stream: PgStream) : Connection {
    @Suppress("UNCHECKED_CAST")
    override fun <T> unwrap(iface: Class<T>): T {
        if (iface.isInstance(this)) {
            return this as T
        }
        throw SQLException("Cannot unwrap to ${iface.name}")
    }

    override fun isWrapperFor(iface: Class<*>): Boolean = iface.isInstance(this)

    fun executeExtendedQuery(sql: String, params: List<ByteArray?> = emptyList()): List<io.github.octaviusframework.network.messages.DataRowMessage> {
        val statementName = "stmt1"
        val portalName = "portal1"
        
        stream.sendMessage(io.github.octaviusframework.network.messages.ParseMessage(statementName, sql))
        stream.sendMessage(io.github.octaviusframework.network.messages.BindMessage(
            portalName, 
            statementName, 
            params, 
            listOf(0), // zakładamy tekstowe wejście dla uproszczenia (ale docelowo binarne 1)
            listOf(1)  // żądamy wyjścia binarnego 1
        ))
        stream.sendMessage(io.github.octaviusframework.network.messages.DescribeMessage('P', portalName))
        stream.sendMessage(io.github.octaviusframework.network.messages.ExecuteMessage(portalName, 0))
        stream.sendMessage(io.github.octaviusframework.network.messages.SyncMessage())
        
        // Pętla odczytu odpowiedzi
        val rows = mutableListOf<io.github.octaviusframework.network.messages.DataRowMessage>()
        var rowDescription: io.github.octaviusframework.network.messages.RowDescriptionMessage? = null
        
        while (true) {
            val msg = stream.receiveMessage()
            when (msg) {
                is io.github.octaviusframework.network.messages.ParseCompleteMessage -> println("ParseComplete")
                is io.github.octaviusframework.network.messages.BindCompleteMessage -> println("BindComplete")
                is io.github.octaviusframework.network.messages.RowDescriptionMessage -> {
                    rowDescription = msg
                    println("RowDescription: ${msg.fields.size} fields")
                }
                is io.github.octaviusframework.network.messages.NoDataMessage -> println("NoData (np. od INSERT/UPDATE)")
                is io.github.octaviusframework.network.messages.DataRowMessage -> rows.add(msg)
                is io.github.octaviusframework.network.messages.CommandCompleteMessage -> println("CommandComplete: ${msg.tag}")
                is io.github.octaviusframework.network.messages.ErrorResponseMessage -> throw java.sql.SQLException("Błąd bazy: ${msg.message}")
                is io.github.octaviusframework.network.messages.ReadyForQueryMessage -> {
                    println("ReadyForQuery, transakcja zakończona.")
                    break // wychodzimy z pętli Sync
                }
                else -> println("Ignoruje niespodziewana wiadomosc w trakcie zapytania: $msg")
            }
        }
        return rows
    }

    override fun createStatement(): Statement = TODO("Not yet implemented")
    override fun prepareStatement(sql: String?): PreparedStatement = TODO("Not yet implemented")
    override fun prepareCall(sql: String?): CallableStatement = TODO("Not yet implemented")
    override fun nativeSQL(sql: String?): String = TODO("Not yet implemented")
    override fun setAutoCommit(autoCommit: Boolean) = TODO("Not yet implemented")
    override fun getAutoCommit(): Boolean = TODO("Not yet implemented")
    override fun commit() = TODO("Not yet implemented")
    override fun rollback() = TODO("Not yet implemented")
    override fun close() = TODO("Not yet implemented")
    override fun isClosed(): Boolean = TODO("Not yet implemented")
    override fun getMetaData(): DatabaseMetaData = TODO("Not yet implemented")
    override fun setReadOnly(readOnly: Boolean) = TODO("Not yet implemented")
    override fun isReadOnly(): Boolean = TODO("Not yet implemented")
    override fun setCatalog(catalog: String?) = TODO("Not yet implemented")
    override fun getCatalog(): String = TODO("Not yet implemented")
    override fun setTransactionIsolation(level: Int) = TODO("Not yet implemented")
    override fun getTransactionIsolation(): Int = TODO("Not yet implemented")
    override fun getWarnings(): SQLWarning? = TODO("Not yet implemented")
    override fun clearWarnings() = TODO("Not yet implemented")
    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int): Statement = TODO("Not yet implemented")
    override fun prepareStatement(sql: String?, resultSetType: Int, resultSetConcurrency: Int): PreparedStatement = TODO("Not yet implemented")
    override fun prepareCall(sql: String?, resultSetType: Int, resultSetConcurrency: Int): CallableStatement = TODO("Not yet implemented")
    override fun getTypeMap(): MutableMap<String, Class<*>> = TODO("Not yet implemented")
    override fun setTypeMap(map: MutableMap<String, Class<*>>?) = TODO("Not yet implemented")
    override fun setHoldability(holdability: Int) = TODO("Not yet implemented")
    override fun getHoldability(): Int = TODO("Not yet implemented")
    override fun setSavepoint(): Savepoint = TODO("Not yet implemented")
    override fun setSavepoint(name: String?): Savepoint = TODO("Not yet implemented")
    override fun rollback(savepoint: Savepoint?) = TODO("Not yet implemented")
    override fun releaseSavepoint(savepoint: Savepoint?) = TODO("Not yet implemented")
    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): Statement = TODO("Not yet implemented")
    override fun prepareStatement(sql: String?, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): PreparedStatement = TODO("Not yet implemented")
    override fun prepareCall(sql: String?, resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): CallableStatement = TODO("Not yet implemented")
    override fun prepareStatement(sql: String?, autoGeneratedKeys: Int): PreparedStatement = TODO("Not yet implemented")
    override fun prepareStatement(sql: String?, columnIndexes: IntArray?): PreparedStatement = TODO("Not yet implemented")
    override fun prepareStatement(sql: String?, columnNames: Array<out String>?): PreparedStatement = TODO("Not yet implemented")
    override fun createClob(): Clob = TODO("Not yet implemented")
    override fun createBlob(): Blob = TODO("Not yet implemented")
    override fun createNClob(): NClob = TODO("Not yet implemented")
    override fun createSQLXML(): SQLXML = TODO("Not yet implemented")
    override fun isValid(timeout: Int): Boolean = TODO("Not yet implemented")
    override fun setClientInfo(name: String?, value: String?) = TODO("Not yet implemented")
    override fun setClientInfo(properties: Properties?) = TODO("Not yet implemented")
    override fun getClientInfo(name: String?): String = TODO("Not yet implemented")
    override fun getClientInfo(): Properties = TODO("Not yet implemented")
    override fun createArrayOf(typeName: String?, elements: Array<out Any>?): java.sql.Array = TODO("Not yet implemented")
    override fun createStruct(typeName: String?, attributes: Array<out Any>?): Struct = TODO("Not yet implemented")
    override fun setSchema(schema: String?) = TODO("Not yet implemented")
    override fun getSchema(): String = TODO("Not yet implemented")
    override fun abort(executor: Executor?) = TODO("Not yet implemented")
    override fun setNetworkTimeout(executor: Executor?, milliseconds: Int) = TODO("Not yet implemented")
    override fun getNetworkTimeout(): Int = TODO("Not yet implemented")
}
