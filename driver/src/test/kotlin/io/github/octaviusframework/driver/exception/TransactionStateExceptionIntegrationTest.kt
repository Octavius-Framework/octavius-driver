package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TransactionStateExceptionIntegrationTest {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private fun getSession() =
        getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
        })

    @Test
    fun `should throw IN_FAILED_TRANSACTION after an error inside a transaction`() {
        getSession().use { session ->
            session.createNativeQuery("BEGIN").execute()
            try {
                assertFailsWith<DataException> {
                    session.createNativeQuery("SELECT 1 / 0").fetchField<Int>()
                }

                val exception = assertFailsWith<TransactionStateException> {
                    session.createNativeQuery("SELECT 1").fetchField<Int>()
                }
                logger.error(exception) { "" }
                assertEquals("25P02", exception.sqlState)
                assertEquals(TransactionStateExceptionReason.IN_FAILED_TRANSACTION, exception.reason)
                // The server sends no error position for class 25, which is the reason this is not a
                // StatementException: there is nothing in the statement to point at.
                assertNull(exception.serverErrorMessage?.position)
            } finally {
                session.createNativeQuery("ROLLBACK").execute()
            }
        }
    }

    @Test
    fun `should throw READ_ONLY_TRANSACTION for a write in a read-only transaction`() {
        getSession().use { session ->
            session.createNativeQuery("BEGIN READ ONLY").execute()
            try {
                val exception = assertFailsWith<TransactionStateException> {
                    session.createNativeQuery("CREATE TEMP TABLE read_only_probe (id INT)").execute()
                }
                logger.error(exception) { "" }
                assertEquals("25006", exception.sqlState)
                assertEquals(TransactionStateExceptionReason.READ_ONLY_TRANSACTION, exception.reason)
            } finally {
                session.createNativeQuery("ROLLBACK").execute()
            }
        }
    }

    @Test
    fun `should throw ACTIVE_TRANSACTION for a statement that refuses a transaction block`() {
        getSession().use { session ->
            session.createNativeQuery("BEGIN").execute()
            try {
                val exception = assertFailsWith<TransactionStateException> {
                    session.createNativeQuery("VACUUM").execute()
                }
                logger.error(exception) { "" }
                assertEquals("25001", exception.sqlState)
                assertEquals(TransactionStateExceptionReason.ACTIVE_TRANSACTION, exception.reason)
            } finally {
                session.createNativeQuery("ROLLBACK").execute()
            }
        }
    }

    @Test
    fun `should throw NO_ACTIVE_TRANSACTION for a savepoint rollback outside a transaction`() {
        getSession().use { session ->
            val exception = assertFailsWith<TransactionStateException> {
                session.createNativeQuery("ROLLBACK TO SAVEPOINT no_such_savepoint").execute()
            }
            logger.error(exception) { "" }
            assertEquals("25P01", exception.sqlState)
            assertEquals(TransactionStateExceptionReason.NO_ACTIVE_TRANSACTION, exception.reason)
        }
    }
}
