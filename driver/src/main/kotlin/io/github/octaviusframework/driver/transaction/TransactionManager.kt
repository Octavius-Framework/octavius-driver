package io.github.octaviusframework.driver.transaction

import io.github.octaviusframework.driver.session.OctaviusSession
import io.github.octaviusframework.driver.session.OctaviusSessionOperations

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
class TransactionManager(@PublishedApi internal val session: OctaviusSession) {

    /**
     * Executes the given [block] within a transaction scope.
     * 
     * If a transaction is currently active (autoCommit = false), the block will be
     * executed within the existing transaction. Otherwise, a new transaction is started,
     * and it will be committed upon successful completion, or rolled back if an exception occurs.
     * 
     * Inside the block, manual transaction operations such as `commit`, `rollback`, 
     * and `autoCommit` modifications are not accessible as the receiver is restricted
     * to [OctaviusSessionOperations].
     * 
     * @param block The block of code to execute.
     * @return The result of the block.
     */
    inline fun <T> required(block: OctaviusSessionOperations.() -> T): T {
        return if (!session.autoCommit) {
            session.block()
        } else {
            session.autoCommit = false
            try {
                val result = session.block()
                session.commit()
                result
            } catch (e: Throwable) {
                session.rollback()
                throw e
            } finally {
                session.autoCommit = true
            }
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
     * is restricted to [OctaviusSessionOperations].
     * 
     * @param block The block of code to execute.
     * @return The result of the block.
     */
    inline fun <T> nested(block: OctaviusSessionOperations.() -> T): T {
        return if (!session.autoCommit) {
            val sp = session.setSavepoint()
            try {
                val result = session.block()
                session.releaseSavepoint(sp)
                result
            } catch (e: Throwable) {
                session.rollback(sp)
                throw e
            }
        } else {
            session.autoCommit = false
            try {
                val result = session.block()
                session.commit()
                result
            } catch (e: Throwable) {
                session.rollback()
                throw e
            } finally {
                session.autoCommit = true
            }
        }
    }

}