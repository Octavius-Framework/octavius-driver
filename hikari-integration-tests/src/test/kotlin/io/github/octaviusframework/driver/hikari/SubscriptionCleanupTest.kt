package io.github.octaviusframework.driver.hikari

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.session.OctaviusSession
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `LISTEN` registrations live on the physical connection, which outlives a pooled session.
 * Closing such a session must drop them, or the next borrower inherits subscriptions it never
 * asked for and receives the previous borrower's notifications.
 */
class SubscriptionCleanupTest {

    private fun pool() = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:octavius://localhost:5432/octavius_test"
        username = "postgres"
        password = "1234"
        maximumPoolSize = 1   // guarantees the same physical connection comes back
        minimumIdle = 1
    })

    private fun OctaviusSession.listeningChannels(): Long =
        createNativeQuery("SELECT count(*) FROM pg_listening_channels()").fetchFieldStrict()

    private fun OctaviusSession.backendPid(): Int =
        createNativeQuery("SELECT pg_backend_pid()").fetchFieldStrict()

    @Test
    fun `a returned connection carries no subscriptions from its previous borrower`() {
        pool().use { pool ->
            val first = pool.getOctaviusSession()
            val pid = first.backendPid()
            first.notifications.listen("cleanup_chan", "cleanup_chan_2")
            assertEquals(2L, first.listeningChannels(), "the subscriptions should be live while in use")
            first.close()

            val second = pool.getOctaviusSession()
            assertEquals(pid, second.backendPid(), "expected the same physical connection back")
            assertEquals(0L, second.listeningChannels(), "the previous borrower's subscriptions leaked")
            second.close()
        }
    }

    @Test
    fun `a session that never subscribed still closes cleanly`() {
        pool().use { pool ->
            val session = pool.getOctaviusSession()
            assertEquals(1, session.createNativeQuery("SELECT 1").fetchFieldStrict<Int>())
            session.close()

            val next = pool.getOctaviusSession()
            assertEquals(0L, next.listeningChannels())
            next.close()
        }
    }

    @Test
    fun `unlistening by hand before closing is not undone twice`() {
        pool().use { pool ->
            val session = pool.getOctaviusSession()
            session.notifications.listen("cleanup_chan_3")
            session.notifications.unlistenAll()
            assertEquals(0L, session.listeningChannels())
            session.close()

            val next = pool.getOctaviusSession()
            assertTrue(next.createNativeQuery("SELECT 1").fetchFieldStrict<Int>() == 1)
            assertEquals(0L, next.listeningChannels())
            next.close()
        }
    }
}
