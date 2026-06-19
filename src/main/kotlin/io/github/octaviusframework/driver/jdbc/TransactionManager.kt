package io.github.octaviusframework.driver.jdbc

import java.sql.Savepoint

class TransactionManager(@PublishedApi internal val connection: OctaviusConnection) {
    val state: OctaviusConnection.TransactionState
        get() = connection.transactionState

    /**
     * Executes the given block within a transaction.
     * If the block completes successfully, the transaction is committed.
     * If an exception is thrown, the transaction is rolled back.
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
            if (initialAutoCommit) { // TODO: Consider lazy transaction management in the future to avoid redundant empty COMMITs
                connection.autoCommit = true
            }
        }
    }

    /**
     * Executes the given block within a savepoint.
     * If the block completes successfully, the savepoint is released.
     * If an exception is thrown, the transaction is rolled back to the savepoint.
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
