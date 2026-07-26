package io.github.octaviusframework.driver.transaction

import io.github.octaviusframework.driver.session.OctaviusSession
import io.github.octaviusframework.driver.session.TransactionState
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.row.get
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.github.octaviusframework.driver.exception.OctaviusException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TransactionTest {

    private lateinit var session: OctaviusSession

    @BeforeEach
    fun setup() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"

        session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        session.createNativeQuery("CREATE TEMP TABLE IF NOT EXISTS test_trx (id INT, value TEXT)").execute()
        session.createNativeQuery("TRUNCATE TABLE test_trx").execute()
    }

    @AfterEach
    fun teardown() {
        try {
            session.close()
        } catch (e: Exception) {}
    }

    private fun countRows(): Long {
        val rows = session.createNativeQuery("SELECT COUNT(*) FROM test_trx").fetchAll()
        return rows[0].get<Long>(0)
    }

    @Test
    fun `test autoCommit false requires explicit commit`() {
        session.autoCommit = false
        assertFalse(session.autoCommit)

        session.createNativeQuery("INSERT INTO test_trx (id, value) VALUES (1, 'A')").execute()
        assertEquals(1L, countRows())

        session.commit() // This should send COMMIT

        // Verify data is still there outside transaction
        assertEquals(1L, countRows())
    }

    @Test
    fun `test rollback`() {
        session.autoCommit = false

        session.createNativeQuery("INSERT INTO test_trx (id, value) VALUES (1, 'A')").execute()
        assertEquals(1L, countRows())

        session.rollback()

        // Verify data is not there
        assertEquals(0L, countRows())
    }

    @Test
    fun `test savepoints`() {
        session.autoCommit = false

        session.createNativeQuery("INSERT INTO test_trx (id, value) VALUES (1, 'A')").execute()

        val sp1 = session.setSavepoint("sp1")

        session.createNativeQuery("INSERT INTO test_trx (id, value) VALUES (2, 'B')").execute()

        assertEquals(2L, countRows())

        session.rollback(sp1)

        assertEquals(1L, countRows())

        session.commit()

        assertEquals(1L, countRows())
    }

    @Test
    fun `test release savepoint`() {
        session.autoCommit = false

        session.createNativeQuery("INSERT INTO test_trx (id, value) VALUES (1, 'A')").execute()

        val sp1 = session.setSavepoint()

        session.createNativeQuery("INSERT INTO test_trx (id, value) VALUES (2, 'B')").execute()

        session.releaseSavepoint(sp1)

        session.commit()

        assertEquals(2L, countRows())
    }

    @Test
    fun `test transaction state`() {
        assertEquals(TransactionState.IDLE, session.transactionState)

        session.autoCommit = false

        assertEquals(TransactionState.IN_TRANSACTION, session.transactionState)

        try {
            session.createNativeQuery("INSERT INTO test_trx (id, value) VALUES ('INVALID_INT', 'A')").execute()
        } catch (e: OctaviusException) {
            // Expected syntax error
        }

        assertEquals(TransactionState.FAILED, session.transactionState)

        session.rollback()
        assertEquals(TransactionState.IN_TRANSACTION, session.transactionState)
    }

    @Test
    fun `test transaction manager successful block`() {
        session.transaction.required {
            createNativeQuery("INSERT INTO test_trx (id, value) VALUES (1, 'A')").execute()
        }

        // Verify data was committed
        assertEquals(1L, countRows())
        // Verify autoCommit was restored to true
        assertEquals(true, session.autoCommit)
    }

    @Test
    fun `test transaction manager failing block rolls back`() {
        try {
            session.transaction.required {
                createNativeQuery("INSERT INTO test_trx (id, value) VALUES (1, 'A')").execute()
                throw RuntimeException("Simulated error")
            }
        } catch (e: RuntimeException) {
            assertEquals("Simulated error", e.message)
        }

        // Verify data was rolled back
        assertEquals(0L, countRows())
        // Verify autoCommit was restored to true
        assertEquals(true, session.autoCommit)
    }

    @Test
    fun `test transaction manager nested successful block`() {
        session.transaction.required {
            session.createNativeQuery("INSERT INTO test_trx (id, value) VALUES (1, 'A')").execute()

            session.transaction.nested {
                session.createNativeQuery("INSERT INTO test_trx (id, value) VALUES (2, 'B')").execute()
            }
        }

        // Verify both were committed
        assertEquals(2L, countRows())
    }

    @Test
    fun `test transaction manager nested failing block rolls back to savepoint`() {
        session.transaction.required {
            session.createNativeQuery("INSERT INTO test_trx (id, value) VALUES (1, 'A')").execute()

            try {
                session.transaction.nested {
                    session.createNativeQuery("INSERT INTO test_trx (id, value) VALUES (2, 'B')").execute()
                    throw RuntimeException("Simulated error in savepoint")
                }
            } catch (e: RuntimeException) {
                assertEquals("Simulated error in savepoint", e.message)
            }
            
            // Should still be 1 row within transaction after savepoint rollback
            assertEquals(1L, countRows())
        }

        // Verify only the first was committed
        assertEquals(1L, countRows())
    }
}
