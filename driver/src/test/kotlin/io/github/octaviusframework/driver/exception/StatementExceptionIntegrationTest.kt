package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StatementExceptionIntegrationTest {

    private fun getSession() = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", OctaviusProperties().apply {
        user = "postgres"
        password = "1234"
    })

    @Test
    fun `should throw StatementException with correct position for native query syntax error`() {
        val session = getSession()

        val exception = assertFailsWith<StatementException> {
            // Error at 'FRO', which is at index 9, position 10
            session.createNativeQuery("SELECT * FRO test_table").fetchOne()
        }

        assertEquals(StatementExceptionReason.SYNTAX_ERROR, exception.reason)
        assertEquals(10, exception.position)
        
        val ctx = exception.queryContext
        assertNotNull(ctx)
        assertEquals("SELECT * FRO test_table", ctx.sql)
        // For native query, dbSql is the same as sql
        assertEquals("SELECT * FRO test_table", ctx.dbSql)
    }

    @Test
    fun `should throw StatementException with correct position for named query syntax error`() {
        val session = getSession()

        val exception = assertFailsWith<StatementException> {
            // Named parameter query transforms "SELECT @param FRO test_table" 
            // into "SELECT $1 FRO test_table"
            // The position from DB will be for the transformed query ("SELECT $1 FRO test_table").
            session.createNamedQuery("SELECT @param FRO test_table")
                .fetchOne("param" to 1)
        }

        assertEquals(StatementExceptionReason.SYNTAX_ERROR, exception.reason)
        // The error is actually at "test_table" (position 15), because PostgreSQL treats "FRO" as a column alias for $1.
        assertEquals(15, exception.position)

        val ctx = exception.queryContext
        assertNotNull(ctx)
        assertEquals("SELECT @param FRO test_table", ctx.sql)
        assertEquals("SELECT $1 FRO test_table", ctx.dbSql)
    }

    @Test
    fun `should throw StatementException for unclosed quote in parser for named query`() {
        val session = getSession()

        val exception = assertFailsWith<StatementException> {
            // The unclosed quote starts at index 15
            session.createNamedQuery("SELECT @param, 'unclosed quote test")
                .fetchOne("param" to 1)
        }

        assertEquals(StatementExceptionReason.UNCLOSED_QUOTE, exception.reason)
        // position is 1-indexed, so 15 + 1 = 16
        assertEquals(16, exception.position)

        val ctx = assertNotNull(exception.queryContext)
        assertEquals("SELECT @param, 'unclosed quote test", ctx.sql)
        assertNull(ctx.dbSql)
    }

    @Test
    fun `should throw StatementException for undefined object with correct position`() {
        val session = getSession()

        val exception = assertFailsWith<StatementException> {
            session.createNativeQuery("SELECT * FROM some_non_existent_table").fetchAll()
        }

        assertEquals(StatementExceptionReason.UNDEFINED_OBJECT, exception.reason)
        // Position points to 'some_non_existent_table'. "SELECT * FROM " is 14 chars. 
        // 's' is at position 15.
        assertEquals(15, exception.position)
    }

    @Test
    fun `should throw StatementException for unclosed comment in parser for named query`() {
        val session = getSession()

        val exception = assertFailsWith<StatementException> {
            // The unclosed comment starts at index 14
            session.createNamedQuery("SELECT @param /* unclosed comment")
                .fetchOne("param" to 1)
        }

        assertEquals(StatementExceptionReason.UNCLOSED_COMMENT, exception.reason)
        // position is 1-indexed, so 14 + 1 = 15
        assertEquals(15, exception.position)
        val ctx = assertNotNull(exception.queryContext)
        assertEquals("SELECT @param /* unclosed comment", ctx.sql)
        assertNull(ctx.dbSql)
    }

    @Test
    fun `should throw StatementException for duplicate object`() {
        val session = getSession()
        
        session.createNativeQuery("CREATE TABLE IF NOT EXISTS duplicate_table_test (id INT)").execute()

        try {
            val exception = assertFailsWith<StatementException> {
                session.createNativeQuery("CREATE TABLE duplicate_table_test (id INT)").execute()
            }
            assertEquals(StatementExceptionReason.DUPLICATE_OBJECT, exception.reason)
        } finally {
            session.createNativeQuery("DROP TABLE IF EXISTS duplicate_table_test").execute()
        }
    }

    @Test
    fun `should throw StatementException for data type error`() {
        val session = getSession()

        val exception = assertFailsWith<StatementException> {
            // Trigger 42804 datatype_mismatch
            session.createNativeQuery("SELECT 1 UNION SELECT current_date").fetchAll()
        }

        assertEquals(StatementExceptionReason.DATA_TYPE_ERROR, exception.reason)
    }
}
