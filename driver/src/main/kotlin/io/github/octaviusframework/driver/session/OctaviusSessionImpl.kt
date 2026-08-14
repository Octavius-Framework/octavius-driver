package io.github.octaviusframework.driver.session

import io.github.octaviusframework.driver.concurrent.OctaviusDispatchers
import io.github.octaviusframework.driver.copy.CopyManager
import io.github.octaviusframework.driver.exception.SQLExceptionWrapper
import io.github.octaviusframework.driver.jdbc.OctaviusConnection
import io.github.octaviusframework.driver.jdbc.unwrap
import io.github.octaviusframework.driver.lo.LargeObjectManager
import io.github.octaviusframework.driver.notification.NotificationManager
import io.github.octaviusframework.driver.query.NamedParameterQuery
import io.github.octaviusframework.driver.query.NativeQuery
import io.github.octaviusframework.driver.registry.GlobalTypeRegistry
import io.github.octaviusframework.driver.transaction.OctaviusSavepoint
import io.github.octaviusframework.driver.transaction.TransactionManager
import io.github.octaviusframework.driver.registry.TypeManager
import java.sql.Connection


/**
 * Internal implementation of the [OctaviusSession] interface.
 *
 * This class wraps a raw JDBC [Connection] (which could be pooled) and delegates 
 * operations to the underlying [OctaviusConnection].
 */
internal class OctaviusSessionImpl(
    private val rawConnection: Connection
) : OctaviusSession {

    internal val octaviusConnection: OctaviusConnection = rawConnection.unwrap()

    override val typeManager: TypeManager = TypeManager(octaviusConnection.typeRegistry) { octaviusConnection.getSearchPath() }

    override val notifications: NotificationManager = NotificationManager(this)

    override val copy: CopyManager = CopyManager(octaviusConnection.stream)

    override val largeObjects: LargeObjectManager = LargeObjectManager(this)

    override val transaction: TransactionManager = TransactionManager(this)

    override val transactionState: TransactionState
        get() = octaviusConnection.transactionState

    override fun setSavepoint(): OctaviusSavepoint {
        return unwrapSqlException { rawConnection.setSavepoint() as OctaviusSavepoint }
    }

    override fun setSavepoint(name: String): OctaviusSavepoint {
        return unwrapSqlException { rawConnection.setSavepoint(name) as OctaviusSavepoint }
    }

    override fun rollback(savepoint: OctaviusSavepoint) {
        unwrapSqlException { rawConnection.rollback(savepoint as java.sql.Savepoint) }
    }

    override fun releaseSavepoint(savepoint: OctaviusSavepoint) {
        unwrapSqlException { rawConnection.releaseSavepoint(savepoint as java.sql.Savepoint) }
    }

    override fun reloadTypes() {
        GlobalTypeRegistry.reload(
            octaviusConnection.registryKey,
            octaviusConnection.queryExecutor
        )
    }

    override fun createNativeQuery(sql: String): NativeQuery {
        octaviusConnection.checkClosed()
        return NativeQuery(sql, octaviusConnection.queryExecutor, typeManager)
    }

    override fun createNamedQuery(sql: String): NamedParameterQuery {
        octaviusConnection.checkClosed()
        return NamedParameterQuery(sql, octaviusConnection.queryExecutor, typeManager)
    }

    override fun cancelQuery() {
        octaviusConnection.cancelQuery()
    }

    override fun getSearchPath() = octaviusConnection.getSearchPath()

    override fun setSearchPath(vararg schemas: String) = octaviusConnection.setSearchPath(*schemas)

    // ------------------------------------------Pool Connection--------------------------------------------------------

    private inline fun <T> unwrapSqlException(block: () -> T): T {
        try {
            return block()
        } catch (e: SQLExceptionWrapper) {
            throw e.wrappedException
        }
    }

    override var autoCommit: Boolean
        get() = unwrapSqlException { rawConnection.autoCommit }
        set(value) {
            unwrapSqlException { rawConnection.autoCommit = value }
        }

    override var readOnly: Boolean
        get() = unwrapSqlException { rawConnection.isReadOnly }
        set(value) {
            unwrapSqlException { rawConnection.isReadOnly = value }
        }

    override var transactionIsolationLevel: TransactionIsolationLevel
        get() = unwrapSqlException { TransactionIsolationLevel.fromJdbcValue(rawConnection.transactionIsolation) }
        set(value) {
            unwrapSqlException { rawConnection.transactionIsolation = value.jdbcValue }
        }

    override var networkTimeout: Int
        get() = unwrapSqlException { rawConnection.networkTimeout }
        set(value) {
            unwrapSqlException { rawConnection.setNetworkTimeout(OctaviusDispatchers.VirtualExecutor, value) }
        }

    override fun isValid(timeout: Int): Boolean = rawConnection.isValid(timeout)

    override fun commit() = unwrapSqlException { rawConnection.commit() }

    override fun rollback() = unwrapSqlException { rawConnection.rollback() }

    // -------------------------------------------Close/Abort-----------------------------------------------------------

    override fun abort() {
        try {
            rawConnection.abort(OctaviusDispatchers.VirtualExecutor)
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
                // A COPY the caller never finished leaves the connection in copy mode.
                // Resynchronize it, or evict it if that fails - otherwise the next borrower
                // of a pooled connection inherits a transfer in progress.
                try {
                    copy.cancelActiveOperation()
                } catch (_: Exception) {
                    abort()
                    return
                }
                rawConnection.close()
            }
        } catch (_: Exception) {}
    }
}
