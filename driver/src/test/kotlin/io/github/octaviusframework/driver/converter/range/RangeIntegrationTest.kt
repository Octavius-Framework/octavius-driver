package io.github.octaviusframework.driver.converter.range

import io.github.octaviusframework.driver.type.MultiRange
import io.github.octaviusframework.driver.type.Range
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.row.get
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RangeIntegrationTest {

    private fun getSession() =
        getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
        })

    @Test
    fun testRangeSelect() {
        val session = getSession()

        val rangeResult = session.createNativeQuery("SELECT '[10,20)'::int4range").fetchOne()
        val range = rangeResult.get<Range<Int>>(0)
        assertNotNull(range)
        assertEquals(10, range.lowerBound)
        assertEquals(20, range.upperBound)
        assertTrue(range.isLowerInclusive)
        assertFalse(range.isUpperInclusive)
    }

    @Test
    fun testMultiRangeSelect() {
        val session = getSession()

        val multiRangeResult = session.createNativeQuery("SELECT '{[1,5], [10,20)}'::int4multirange").fetchOne()
        val multiRange = multiRangeResult.get<MultiRange<Int>>(0)
        assertNotNull(multiRange)
        assertEquals(2, multiRange.ranges.size)

        val first = multiRange.ranges[0]
        assertEquals(1, first.lowerBound)
        assertEquals(6, first.upperBound) // Normalized from [1,5] to [1,6)
        assertTrue(first.isLowerInclusive)
        assertFalse(first.isUpperInclusive)

        val second = multiRange.ranges[1]
        assertEquals(10, second.lowerBound)
        assertEquals(20, second.upperBound)
        assertTrue(second.isLowerInclusive)
        assertFalse(second.isUpperInclusive)
    }

    @Test
    fun testRangeParameter() {
        val session = getSession()

        val inputRange = Range(lowerBound = 5, upperBound = 15, isLowerInclusive = true, isUpperInclusive = true)

        val result = session.createNativeQuery("SELECT $1::int4range").fetchOne(inputRange)
        val rangeBack = result.get<Range<Int>>(0)

        assertNotNull(rangeBack)
        assertEquals(5, rangeBack.lowerBound)
        assertEquals(16, rangeBack.upperBound) // Normalized [5,15] to [5,16)
        assertTrue(rangeBack.isLowerInclusive)
        assertFalse(rangeBack.isUpperInclusive)
    }

    @Test
    fun testMultiRangeParameter() {
        val session = getSession()

        val inputMultiRange = MultiRange(
            listOf(
                Range(lowerBound = 1, upperBound = 5, isLowerInclusive = true, isUpperInclusive = false),
                Range(lowerBound = 10, upperBound = null, isLowerInclusive = false, isUpperInfinite = true)
            )
        )

        val result = session.createNativeQuery("SELECT $1::int4multirange").fetchOne(inputMultiRange)
        val multiRangeBack = result.get<MultiRange<Int>>(0)

        assertNotNull(multiRangeBack)
        assertEquals(2, multiRangeBack.ranges.size)

        val first = multiRangeBack.ranges[0]
        assertEquals(1, first.lowerBound)
        assertEquals(5, first.upperBound)
        assertTrue(first.isLowerInclusive)
        assertFalse(first.isUpperInclusive)

        val second = multiRangeBack.ranges[1]
        assertEquals(11, second.lowerBound) // Normalized from (10, infinity) to [11, infinity)
        assertNull(second.upperBound)
        assertTrue(second.isLowerInclusive)
        assertTrue(second.isUpperInfinite)
    }
}