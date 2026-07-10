package io.github.octaviusframework.driver.session

import io.github.octaviusframework.driver.notification.NotificationManager
import io.github.octaviusframework.driver.query.NamedParameterQuery
import io.github.octaviusframework.driver.query.NativeQuery
import io.github.octaviusframework.driver.transaction.TransactionManager
import io.github.octaviusframework.driver.type.TypeManager

interface OctaviusSession : AutoCloseable {
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
}
