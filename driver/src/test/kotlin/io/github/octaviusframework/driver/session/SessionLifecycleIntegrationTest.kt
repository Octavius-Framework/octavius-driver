package io.github.octaviusframework.driver.session

import io.github.octaviusframework.driver.exception.NetworkException
import io.github.octaviusframework.driver.exception.NetworkExceptionReason
import io.github.octaviusframework.driver.exception.StatementException
import io.github.octaviusframework.driver.exception.TransactionExceptionReason
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.row.get
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionLifecycleIntegrationTest {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @Test
    fun `should cancel long running query`() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            Thread.sleep(100) // wait for query to start
            session.cancelQuery()
        }

        val exception = assertFailsWith<io.github.octaviusframework.driver.exception.TransactionException> {
            session.createNativeQuery("SELECT pg_sleep(2)").fetchRowStrict()
        }
        
        // 57014 is query_canceled
        assertEquals("57014", exception.sqlState)
        assertEquals(TransactionExceptionReason.TIMEOUT, exception.reason)
        logger.error(exception) { "" }
        // Session should be usable after query cancellation
        val result = session.createNativeQuery("SELECT 1").fetchRowStrict().get<Int>(0)
        assertEquals(1, result)

        session.close()
        executor.shutdown()
    }

    @Test
    fun `should abort session`() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            Thread.sleep(100)
            session.abort()
        }

        val exception = assertFailsWith<NetworkException> {
            session.createNativeQuery("SELECT pg_sleep(2)").fetchRowStrict()
        }

        // Following queries should fail with CONNECTION_CLOSED since the stream is broken/aborted
        val nextException = assertFailsWith<NetworkException> {
            session.createNativeQuery("SELECT 1").fetchRowStrict()
        }

        assertEquals(NetworkExceptionReason.CONNECTION_CLOSED, nextException.reason)

        executor.shutdown()
    }
}
