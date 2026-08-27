package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoutineAssertionExceptionIntegrationTest {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private fun getSession() = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", OctaviusProperties().apply {
        user = "postgres"
        password = "1234"
    })

    @Test
    fun `should throw NO_DATA_FOUND`() {
        getSession().use { session ->
            val exception = assertFailsWith<RoutineAssertionException> {
                session.createNativeQuery("""
                    DO $$
                    DECLARE
                        temp_var INT;
                    BEGIN
                        SELECT 1 INTO STRICT temp_var WHERE false;
                    END;
                    $$;
                """).execute()
            }
            logger.error(exception) { "" }
            assertEquals("P0002", exception.sqlState)
            assertEquals(RoutineAssertionExceptionReason.NO_DATA_FOUND, exception.reason)
            assertNotNull(exception.where)
            assertTrue(exception.where!!.isNotEmpty())
        }
    }

    @Test
    fun `should throw TOO_MANY_ROWS`() {
        getSession().use { session ->
            val exception = assertFailsWith<RoutineAssertionException> {
                session.createNativeQuery("""
                    DO $$
                    DECLARE
                        temp_var INT;
                    BEGIN
                        SELECT * INTO STRICT temp_var FROM (VALUES (1), (2)) AS t(c);
                    END;
                    $$;
                """).execute()
            }
            logger.error(exception) { "" }
            assertEquals("P0003", exception.sqlState)
            assertEquals(RoutineAssertionExceptionReason.TOO_MANY_ROWS, exception.reason)
            assertNotNull(exception.where)
            assertTrue(exception.where!!.isNotEmpty())
        }
    }

    @Test
    fun `should throw ASSERT_FAILURE`() {
        getSession().use { session ->
            val exception = assertFailsWith<RoutineAssertionException> {
                session.createNativeQuery("""
                    DO $$
                    BEGIN
                        ASSERT false, 'Assertion failed';
                    END;
                    $$;
                """).execute()
            }
            logger.error(exception) { "" }
            assertEquals("P0004", exception.sqlState)
            assertEquals(RoutineAssertionExceptionReason.ASSERT_FAILURE, exception.reason)
            assertNotNull(exception.where)
            assertTrue(exception.where!!.isNotEmpty())
        }
    }
}
