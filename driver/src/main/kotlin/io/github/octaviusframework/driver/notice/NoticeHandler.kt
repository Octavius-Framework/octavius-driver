package io.github.octaviusframework.driver.notice

/**
 * Interface for handling PostgreSQL notices.
 * Implementations can be provided via the connection URL property `noticeHandler`.
 * 
 * **Note:** The `handleNotice` method is executed synchronously on the connection's network thread. 
 * Any long-running or blocking operations should be offloaded to another thread to avoid 
 * blocking further message processing from the database.
 */
interface NoticeHandler {
    fun handleNotice(notice: PgNotice)
}
