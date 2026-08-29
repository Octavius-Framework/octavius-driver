package io.github.octaviusframework.client

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.octaviusframework.client.transaction.TransactionPropagation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class DefaultSessionProviderNestingTest {

    private fun pool(size: Int) = HikariDataSource(HikariConfig().apply {
        jdbcUrl = "jdbc:octavius://localhost:5432/octavius_test"
        username = "postgres"
        password = "1234"
        maximumPoolSize = size
    })

    private fun OctaviusClient.pid(): Int = rawQuery("SELECT pg_backend_pid()").fetchFieldStrict<Int>()

    @Test
    fun `the outer session comes back after REQUIRES_NEW returns`() {
        pool(3).use { p ->
            OctaviusClient.fromDataSource(p).use { db ->
                db.transaction {
                    val before = pid()
                    val inner = transaction(propagation = TransactionPropagation.REQUIRES_NEW) { pid() }
                    val after = pid()

                    assertNotEquals(before, inner, "REQUIRES_NEW must run on a session of its own")
                    assertEquals(before, after, "the outer session must be bound again after the inner one ends")
                }
            }
        }
    }

    @Test
    fun `two levels of REQUIRES_NEW unwind in order`() {
        pool(3).use { p ->
            OctaviusClient.fromDataSource(p).use { db ->
                db.transaction {
                    val l0 = pid()
                    transaction(propagation = TransactionPropagation.REQUIRES_NEW) {
                        val l1 = pid()
                        transaction(propagation = TransactionPropagation.REQUIRES_NEW) {
                            val l2 = pid()
                            assertNotEquals(l1, l2)
                            assertNotEquals(l0, l2)
                        }
                        assertEquals(l1, pid(), "level 1 must be bound again")
                    }
                    assertEquals(l0, pid(), "level 0 must be bound again")
                }
            }
        }
    }

    @Test
    fun `work after a REQUIRES_NEW still rolls back with the outer transaction`() {
        pool(3).use { p ->
            OctaviusClient.fromDataSource(p).use { db ->
                db.rawQuery("CREATE TABLE IF NOT EXISTS probe_rows (tag TEXT PRIMARY KEY)").execute()
                db.rawQuery("TRUNCATE probe_rows").execute()

                assertFailsWith<IllegalStateException> {
                    db.transaction {
                        transaction(propagation = TransactionPropagation.REQUIRES_NEW) {
                            rawQuery("INSERT INTO probe_rows VALUES ('inner')").update()
                        }
                        // If the binding were lost here this would auto-commit on a borrowed session.
                        rawQuery("INSERT INTO probe_rows VALUES ('after')").update()
                        error("take the outer transaction down")
                    }
                }

                val tags = db.rawQuery("SELECT tag FROM probe_rows ORDER BY tag").fetchFields<String>()
                assertEquals(listOf("inner"), tags, "'after' must have rolled back with the outer transaction")

                db.rawQuery("DROP TABLE probe_rows").execute()
            }
        }
    }
}