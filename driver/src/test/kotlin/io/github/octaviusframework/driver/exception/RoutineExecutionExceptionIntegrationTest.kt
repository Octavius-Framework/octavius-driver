package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoutineExecutionExceptionIntegrationTest {

    private fun getSession() = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", OctaviusProperties().apply {
        user = "postgres"
        password = "1234"
    })

    @Test
    fun `should throw RAISE_EXCEPTION`() {
        getSession().use { session ->
            val exception = assertFailsWith<RoutineExecutionException> {
                session.createNativeQuery("""
                    DO $$
                    BEGIN
                        RAISE EXCEPTION 'Test exception';
                    END;
                    $$;
                """).execute()
            }
            assertEquals(RoutineExecutionExceptionReason.RAISE_EXCEPTION, exception.reason)
            assertNotNull(exception.whereContext)
            assertTrue(exception.whereContext.isNotEmpty())
        }
    }

    @Test
    fun `should throw NO_DATA_FOUND`() {
        getSession().use { session ->
            val exception = assertFailsWith<RoutineExecutionException> {
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
            assertEquals(RoutineExecutionExceptionReason.NO_DATA_FOUND, exception.reason)
            assertNotNull(exception.whereContext)
            assertTrue(exception.whereContext.isNotEmpty())
        }
    }

    @Test
    fun `should throw TOO_MANY_ROWS`() {
        getSession().use { session ->
            val exception = assertFailsWith<RoutineExecutionException> {
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
            assertEquals(RoutineExecutionExceptionReason.TOO_MANY_ROWS, exception.reason)
            assertNotNull(exception.whereContext)
            assertTrue(exception.whereContext.isNotEmpty())
        }
    }

    @Test
    fun `should throw ASSERT_FAILURE`() {
        getSession().use { session ->
            val exception = assertFailsWith<RoutineExecutionException> {
                session.createNativeQuery("""
                    DO $$
                    BEGIN
                        ASSERT false, 'Assertion failed';
                    END;
                    $$;
                """).execute()
            }
            assertEquals(RoutineExecutionExceptionReason.ASSERT_FAILURE, exception.reason)
            assertNotNull(exception.whereContext)
            assertTrue(exception.whereContext.isNotEmpty())
        }
    }
}
