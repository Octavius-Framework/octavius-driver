package io.github.octaviusframework.jdbc

import io.github.octaviusframework.network.PgStream
import io.github.octaviusframework.query.QueryExecutor
import java.sql.*
import java.util.Properties
import java.util.concurrent.Executor

class OctaviusConnection(private val stream: PgStream) : Connection {
    val queryExecutor = QueryExecutor(stream)
    val typeRegistry = io.github.octaviusframework.types.TypeRegistry()

    init {
        loadTypeRegistry()
    }

    private fun loadTypeRegistry() {
        val typesSql = "SELECT oid, typname, typrelid, typelem, typarray FROM pg_catalog.pg_type"
        val typesResult = queryExecutor.executeExtendedQuery(typesSql)
        
        for (row in typesResult.rows) {
            if (row.columns.size < 5) continue
            val oidBytes = row.columns[0] ?: continue
            val nameBytes = row.columns[1] ?: continue
            val typrelidBytes = row.columns[2] ?: continue
            val typelemBytes = row.columns[3] ?: continue
            val typarrayBytes = row.columns[4] ?: continue
            
            val oid = io.github.octaviusframework.types.IntDecoder.decodeBinary(oidBytes)
            val name = io.github.octaviusframework.types.StringDecoder.decodeBinary(nameBytes)
            val typrelid = io.github.octaviusframework.types.IntDecoder.decodeBinary(typrelidBytes)
            val typelem = io.github.octaviusframework.types.IntDecoder.decodeBinary(typelemBytes)
            val typarray = io.github.octaviusframework.types.IntDecoder.decodeBinary(typarrayBytes)
            
            typeRegistry.types[oid] = io.github.octaviusframework.types.PgType(oid, name, typrelid, typelem, typarray)
        }

        // Krok 2: Pobranie struktury kompozytów z pg_attribute
        val attrSql = "SELECT attrelid, attnum, attname, atttypid FROM pg_catalog.pg_attribute WHERE attnum > 0 AND attisdropped = false ORDER BY attrelid, attnum"
        val attrResult = queryExecutor.executeExtendedQuery(attrSql)

        for (row in attrResult.rows) {
            if (row.columns.size < 4) continue
            val attrelidBytes = row.columns[0] ?: continue
            val attnumBytes = row.columns[1] ?: continue // wewnętrznie int2 (smallint), zajmuje 2 bajty w binarce? Zobaczmy co przyjdzie.
            val attnameBytes = row.columns[2] ?: continue
            val atttypidBytes = row.columns[3] ?: continue

            val attrelid = io.github.octaviusframework.types.IntDecoder.decodeBinary(attrelidBytes)
            // attnum to int2, więc binarne 2 bajty, musimy zdekodować jako short:
            val attnum = java.nio.ByteBuffer.wrap(attnumBytes).short.toInt()
            val attname = io.github.octaviusframework.types.StringDecoder.decodeBinary(attnameBytes)
            val atttypid = io.github.octaviusframework.types.IntDecoder.decodeBinary(atttypidBytes)

            val attr = io.github.octaviusframework.types.PgAttribute(attrelid, attnum, attname, atttypid)
            typeRegistry.relationAttributes.getOrPut(attrelid) { mutableListOf() }.add(attr)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> unwrap(iface: Class<T>): T {
        if (iface.isInstance(this)) {
            return this as T
        }
        throw SQLException("Cannot unwrap to ${iface.name}")
    }

    override fun isWrapperFor(iface: Class<*>): Boolean = iface.isInstance(this)

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
