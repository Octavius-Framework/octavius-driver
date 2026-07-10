package io.github.octaviusframework.driver.session
import io.github.octaviusframework.driver.exception.OctaviusException
import io.github.octaviusframework.driver.transaction.OctaviusSavepoint

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
        TypeManager(octaviusConnection.typeRegistry) { octaviusConnection.getSearchPath() }
    }

    override val notifications: NotificationManager by lazy {
        NotificationManager(octaviusConnection)
    }

    override val transaction: TransactionManager by lazy {
        TransactionManager(this)
    }

    override val transactionState: TransactionState
        get() = octaviusConnection.transactionState

    override var transactionIsolationLevel: Int
        get() = octaviusConnection.transactionIsolation
        set(value) {
            octaviusConnection.transactionIsolation = value
        }

    override var autoCommit: Boolean
        get() = octaviusConnection.autoCommit
        set(value) {
            octaviusConnection.autoCommit = value
        }

    override fun commit() = octaviusConnection.commit()

    override fun rollback() = octaviusConnection.rollback()

    private var savepointIdCounter: Int = 1

    override fun setSavepoint(): OctaviusSavepoint {
        octaviusConnection.checkClosed()
        if (autoCommit) throw OctaviusException("Cannot set a savepoint when auto-commit is enabled")
        val sp = OctaviusSavepoint(savepointIdCounter++)
        octaviusConnection.queryExecutor.execute("SAVEPOINT ${sp.pgName}")
        return sp
    }

    override fun setSavepoint(name: String): OctaviusSavepoint {
        octaviusConnection.checkClosed()
        if (autoCommit) throw OctaviusException("Cannot set a savepoint when auto-commit is enabled")
        val sp = OctaviusSavepoint(name)
        octaviusConnection.queryExecutor.execute("SAVEPOINT ${sp.pgName}")
        return sp
    }

    override fun rollback(savepoint: OctaviusSavepoint) {
        octaviusConnection.checkClosed()
        if (autoCommit) throw OctaviusException("Cannot rollback to a savepoint when auto-commit is enabled")
        octaviusConnection.queryExecutor.execute("ROLLBACK TO SAVEPOINT ${savepoint.pgName}")
    }

    override fun releaseSavepoint(savepoint: OctaviusSavepoint) {
        octaviusConnection.checkClosed()
        if (autoCommit) throw OctaviusException("Cannot release a savepoint when auto-commit is enabled")
        octaviusConnection.queryExecutor.execute("RELEASE SAVEPOINT ${savepoint.pgName}")
    }

    override fun reloadTypes() {
        GlobalTypeRegistry.reload(
            octaviusConnection.url,
            octaviusConnection.queryExecutor,
            octaviusConnection.getSearchPath()
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
    
    override fun getSearchPath() = octaviusConnection.getSearchPath()

    override fun setSearchPath(vararg schemas: String) = octaviusConnection.setSearchPath(*schemas)

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
