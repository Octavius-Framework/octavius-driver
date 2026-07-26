package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.row.get
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BasicQueryIntegrationTest {

    @Test
    fun test() {
        println("Zaczynamy test!")

        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val result = session.createNativeQuery("SELECT 1, 'abc', 4.5::float8").fetchAll()
        val row = result.first()
        assertEquals(1, row.get(0))
        assertEquals("abc", row.get(1))
        assertEquals(4.5, row.get(2))

        val result2 = session.createNativeQuery("SELECT $1 as test_int, $2 as test_float, $1 as test_int2")
            .fetchOne(1, 2.4f)
        assertEquals(1, result2.get(0))
        assertEquals(2.4f, result2.get(1))
        assertEquals(1, result2.get(2))
    }

    @Test
    fun testFetchOneWithMultipleRows() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        // Generate 1000 rows. Thanks to maxRows=2 and PortalSuspended, it should fetch exactly 2 rows
        // and throw IllegalStateException without loading all 1000 rows into memory.
        val exception = assertFailsWith<IllegalStateException> {
            session.createNativeQuery("SELECT generate_series(1, 1000)").fetchOne()
        }

        assertEquals("Expected exactly one row, but got 2", exception.message)

        // Make sure the connection is in a healthy state and can execute subsequent queries
        val subsequentResult = session.createNativeQuery("SELECT 42").fetchOne().get<Int>(0)
        assertEquals(42, subsequentResult)
    }
}
