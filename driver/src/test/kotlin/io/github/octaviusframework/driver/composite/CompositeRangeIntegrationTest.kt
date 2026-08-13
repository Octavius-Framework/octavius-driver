package io.github.octaviusframework.driver.composite

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.row.get
import io.github.octaviusframework.driver.type.MultiRange
import io.github.octaviusframework.driver.type.Range
import io.github.octaviusframework.driver.type.multiRangeOf
import io.github.octaviusframework.driver.type.rangeOf
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompositeRangeIntegrationTest {

    data class SimpleData(val major: Int, val minor: Int)

    @BeforeAll
    fun setup() {
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            session.createNativeQuery("DROP TYPE IF EXISTS simple_data_range CASCADE").execute()
            session.createNativeQuery("DROP TYPE IF EXISTS simple_data CASCADE").execute()

            session.createNativeQuery("CREATE TYPE simple_data AS (major int, minor int)").execute()

            session.createNativeQuery("CREATE TYPE simple_data_range AS RANGE (subtype = simple_data)").execute()
        } catch (e: Exception) {
            println("Exception during setup: ${e.message}")
            throw e
        } finally {
            session.close()
        }
    }

    @AfterAll
    fun teardown() {
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            session.createNativeQuery("DROP TYPE IF EXISTS simple_data_range CASCADE").execute()
            session.createNativeQuery("DROP TYPE IF EXISTS simple_data CASCADE").execute()
        } finally {
            session.close()
        }
    }

    @Test
    fun testCompositeRangeNativeQuery() {
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            session.reloadTypes()
            session.types.registerAutoComposite<SimpleData>("simple_data")

            val dataRange = rangeOf(
                lowerBound = SimpleData(10, 10),
                upperBound = SimpleData(10, 20)
            )

            val query = "SELECT $1 AS data_range"
            val resultRow = session.createNativeQuery(query).fetchRowStrict(dataRange)
            val parsedRange = resultRow.get<Range<SimpleData>>("data_range")

            assertEquals(10, parsedRange.lowerBound?.minor)
            assertEquals(20, parsedRange.upperBound?.minor)
        } finally {
            session.close()
        }
    }

    @Test
    fun testCompositeMultiRangeNativeQuery() {
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            session.reloadTypes()
            session.types.registerAutoComposite<SimpleData>("simple_data")

            val dataRange1 = rangeOf(
                lowerBound = SimpleData(10, 10),
                upperBound = SimpleData(10, 20)
            )

            val dataRange2 = rangeOf(
                lowerBound = SimpleData(10, 30),
                upperBound = SimpleData(10, 40)
            )

            val multiRange = multiRangeOf(dataRange1, dataRange2)

            val query = "SELECT $1 AS data_range"
            val resultRow = session.createNativeQuery(query).fetchRowStrict(multiRange)
            val parsedMultiRange = resultRow.get<MultiRange<SimpleData>>("data_range")

            assertEquals(2, parsedMultiRange.ranges.size)
            assertEquals(10, parsedMultiRange.ranges[0].lowerBound?.minor)
            assertEquals(20, parsedMultiRange.ranges[0].upperBound?.minor)
            assertEquals(30, parsedMultiRange.ranges[1].lowerBound?.minor)
            assertEquals(40, parsedMultiRange.ranges[1].upperBound?.minor)
        } finally {
            session.close()
        }
    }
}
