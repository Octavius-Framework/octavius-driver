package io.github.octaviusframework.driver.session

import io.github.octaviusframework.driver.transaction.OctaviusSavepoint

import io.github.octaviusframework.driver.notification.NotificationManager
import io.github.octaviusframework.driver.query.NamedParameterQuery
import io.github.octaviusframework.driver.query.NativeQuery
import io.github.octaviusframework.driver.transaction.TransactionManager
import io.github.octaviusframework.driver.type.TypeManager

interface OctaviusSessionOperations {
    val types: TypeManager
    val notifications: NotificationManager
    val transaction: TransactionManager

    fun reloadTypes()
    fun createNativeQuery(sql: String): NativeQuery
    fun createNamedQuery(sql: String): NamedParameterQuery
    fun cancelQuery()

    fun getSearchPath(): List<String>
    fun setSearchPath(vararg schemas: String)

    /**
     * Manually aborts the connection, forcing the underlying connection pool (like HikariCP)
     * to evict it instead of returning it to the pool.
     */
    fun abort()

    val transactionState: TransactionState
    var transactionIsolationLevel: Int
    var readOnly: Boolean
    var networkTimeout: Int
    fun isValid(timeout: Int): Boolean
}

interface OctaviusSession : OctaviusSessionOperations, AutoCloseable {
    var autoCommit: Boolean
    fun commit()
    fun rollback()

    fun setSavepoint(): OctaviusSavepoint
    fun setSavepoint(name: String): OctaviusSavepoint
    fun rollback(savepoint: OctaviusSavepoint)
    fun releaseSavepoint(savepoint: OctaviusSavepoint)
}

enum class TransactionState {
    IDLE,
    IN_TRANSACTION,
    FAILED;

    companion object {
        fun fromChar(c: Char): TransactionState = when (c) {
            'I' -> IDLE
            'T' -> IN_TRANSACTION
            'E' -> FAILED
            else -> throw IllegalArgumentException("Unknown transaction state: $c")
        }
    }
}
