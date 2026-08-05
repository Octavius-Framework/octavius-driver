package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NetworkExceptionIntegrationTest {

    private fun getSession(props: OctaviusProperties = OctaviusProperties()) = getOctaviusSession(
        "jdbc:octavius://localhost:5432/octavius_test",
        props.apply {
            user = "postgres"
            password = "1234"
        }
    )

    @Test
    fun `should throw NetworkException with CONNECTION_TIMEOUT when socket timeout is reached`() {
        val props = OctaviusProperties().apply {
            socketTimeout = 1 // Ustawiamy 1 sekundę timeout na socket
        }

        getSession(props).use { session ->
            val exception = assertFailsWith<NetworkException> {
                session.createNativeQuery("SELECT pg_sleep(2)").fetchRows()
            }

            assertEquals(NetworkExceptionReason.CONNECTION_TIMEOUT, exception.reason)
        }
    }

    @Test
    fun `should throw NetworkException when backend connection is abruptly terminated`() {
        getSession().use { session1 ->
            getSession().use { session2 ->
                val pid = session1.createNativeQuery("SELECT pg_backend_pid()").fetchFieldStrict<Int>()

                session2.createNativeQuery("SELECT pg_terminate_backend($pid)").fetchRowStrict()

                val exception = assertFailsWith<NetworkException> {
                    session1.createNativeQuery("SELECT 1").fetchRowStrict()
                }

                assertTrue(
                    exception.reason == NetworkExceptionReason.CONNECTION_CLOSED_BY_PEER ||
                    exception.reason == NetworkExceptionReason.CONNECTION_ERROR,
                    "Expectiong CONNECTION_CLOSED_BY_PEER or CONNECTION_ERROR, got: ${exception.reason}"
                )
            }
        }
    }
}
