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
    fun `should discard the rows when execute is told to ignore them`() {
        getSession().use { session ->
            session.createNativeQuery("SELECT 1").execute(ignoreRows = true)

            // The session still works, which is the half worth asserting: the rows were drained on the way
            // to ReadyForQuery rather than left on the socket for the next statement to trip over.
            assertEquals(42, session.createNativeQuery("SELECT 42").fetchFieldStrict<Int>())
        }
    }

    @Test
    fun `should run a whole script past a statement that returns rows`() {
        getSession().use { session ->
            // What a script written elsewhere looks like: pg_dump puts a SELECT pg_catalog.setval(...) after
            // every sequence, and one of those in the middle used to take the whole call down.
            session.createNativeQuery(
                """
                CREATE TEMP TABLE legio (id int);
                INSERT INTO legio VALUES (1);
                SELECT 'a stray select in the middle';
                INSERT INTO legio VALUES (2)
                """.trimIndent()
            ).execute(ignoreRows = true)

            assertEquals(2, session.createNativeQuery("SELECT count(*) FROM legio").fetchFieldStrict<Long>())
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
