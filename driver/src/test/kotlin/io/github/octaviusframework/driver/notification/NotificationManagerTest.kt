package io.github.octaviusframework.driver.notification

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Test
import java.util.Properties
import kotlin.test.assertEquals

class NotificationManagerTest {

    @Test
    fun testPollingListener() = runBlocking {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"

        val listenerSession = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)
        val notifierSession = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        listenerSession.notifications.listen("test_channel")

        val pollingJob = launch {
            listenerSession.notifications.startPollingListenerLoop(100)
        }

        val notificationDeferred = async {
            listenerSession.notifications.messages.first { it.channel == "test_channel" }
        }

        // Allow some time for the listener loop and flow collection to start
        delay(300)

        notifierSession.notifications.notify("test_channel", "hello_polling")

        val notification = withTimeout(2000) {
            notificationDeferred.await()
        }

        assertEquals("test_channel", notification.channel)
        assertEquals("hello_polling", notification.payload)

        pollingJob.cancelAndJoin()
        listenerSession.close()
        notifierSession.close()
    }

    @Test
    fun testInterruptibleListener() = runBlocking {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"

        val listenerSession = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)
        val notifierSession = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        listenerSession.notifications.listen("test_channel_int")

        val listenerJob = launch {
            listenerSession.notifications.startInterruptibleListenerLoop()
        }

        val notificationDeferred = async {
            listenerSession.notifications.messages.first { it.channel == "test_channel_int" }
        }

        // Allow some time for the listener loop and flow collection to start
        delay(300)

        notifierSession.notifications.notify("test_channel_int", "hello_interruptible")

        val notification = withTimeout(2000) {
            notificationDeferred.await()
        }

        assertEquals("test_channel_int", notification.channel)
        assertEquals("hello_interruptible", notification.payload)

        listenerJob.cancelAndJoin()
        // startInterruptibleListenerLoop closes the socket upon cancellation, so we shouldn't explicitly close it without expecting errors or it's fine.
        // Session aborts on cancel, so listenerSession might be already closed or aborting.
        notifierSession.close()
    }
}
