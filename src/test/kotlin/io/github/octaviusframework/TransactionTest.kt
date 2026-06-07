package io.github.octaviusframework

import io.github.octaviusframework.jdbc.OctaviusConnection
import io.github.octaviusframework.jdbc.unwrap
import io.github.octaviusframework.query.get
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TransactionTest {

    private lateinit var connection: OctaviusConnection

    @BeforeEach
    fun setup() {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val conn = DriverManager.getConnection("jdbc:octavius://localhost:5432/octavius_test", props)
        connection = conn.unwrap<OctaviusConnection>()

        connection.queryExecutor.execute("CREATE TEMP TABLE IF NOT EXISTS test_trx (id INT, value TEXT)")
        connection.queryExecutor.execute("TRUNCATE TABLE test_trx")
    }

    @AfterEach
    fun teardown() {
        if (!connection.isClosed) {
            connection.close()
        }
    }

    private fun countRows(): Long {
        val rows = connection.queryExecutor.query("SELECT COUNT(*) FROM test_trx")
        return rows[0].get<Long>(0)
    }

    @Test
    fun `test autoCommit false requires explicit commit`() {
        connection.autoCommit = false
        assertFalse(connection.autoCommit)

        connection.queryExecutor.update("INSERT INTO test_trx (id, value) VALUES (1, 'A')")
        assertEquals(1L, countRows())

        connection.commit() // This should send COMMIT

        // Verify data is still there outside transaction
        assertEquals(1L, countRows())
    }

    @Test
    fun `test rollback`() {
        connection.autoCommit = false

        connection.queryExecutor.update("INSERT INTO test_trx (id, value) VALUES (1, 'A')")
        assertEquals(1L, countRows())

        connection.rollback()

        // Verify data is not there
        assertEquals(0L, countRows())
    }

    @Test
    fun `test savepoints`() {
        connection.autoCommit = false

        connection.queryExecutor.update("INSERT INTO test_trx (id, value) VALUES (1, 'A')")

        val sp1 = connection.setSavepoint("sp1")

        connection.queryExecutor.update("INSERT INTO test_trx (id, value) VALUES (2, 'B')")

        assertEquals(2L, countRows())

        connection.rollback(sp1)

        assertEquals(1L, countRows())

        connection.commit()

        assertEquals(1L, countRows())
    }

    @Test
    fun `test release savepoint`() {
        connection.autoCommit = false

        connection.queryExecutor.update("INSERT INTO test_trx (id, value) VALUES (1, 'A')")

        val sp1 = connection.setSavepoint()

        connection.queryExecutor.update("INSERT INTO test_trx (id, value) VALUES (2, 'B')")

        connection.releaseSavepoint(sp1)

        connection.commit()

        assertEquals(2L, countRows())
    }

    @Test
    fun `test transaction state`() {
        assertEquals(OctaviusConnection.TransactionState.IDLE, connection.transactionState)
        
        connection.autoCommit = false
        
        assertEquals(OctaviusConnection.TransactionState.IN_TRANSACTION, connection.transactionState)

        try {
            connection.queryExecutor.update("INSERT INTO test_trx (id, value) VALUES ('INVALID_INT', 'A')")
        } catch (e: SQLException) {
            // Expected syntax error
        }
        
        assertEquals(OctaviusConnection.TransactionState.FAILED, connection.transactionState)

        connection.rollback()
        assertEquals(OctaviusConnection.TransactionState.IN_TRANSACTION, connection.transactionState)
    }
}
