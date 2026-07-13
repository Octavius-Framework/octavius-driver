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
    private val rawConnection: Connection,
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

    // ------------------------------------------Pool Connection--------------------------------------------------------

    override var autoCommit: Boolean
        get() = rawConnection.autoCommit
        set(value) {
            rawConnection.autoCommit = value
        }

    override var readOnly: Boolean
        get() = rawConnection.isReadOnly
        set(value) {
            rawConnection.isReadOnly = value
        }

    override var transactionIsolationLevel: Int
        get() = rawConnection.transactionIsolation
        set(value) {
            rawConnection.transactionIsolation = value
        }

    override var networkTimeout: Int
        get() = rawConnection.networkTimeout
        set(value) {
            rawConnection.setNetworkTimeout(Executors.newVirtualThreadPerTaskExecutor(), value)
        }

    override fun isValid(timeout: Int): Boolean = rawConnection.isValid(timeout)

    override fun commit() = rawConnection.commit()

    override fun rollback() = rawConnection.rollback()

    // -------------------------------------------Close/Abort-----------------------------------------------------------

    override fun abort() {
        try {
            rawConnection.abort(Executors.newVirtualThreadPerTaskExecutor())
        } catch (e: Exception) {
            // Internal exception
        }
    }

    override fun close() {
        try {
            if (octaviusConnection.isClosed || octaviusConnection.stream.isBroken) {
                // If the underlying connection is already flagged as closed/broken,
                // we force an abort on the pool connection to evict it from the pool.
                abort()
            } else {
                rawConnection.close()
            }
        } catch (ignored: Exception) {
        }
    }
}
