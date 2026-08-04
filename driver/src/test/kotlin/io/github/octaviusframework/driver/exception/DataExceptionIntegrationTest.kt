package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DataExceptionIntegrationTest {

    private fun getSession() = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", OctaviusProperties().apply {
        user = "postgres"
        password = "1234"
    })

    @Test
    fun `should throw DIVISION_BY_ZERO`() {
        getSession().use { session ->
            val exception = assertFailsWith<DataException> {
                session.createNativeQuery("SELECT 1 / 0").fetchRowStrict()
            }
            assertEquals(DataExceptionReason.DIVISION_BY_ZERO, exception.reason)
        }
    }

    @Test
    fun `should throw INVALID_FORMAT`() {
        getSession().use { session ->
            val exception = assertFailsWith<DataException> {
                session.createNativeQuery("SELECT 'not-a-number'::int").fetchRowStrict()
            }
            assertEquals(DataExceptionReason.INVALID_FORMAT, exception.reason)
        }
    }

    @Test
    fun `should throw NUMERIC_OUT_OF_RANGE`() {
        getSession().use { session ->
            val exception = assertFailsWith<DataException> {
                session.createNativeQuery("SELECT 10000000000::int").fetchRowStrict()
            }
            assertEquals(DataExceptionReason.NUMERIC_OUT_OF_RANGE, exception.reason)
        }
    }
    
    @Test
    fun `should throw DATA_TRUNCATION`() {
        getSession().use { session ->
            session.createNativeQuery("CREATE TABLE IF NOT EXISTS test_truncation (val VARCHAR(3))").execute()
            try {
                val exception = assertFailsWith<DataException> {
                    session.createNativeQuery("INSERT INTO test_truncation VALUES ('too_long')").execute()
                }
                assertEquals(DataExceptionReason.DATA_TRUNCATION, exception.reason)
            } finally {
                session.createNativeQuery("DROP TABLE test_truncation").execute()
            }
        }
    }

    @Test
    fun `should throw ARRAY_SUBSCRIPT_ERROR`() {
        getSession().use { session ->
            val exception = assertFailsWith<DataException> {
                session.createNativeQuery("SELECT ARRAY[ARRAY[1,2], ARRAY[1]]").fetchRowStrict()
            }
            assertEquals(DataExceptionReason.ARRAY_SUBSCRIPT_ERROR, exception.reason)
        }
    }

    @Test
    fun `should throw JSON_ERROR`() {
        getSession().use { session ->
            val exception = assertFailsWith<DataException> {
                session.createNativeQuery("SELECT '{\"invalid_json\"'::json").fetchRowStrict()
            }
            assertEquals(DataExceptionReason.INVALID_FORMAT, exception.reason)
        }
    }

    @Test
    fun `should throw REGEX_ERROR`() {
        getSession().use { session ->
            val exception = assertFailsWith<DataException> {
                session.createNativeQuery("SELECT 'abc' ~ '*abc'").fetchRowStrict()
            }
            assertEquals(DataExceptionReason.REGEX_ERROR, exception.reason)
        }
    }
}
