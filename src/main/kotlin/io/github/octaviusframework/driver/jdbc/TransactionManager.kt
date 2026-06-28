package io.github.octaviusframework.driver.jdbc

import java.sql.Savepoint

class TransactionManager(@PublishedApi internal val connection: OctaviusConnection) {
    /**
     * The current transaction state of the connection.
     */
    val state: OctaviusConnection.TransactionState
        get() = connection.transactionState

    /**
     * Executes the given block within a transaction.
     * If the block completes successfully, the transaction is committed.
     * If an exception is thrown, the transaction is rolled back.
     *
     * @param T The return type of the block.
     * @param block The code block to execute within the transaction.
     * @return The result of the block.
     */
    inline fun <T> transaction(block: () -> T): T {
        val initialAutoCommit = connection.autoCommit
        if (initialAutoCommit) {
            connection.autoCommit = false
        }
        
        try {
            val result = block()
            connection.commit()
            return result
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            if (initialAutoCommit) {
                connection.autoCommit = true
            }
        }
    }

    /**
     * Executes the given block within a savepoint.
     * If the block completes successfully, the savepoint is released.
     * If an exception is thrown, the transaction is rolled back to the savepoint.
     *
     * @param T The return type of the block.
     * @param name Optional name for the savepoint.
     * @param block The code block to execute within the savepoint.
     * @return The result of the block.
     */
    inline fun <T> savepoint(name: String? = null, block: () -> T): T {
        val sp: Savepoint = if (name != null) {
            connection.setSavepoint(name)
        } else {
            connection.setSavepoint()
        }
        
        try {
            val result = block()
            connection.releaseSavepoint(sp)
            return result
        } catch (e: Exception) {
            connection.rollback(sp)
            throw e
        }
    }
}
