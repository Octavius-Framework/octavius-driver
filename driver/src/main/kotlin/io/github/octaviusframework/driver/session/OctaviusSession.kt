package io.github.octaviusframework.driver.session

import io.github.octaviusframework.driver.copy.CopyManager
import io.github.octaviusframework.driver.lo.LargeObjectManager
import io.github.octaviusframework.driver.notification.NotificationManager
import io.github.octaviusframework.driver.query.NamedParameterQuery
import io.github.octaviusframework.driver.query.NativeQuery
import io.github.octaviusframework.driver.transaction.OctaviusSavepoint
import io.github.octaviusframework.driver.transaction.TransactionManager
import io.github.octaviusframework.driver.registry.TypeManager

/**
 * Defines a set of core operations that can be executed on an active database session.
 * These operations do not include explicit transaction lifecycle management (commit, rollback, auto-commit).
 */
interface OctaviusSessionOperations {

    /**
     * Manages PostgreSQL types and their OIDs, providing functionality for registration and resolution.
     */
    val typeManager: TypeManager

    /**
     * Handles asynchronous PostgreSQL notifications (`LISTEN` / `NOTIFY` mechanism).
     */
    val notifications: NotificationManager

    /**
     * Provides an API for performing PostgreSQL COPY operations.
     */
    val copy: CopyManager

    /**
     * Provides an API for managing PostgreSQL Large Objects.
     */
    val largeObjects: LargeObjectManager

    /**
     * Provides a high-level API for executing operations within transaction scopes 
     * (e.g., `required`, `nested`). Automatically manages commits and rollbacks 
     * based on block execution outcomes.
     */
    val transaction: TransactionManager

    /**
     * Reloads the database types and updates internal OID mappings.
     */
    fun reloadTypes()

    /**
     * Creates a native query from the provided SQL string using standard PostgreSQL 
     * positional parameters (e.g. `$1`, `$2`).
     *
     * @param sql The SQL statement to prepare.
     * @return A newly created [NativeQuery].
     */
    fun createNativeQuery(sql: String): NativeQuery

    /**
     * Creates a query with named parameters (e.g., `@name`, `@id`) which will be translated
     * into positional parameters under the hood.
     *
     * @param sql The SQL statement containing named parameters.
     * @return A newly created [NamedParameterQuery].
     */
    fun createNamedQuery(sql: String): NamedParameterQuery

    /**
     * Attempts to cancel the currently executing query on this session.
     */
    fun cancelQuery()

    /**
     * Retrieves the current `search_path` configured for this session.
     *
     * @return A list of schemas comprising the current search path.
     */
    fun getSearchPath(): List<String>

    /**
     * Sets the `search_path` for this session to the provided schemas.
     *
     * @param schemas The schemas to set in the search path.
     */
    fun setSearchPath(vararg schemas: String)

    /**
     * Manually aborts the connection, forcing the underlying connection pool (like HikariCP)
     * to evict it instead of returning it to the pool.
     */
    fun abort()

    /**
     * The current transaction state of this session (Idle, In Transaction, or Failed).
     */
    val transactionState: TransactionState

    /**
     * The transaction isolation level for this session.
     */
    var transactionIsolationLevel: TransactionIsolationLevel

    /**
     * Indicates whether this session is currently in read-only mode.
     */
    var readOnly: Boolean

    /**
     * The network timeout (in milliseconds) for operations executed on this session.
     */
    var networkTimeout: Int

    /**
     * Checks if this session is still valid and the underlying connection is alive.
     *
     * @param timeout The maximum time in seconds to wait for a database response.
     * @return `true` if the session is valid, `false` otherwise.
     */
    fun isValid(timeout: Int): Boolean
}

/**
 * Represents an active database session extending standard operations with 
 * full lifecycle control and transaction management.
 */
interface OctaviusSession : OctaviusSessionOperations, AutoCloseable {

    /**
     * Specifies whether the session operates in auto-commit mode.
     * If `true`, each individual statement is treated as a separate transaction.
     */
    var autoCommit: Boolean

    /**
     * Commits the current transaction, persisting all changes made within it.
     */
    fun commit()

    /**
     * Rolls back the current transaction, discarding all changes made within it.
     */
    fun rollback()

    /**
     * Creates an unnamed savepoint within the current transaction and returns it.
     *
     * @return The created [OctaviusSavepoint].
     */
    fun setSavepoint(): OctaviusSavepoint

    /**
     * Creates a named savepoint within the current transaction and returns it.
     *
     * @param name The specific name to assign to the savepoint.
     * @return The created [OctaviusSavepoint].
     */
    fun setSavepoint(name: String): OctaviusSavepoint

    /**
     * Rolls back the transaction to the state at the time the given savepoint was created.
     *
     * @param savepoint The savepoint to roll back to.
     */
    fun rollback(savepoint: OctaviusSavepoint)

    /**
     * Releases the specified savepoint from the current transaction.
     *
     * @param savepoint The savepoint to release.
     */
    fun releaseSavepoint(savepoint: OctaviusSavepoint)
}

/**
 * Represents the current state of a database transaction.
 */
enum class TransactionState {
    /**
     * Session is idle, no active transaction block.
     */
    IDLE,

    /**
     * Session is inside an active transaction block.
     */
    IN_TRANSACTION,

    /**
     * Session is inside a failed transaction block, awaiting a `ROLLBACK`.
     */
    FAILED;

    companion object {
        /**
         * Converts the single-character protocol response into a [TransactionState].
         *
         * @param c The character code representing the transaction state ('I', 'T', or 'E').
         * @return The corresponding [TransactionState].
         * @throws IllegalArgumentException If an unknown state character is provided.
         */
        fun fromChar(c: Char): TransactionState = when (c) {
            'I' -> IDLE
            'T' -> IN_TRANSACTION
            'E' -> FAILED
            else -> error("Unknown transaction state: $c")
        }
    }
}

/**
 * Defines the available transaction isolation levels.
 */
enum class TransactionIsolationLevel(val jdbcValue: Int) {
    READ_UNCOMMITTED(1),
    READ_COMMITTED(2),
    REPEATABLE_READ(4),
    SERIALIZABLE(8);

    companion object {
        /**
         * Maps a JDBC isolation level integer to the corresponding [TransactionIsolationLevel].
         *
         * @param level The JDBC isolation level constant.
         * @return The matching [TransactionIsolationLevel].
         * @throws IllegalArgumentException if the provided level does not match any known level.
         */
        fun fromJdbcValue(level: Int): TransactionIsolationLevel {
            return entries.first { it.jdbcValue == level }
        }
    }
}
