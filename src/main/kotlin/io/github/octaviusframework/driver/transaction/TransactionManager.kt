package io.github.octaviusframework.driver.transaction

import io.github.octaviusframework.driver.jdbc.OctaviusConnection

class TransactionManager(@PublishedApi internal val connection: OctaviusConnection) {

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
    inline operator fun <T> invoke(block: OctaviusConnection.() -> T): T {
        val initialAutoCommit = connection.autoCommit

        return if (!initialAutoCommit) {
            val sp = connection.setSavepoint()
            try {
                val result = connection.block()
                connection.releaseSavepoint(sp)
                result
            } catch (e: Throwable) {
                connection.rollback(sp)
                throw e
            }
        } else {
            connection.autoCommit = false
            try {
                val result = connection.block()
                // commited by autocommit = true
                result
            } catch (e: Throwable) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }
}