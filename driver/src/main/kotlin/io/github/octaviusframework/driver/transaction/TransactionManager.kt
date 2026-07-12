package io.github.octaviusframework.driver.transaction

import io.github.octaviusframework.driver.session.OctaviusSession

class TransactionManager(@PublishedApi internal val session: OctaviusSession) {

    /**
     * Executes the given block within a transaction.
     * If already in a transaction, a savepoint is implicitly created for nested execution.
     * If the block completes successfully, the transaction (or savepoint) is committed/released.
     * If an exception is thrown, the transaction is rolled back (or rolled back to the savepoint).
     *
     * @param T The return type of the block.
     * @param block The code block to execute within the transaction, with the connection as the receiver.
     * @return The result of the block.
     */
    inline operator fun <T> invoke(block: OctaviusSession.() -> T): T {
        val initialAutoCommit = session.autoCommit

        return if (!initialAutoCommit) {
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