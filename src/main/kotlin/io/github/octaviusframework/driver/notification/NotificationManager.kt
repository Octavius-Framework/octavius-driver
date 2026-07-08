package io.github.octaviusframework.driver.notification

import io.github.octaviusframework.driver.io.virtualDispatcher
import io.github.octaviusframework.driver.jdbc.OctaviusConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException

class NotificationManager(private val connection: OctaviusConnection) {

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

        withContext(dispatcher ?: virtualDispatcher) {
            val originalTimeout = connection.stream.networkTimeout
            try {
                connection.stream.networkTimeout = pollTimeoutMs

                while (currentCoroutineContext().isActive && !connection.isClosedFlag) {
                    try {
                        connection.stream.receiveMessage()
                    } catch (e: SocketTimeoutException) {
                        // Timeout is expected, loop continues and checks isActive
                    } catch (e: SocketException) {
                        // Socket was closed from the outside
                        break
                    } catch (e: IOException) {
                        // Connection dropped by network, server, or closed explicitly
                        break
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

    /**
     * Starts a listener loop that blocks indefinitely.
     * When the coroutine is cancelled, it simply closes the socket.
     *
     * @param dispatcher The coroutine dispatcher to run the loop on. Defaults to a virtual thread dispatcher if null.
     */
    suspend fun startInterruptibleListenerLoop(dispatcher: CoroutineDispatcher? = null) {
        if (connection.isClosedFlag) return

        withContext(dispatcher ?: virtualDispatcher) {
            val job = currentCoroutineContext()[Job]
            val completionHandle = job?.invokeOnCompletion {
                connection.close()
            }

            try {
                connection.stream.networkTimeout = 0

                while (currentCoroutineContext().isActive && !connection.isClosedFlag) {
                    try {
                        connection.stream.receiveMessage()
                    } catch (e: SocketException) {
                        break
                    } catch (e: IOException) {
                        break
                    }
                }
            } finally {
                completionHandle?.dispose()
                connection.close()
            }
        }
    }
}