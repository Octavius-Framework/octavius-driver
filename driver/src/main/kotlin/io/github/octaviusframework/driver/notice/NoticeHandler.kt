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
    /**
     * Called for every notice the server sends on a connection this handler is installed on.
     *
     * A handler declared as a Kotlin `object` is shared by every connection using it, so notices from a
     * whole pool arrive through this one method; [PgNotice.processId] is what tells them apart.
     *
     * @param notice The notice received.
     */
    fun handleNotice(notice: PgNotice)
}
