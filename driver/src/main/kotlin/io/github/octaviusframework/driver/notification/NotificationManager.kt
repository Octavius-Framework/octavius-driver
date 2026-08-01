package io.github.octaviusframework.driver.notification

import io.github.octaviusframework.driver.concurrent.OctaviusDispatchers
import io.github.octaviusframework.driver.exception.NetworkException
import io.github.octaviusframework.driver.identifier.quoteAsPgIdentifier
import io.github.octaviusframework.driver.session.OctaviusSessionImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import kotlin.concurrent.withLock

class NotificationManager internal constructor(private val session: OctaviusSessionImpl) {

    private val connection get() = session.octaviusConnection

    /**
     * A [SharedFlow] of asynchronous notifications (LISTEN/NOTIFY) received from the database.
     */
    val messages: SharedFlow<PgNotification>
        get() = connection.stream.notifications

    /**
     * Starts a listener loop using active polling with a socket timeout.
     * When the coroutine is cancelled, the loop exits gracefully without closing
     * the underlying database connection, allowing it to be reused.
     *
     * @param pollTimeoutMs The socket timeout used for polling in milliseconds.
     * @param dispatcher The coroutine dispatcher to run the loop on. Defaults to a virtual thread dispatcher if null.
     */
    suspend fun startPollingListenerLoop(pollTimeoutMs: Int = 500, dispatcher: CoroutineDispatcher? = null) {
        if (connection.isClosedFlag) return

        withContext(dispatcher ?: OctaviusDispatchers.Virtual) {
            val context = currentCoroutineContext()
            connection.stream.lock.withLock {
                val originalTimeout = connection.stream.networkTimeout
                try {
                    connection.stream.networkTimeout = pollTimeoutMs

                    while (context.isActive && !connection.isClosedFlag) {
                        try {
                            connection.stream.pollMessage()
                        } catch (e: NetworkException) {
                            if (connection.isClosedFlag || !context.isActive) {
                                // Connection closed explicitly or coroutine cancelled
                                break
                            } else {
                                // Connection dropped by network or server
                                throw e
                            }
                        }
                    }
                } finally {
                    try {
                        if (!connection.isClosedFlag) connection.stream.networkTimeout = originalTimeout
                    } catch (ignore: Exception) {
                    }
                }
            }
        }
    }

    /**
     * Starts a listener loop that blocks indefinitely.
     * When the coroutine is cancelled, it simply closes the socket.
     *
     * @param dispatcher The coroutine dispatcher to run the loop on. Defaults to a virtual thread dispatcher if null.
     */
    suspend fun startInterruptibleListenerLoop(dispatcher: CoroutineDispatcher? = null) {
        if (connection.isClosedFlag) return

        withContext(dispatcher ?: OctaviusDispatchers.Virtual) {
            val cancelJob = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    session.abort()
                }
            }

            val context = currentCoroutineContext()
            connection.stream.lock.withLock {
                try {
                    connection.stream.networkTimeout = 0

                    while (context.isActive && !connection.isClosedFlag) {
                        try {
                            connection.stream.receiveMessage()
                        } catch (e: NetworkException) {
                            if (connection.isClosedFlag || !context.isActive) {
                                break
                            } else {
                                throw e
                            }
                        }
                    }
                } finally {
                    cancelJob.cancel()
                    session.abort()
                }
            }
        }
    }

    /**
     * Registers this connection to listen for notifications on the specified channel(s).
     */
    fun listen(vararg channels: String) {
        if (channels.isEmpty()) return
        val sql = channels.joinToString("; ") { "LISTEN ${it.quoteAsPgIdentifier()}" }
        session.createNativeQuery(sql).execute()
    }

    /**
     * Stops listening for notifications on the specified channel(s).
     */
    fun unlisten(vararg channels: String) {
        if (channels.isEmpty()) return
        val sql = channels.joinToString("; ") { "UNLISTEN ${it.quoteAsPgIdentifier()}" }
        session.createNativeQuery(sql).execute()
    }

    /**
     * Stops listening for all notifications on this connection.
     */
    fun unlistenAll() {
        session.createNativeQuery("UNLISTEN *").execute()
    }

    /**
     * Sends a notification to the specified channel, optionally with a payload string.
     */
    fun notify(channel: String, payload: String? = null) {
        session.createNativeQuery("SELECT pg_notify($1, $2)").fetchField<Unit>(channel, payload)
    }
}