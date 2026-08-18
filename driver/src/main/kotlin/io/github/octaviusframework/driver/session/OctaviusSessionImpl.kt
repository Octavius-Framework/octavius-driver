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
import io.github.oshai.kotlinlogging.KotlinLogging
import java.sql.Connection

private val logger = KotlinLogging.logger {}


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

    /** Ties a log line to the backend behind this session; see the same property on [OctaviusConnection]. */
    private val pid: String get() = "[PID: ${octaviusConnection.stream.processId}]"

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

    /**
     * Undoes the per-session state this session left on its connection, so the next borrower of
     * a pooled connection finds it as it was: a `COPY` that was never finished, any `LISTEN`
     * registrations, and a transaction opened by a hand-written `BEGIN`.
     *
     * Only reachable from [close]. Code that hands the connection back some other way - Spring's
     * `DataSourceUtils`, for instance - never closes the session and so never runs this.
     */
    private fun resetConnectionState() {
        copy.cancelActiveOperation()
        notifications.releaseSubscriptions()
        rollbackTransactionTheDriverNeverOpened()
    }

    /**
     * Undoes a transaction the driver did not open, which in practice means one started by a
     * hand-written `BEGIN`.
     *
     * With auto-commit on, neither this driver nor the pool believes a transaction exists, so
     * nothing else would clean it up and the connection would go back carrying somebody's
     * uncommitted work - which the next borrower could then commit without ever knowing. The
     * transaction status comes from the server's own `ReadyForQuery`, so noticing this costs
     * nothing; only actually finding one costs a round trip.
     *
     * It is rolled back rather than committed on purpose: the driver has no idea what that
     * work was, and discarding it is the recoverable mistake.
     */
    private fun rollbackTransactionTheDriverNeverOpened() {
        if (!autoCommit) return // a real manual transaction; the pool resets those itself
        if (transactionState == TransactionState.IDLE) return
        octaviusConnection.queryExecutor.execute("ROLLBACK")
    }

    override fun abort() {
        try {
            rawConnection.abort(OctaviusDispatchers.VirtualExecutor)
        } catch (_: SQLExceptionWrapper) {
            // Expected, and not a failure: aborting throws on purpose, that being the signal a
            // pool needs in order to evict the connection instead of taking it back. The abort
            // itself is already reported by the connection, so logging here would put a stack
            // trace under every routine teardown - a cancelled listener loop aborts on its way
            // out - and say nothing the line above it did not.
        } catch (e: Exception) {
            // Anything that is not the driver's own wrapper did not come from the abort protocol: a
            // rejected executor, a pool proxy in a state of its own. Then the connection really
            // was left as it stood, and nothing else records that.
            logger.debug(e) { "$pid Aborting the session raised" }
        }
    }

    override fun close() {
        try {
            if (octaviusConnection.isClosed || octaviusConnection.stream.isBroken) {
                // If the underlying connection is already flagged as closed/broken,
                // we force an abort on the pool connection to evict it from the pool.
                abort()
            } else {
                // Both of these outlive the session on a pooled connection, so they are undone
                // here instead of being left for whoever borrows it next: a COPY the caller
                // never finished, and any LISTEN registrations this session made.
                try {
                    resetConnectionState()
                } catch (e: Exception) {
                    // The connection cannot go back to the pool carrying this session's leftovers,
                    // so it is evicted instead - which the pool reports as a connection lost for
                    // no visible reason unless this line says why.
                    logger.warn(e) { "$pid Could not reset connection state on close; aborting the connection instead" }
                    abort()
                    return
                }
                rawConnection.close()
            }
        } catch (e: Exception) {
            logger.debug(e) { "$pid Closing the session raised" }
        }
    }
}
