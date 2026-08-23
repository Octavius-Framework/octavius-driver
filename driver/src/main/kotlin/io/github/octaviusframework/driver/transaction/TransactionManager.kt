package io.github.octaviusframework.driver.transaction

import io.github.octaviusframework.driver.session.OctaviusSession
import io.github.octaviusframework.driver.session.OctaviusSessionOperations
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * A high-level API for managing transactions via scoped blocks.
 * 
 * This manager provides robust block-based transaction scoping, such as 
 * [required] and [nested], automatically handling commit, rollback, and 
 * savepoints based on the execution result. Within these scopes, the receiver 
 * is restricted to [OctaviusSessionOperations], preventing manual interference 
 * with the transaction lifecycle.
 * 
 * If you need manual, low-level transaction control (e.g., explicit `commit()`, 
 * `rollback()`, `autoCommit` manipulation, or manual savepoints), use the methods 
 * provided directly on the parent [OctaviusSession].
 */
class TransactionManager internal constructor(@PublishedApi internal val session: OctaviusSession) {

    /**
     * Executes the given [block] within a transaction scope.
     * 
     * If a transaction is currently active (autoCommit = false), the block will be
     * executed within the existing transaction. Otherwise, a new transaction is started,
     * and it will be committed upon successful completion, or rolled back if an exception occurs.
     * 
     * Inside the block, manual transaction operations such as `commit`, `rollback`, 
     * and `autoCommit` modifications are not accessible as the receiver is restricted
     * to [OctaviusSessionOperations]. A `return` out of the block does not compile either - use
     * `return@required`, since an early exit would otherwise commit whatever the block had already
     * done, which is the same decision the restricted receiver is there to keep out of the block.
     * 
     * @param block The block of code to execute.
     * @return The result of the block.
     */
    inline fun <T> required(crossinline block: OctaviusSessionOperations.() -> T): T {
        if (!session.autoCommit) return session.block()

        session.autoCommit = false
        var failure: Throwable? = null
        try {
            val result = session.block()
            session.commit()
            return result
        } catch (e: Throwable) {
            failure = e
            try {
                session.rollback()
            } catch (rollbackFailure: Throwable) {
                e.addSuppressed(rollbackFailure)
            }
            throw e
        } finally {
            restoreAutoCommit(failure)
        }
    }

    /**
     * Executes the given [block] within a nested transaction scope.
     * 
     * If a transaction is already active, a savepoint is created. Upon successful
     * completion of the block, the savepoint is released. If an exception occurs,
     * the transaction is rolled back to the savepoint. If no transaction is active,
     * a new one is started similar to [required].
     * 
     * Inside the block, manual transaction operations are not accessible as the receiver
     * is restricted to [OctaviusSessionOperations]. A `return` out of the block does not compile
     * either - use `return@nested`, since an early exit would otherwise slip past the release and
     * the rollback alike and leave the savepoint standing.
     * 
     * @param block The block of code to execute.
     * @return The result of the block.
     */
    inline fun <T> nested(crossinline block: OctaviusSessionOperations.() -> T): T {
        if (!session.autoCommit) {
            val sp = session.setSavepoint()
            try {
                val result = session.block()
                session.releaseSavepoint(sp)
                return result
            } catch (e: Throwable) {
                try {
                    session.rollback(sp)
                } catch (rollbackFailure: Throwable) {
                    e.addSuppressed(rollbackFailure)
                }
                throw e
            }
        }

        return required(block)
    }

    /**
     * Puts auto-commit back once a scope is over, however it ended.
     *
     * A failure here is only rethrown when the scope was already failing, and then as a suppressed
     * exception on [failure] rather than in its place. Out of a scope that **committed** it is logged
     * and goes no further. What it commits there is the empty transaction the successful `commit()`
     * left open behind it, which the server has no reason to refuse - so what fails is the socket,
     * and a broken socket is already on the record: `checkClosed` raises `NetworkException` the next
     * time anything touches this session, logged or not. Raising it here would only report a failed
     * transaction for one whose work is in the database, and invite a retry that writes it twice.
     */
    @PublishedApi
    internal fun restoreAutoCommit(failure: Throwable?) {
        try {
            session.autoCommit = true
        } catch (restoreFailure: Throwable) {
            if (failure != null) {
                failure.addSuppressed(restoreFailure)
            } else {
                logger.warn(restoreFailure) {
                    "Transaction committed, but auto-commit could not be restored - this session is no longer safe to reuse"
                }
            }
        }
    }
}