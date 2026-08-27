package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DriverExceptionIntegrationTest {

    companion object {
        val logger = KotlinLogging.logger {}
    }

    private fun getSession() = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", OctaviusProperties().apply {
        user = "postgres"
        password = "1234"
    })

    @Test
    fun `should throw InvalidOperationException when calling execute on query that returns rows`() {
        getSession().use { session ->
            val exception = assertFailsWith<InvalidOperationException> {
                session.createNativeQuery("SELECT 1").execute()
            }
            logger.error(exception) { "" }
            assertEquals(InvalidOperationExceptionReason.UNEXPECTED_RESULT, exception.reason)
        }
    }

    @Test
    fun `should throw InitializationException for invalid credentials`() {
        val exception = assertFailsWith<InitializationException> {
            getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", OctaviusProperties().apply {
                user = "postgres"
                password = "wrong_password"
            })
        }
        logger.error(exception) { "" }
        assertEquals(InitializationExceptionReason.SERVER_REJECTED_CREDENTIALS, exception.reason)
        assertEquals("28P01", exception.sqlState) // Invalid password state
    }

    @Test
    fun `should throw InitializationException with CONNECTION_ERROR for unreachable host`() {
        val exception = assertFailsWith<InitializationException> {
            getOctaviusSession("jdbc:octavius://localhost:54321/octavius_test", OctaviusProperties().apply {
                user = "postgres"
                password = "1234"
            })
        }
        logger.error(exception) { "" }
        assertEquals(InitializationExceptionReason.CONNECTION_ERROR, exception.reason)
    }

    @Test
    fun `should throw InvalidOperationException with INCORRECT_RESULT_SIZE for fetchRowStrict on empty result`() {
        getSession().use { session ->
            val exception = assertFailsWith<InvalidOperationException> {
                session.createNativeQuery("SELECT 1 WHERE false").fetchRowStrict()
            }
            logger.error(exception) { "" }
            assertEquals(InvalidOperationExceptionReason.INCORRECT_RESULT_SIZE, exception.reason)
        }
    }
    
    @Test
    fun `should throw InvalidOperationException with INCORRECT_RESULT_SIZE for fetchRow on multiple results`() {
        getSession().use { session ->
            val exception = assertFailsWith<InvalidOperationException> {
                session.createNativeQuery("SELECT 1 UNION ALL SELECT 2").fetchRow()
            }
            logger.error(exception) { "" }
            assertEquals(InvalidOperationExceptionReason.INCORRECT_RESULT_SIZE, exception.reason)
        }
    }
}
