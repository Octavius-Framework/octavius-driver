package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason
import io.github.octaviusframework.driver.exception.StatementException
import io.github.octaviusframework.driver.exception.StatementExceptionReason
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BasicQueryIntegrationTest {

    @Test
    fun test() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val result = session.createNativeQuery("SELECT 1, 'abc', 4.5::float8").fetchRows()
        val row = result.first()
        assertEquals(1, row.get(0))
        assertEquals("abc", row.get(1))
        assertEquals(4.5, row.get(2))

        val result2 = session.createNativeQuery("SELECT $1 as test_int, $2 as test_float, $1 as test_int2")
            .fetchRowStrict(1, 2.4f)
        assertEquals(1, result2.get(0))
        assertEquals(2.4f, result2.get(1))
        assertEquals(1, result2.get(2))
        session.close()
    }

    @Test
    fun testFetchOneWithMultipleRows() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        // Generate 1000 rows. Thanks to maxRows=2 and PortalSuspended, it should fetch exactly 2 rows
        // and throw StatementException without loading all 1000 rows into memory.
        val exception = assertFailsWith<InvalidOperationException> {
            session.createNativeQuery("SELECT generate_series(1, 1000)").fetchRowStrict()
        }

        assertEquals(InvalidOperationExceptionReason.INCORRECT_RESULT_SIZE, exception.reason)

        // Make sure the connection is in a healthy state and can execute subsequent queries
        val subsequentResult = session.createNativeQuery("SELECT 42").fetchRowStrict().get<Int>(0)
        assertEquals(42, subsequentResult)
        session.close()
    }

    @Test
    fun testForEachMethods() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        // NativeQuery forEachRow
        var sum = 0
        var count = 0
        session.createNativeQuery("SELECT i FROM generate_series(1, 10) as i").forEachRow(fetchSize = 3) {
            sum += it.get<Int>(0)
            count++
        }
        assertEquals(10, count)
        assertEquals(55, sum)

        // NativeQuery forEachField
        var sumField = 0
        var countField = 0
        session.createNativeQuery("SELECT i * $1 FROM generate_series(1, 10) as i").forEachField<Int>(2, fetchSize = 4) {
            sumField += it
            countField++
        }
        assertEquals(10, countField)
        assertEquals(110, sumField)

        // NamedParameterQuery forEachField
        var sumNamedField = 0
        var countNamedField = 0
        session.createNamedQuery("SELECT i * @mult FROM generate_series(1, 10) as i").forEachField<Int>("mult" to 3, fetchSize = 5) {
            sumNamedField += it
            countNamedField++
        }
        assertEquals(10, countNamedField)
        assertEquals(165, sumNamedField)
        session.close()
    }

    @Test
    fun testForEachRejectsANegativeFetchSizeButNotZero() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val e = assertFailsWith<InvalidOperationException> {
            session.createNativeQuery("SELECT i FROM generate_series(1, 10) as i").forEachRow(fetchSize = -1) { }
        }
        assertEquals(InvalidOperationExceptionReason.INVALID_ARGUMENT, e.reason)

        // Zero is not a rejected batch size but Execute's own "no limit": one batch carrying the
        // whole result, which still arrives row by row.
        var seen = 0
        session.createNativeQuery("SELECT i FROM generate_series(1, 10) as i").forEachRow(fetchSize = 0) { seen++ }
        assertEquals(10, seen)

        // The refused call never reached the connection, so the session is still usable afterwards.
        assertEquals(10, session.createNativeQuery("SELECT i FROM generate_series(1, 10) as i").fetchRows().size)
        session.close()
    }
}
