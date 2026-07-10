package io.github.octaviusframework.driver.session

import io.github.octaviusframework.driver.identifier.quoteAsPgIdentifier
import io.github.octaviusframework.driver.jdbc.OctaviusConnection
import io.github.octaviusframework.driver.notification.NotificationManager
import io.github.octaviusframework.driver.query.NamedParameterQuery
import io.github.octaviusframework.driver.query.NativeQuery
import io.github.octaviusframework.driver.registry.GlobalTypeRegistry
import io.github.octaviusframework.driver.transaction.TransactionManager
import io.github.octaviusframework.driver.type.TypeManager
import java.sql.Connection
import java.util.concurrent.Executors


internal class OctaviusSessionImpl(
    private val poolConnection: Connection,
    private val octaviusConnection: OctaviusConnection
) : OctaviusSession {

    override val types: TypeManager by lazy {
        TypeManager(octaviusConnection.typeRegistry) { getSearchPath() }
    }

    override val notifications: NotificationManager by lazy {
        NotificationManager(octaviusConnection)
    }

    override val transaction: TransactionManager by lazy {
        TransactionManager(octaviusConnection)
    }

    override fun reloadTypes() {
        GlobalTypeRegistry.reload(
            octaviusConnection.url,
            octaviusConnection.queryExecutor,
            getSearchPath()
        )
    }

    override fun createNativeQuery(sql: String): NativeQuery {
        octaviusConnection.checkClosed()
        return NativeQuery(sql, octaviusConnection.queryExecutor, types)
    }

    override fun createNamedQuery(sql: String): NamedParameterQuery {
        octaviusConnection.checkClosed()
        return NamedParameterQuery(sql, octaviusConnection.queryExecutor, types)
    }

    override fun cancelQuery() {
        octaviusConnection.cancelQuery()
    }

    private var lastSearchPathString: String? = null
    private var cachedSearchPath: List<String>? = null

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

    override fun getSearchPath(): List<String> {
        octaviusConnection.checkClosed()
        val paramSearchPath = octaviusConnection.stream.parameters["search_path"]
        if (paramSearchPath != null) {
            if (paramSearchPath == lastSearchPathString && cachedSearchPath != null) {
                return cachedSearchPath!!
            }
            val parsed = parseSearchPath(paramSearchPath)
            lastSearchPathString = paramSearchPath
            cachedSearchPath = parsed
            return parsed
        }
        return listOf("public")
    }

    override fun setSearchPath(vararg schemas: String) {
        octaviusConnection.checkClosed()
        if (schemas.isEmpty()) {
            octaviusConnection.queryExecutor.execute("SET search_path TO DEFAULT")
        } else {
            val pathStr = schemas.joinToString(", ") { it.quoteAsPgIdentifier() }
            octaviusConnection.queryExecutor.execute("SET search_path TO $pathStr")
        }
    }

    override fun abort() {
        try {
            poolConnection.abort(Executors.newVirtualThreadPerTaskExecutor())
        } catch (e: Exception) {
            // Fallback if abort is not supported
            poolConnection.close()
        }
    }

    override fun close() {
        if (octaviusConnection.isClosed || octaviusConnection.stream.isBroken) {
            // If the underlying connection is already flagged as closed/broken,
            // we force an abort on the pool connection to evict it from the pool.
            abort()
        } else {
            poolConnection.close()
        }
    }
}
