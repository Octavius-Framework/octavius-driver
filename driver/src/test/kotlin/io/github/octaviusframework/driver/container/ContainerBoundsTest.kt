package io.github.octaviusframework.driver.container

import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason
import io.github.octaviusframework.driver.type.PgType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A position outside a container is a mapping failure like any other. Every accessor here used to let the
 * list or array underneath raise its own `IndexOutOfBoundsException`, which nothing catching the driver's
 * exceptions ever saw - so each one gets a test of its own rather than sharing a block where the first
 * failure would hide the rest.
 */
class ContainerBoundsTest {

    companion object {
        val logger = KotlinLogging.logger {}
    }

    private val compositeType = PgType.Composite(
        oid = 1,
        name = "residence",
        schema = "public",
        attributes = linkedMapOf("city" to 25, "street" to 25)
    )

    private fun composite() = PgComposite(compositeType, arrayOf<Any?>("Roma", "Via Sacra"))

    private fun array() = PgArray(
        arrayOid = 1007,
        elementOid = 23,
        dimensions = listOf(ArrayDimension(2, 1)),
        elements = listOf(10, 20)
    )

    private fun record() = PgRecord(PgType.Record, intArrayOf(23, 25), arrayOf<Any?>(7, "denarii"))

    private fun multirange() = PgMultirange.create(
        multirangeOid = 4451,
        rangeOid = 3904,
        ranges = listOf(PgRange.create(3904, 23, 1, 10))
    )

    private fun assertOutOfBounds(block: () -> Unit) {
        val exception = assertFailsWith<MappingException> { block() }
        logger.error(exception) { "" }
        assertEquals(MappingExceptionReason.COLUMN_NOT_FOUND, exception.reason)
        assertTrue(exception.details.contains("out of bounds"), exception.details)
    }

    @Test
    fun `array get outside the flat element list is COLUMN_NOT_FOUND`() {
        assertOutOfBounds { array().get<Int>(2) }
        assertOutOfBounds { array().get<Int>(-1) }
    }

    @Test
    fun `composite get outside the declared attributes is COLUMN_NOT_FOUND`() {
        assertOutOfBounds { composite().get<String>(2) }
        assertOutOfBounds { composite().get<String>(-1) }
    }

    @Test
    fun `composite set outside the declared attributes is COLUMN_NOT_FOUND`() {
        assertOutOfBounds { composite()[2] = "Ostia" }
        assertOutOfBounds { composite()[-1] = "Ostia" }
    }

    @Test
    fun `composite getAttributeOid outside the declared attributes is COLUMN_NOT_FOUND`() {
        assertOutOfBounds { composite().getAttributeOid(2) }
        assertOutOfBounds { composite().getAttributeOid(-1) }
    }

    @Test
    fun `record get outside the selected fields is COLUMN_NOT_FOUND`() {
        assertOutOfBounds { record().get<String>(2) }
        assertOutOfBounds { record().get<String>(-1) }
    }

    @Test
    fun `record getAttributeOid outside the selected fields is COLUMN_NOT_FOUND`() {
        assertOutOfBounds { record().getAttributeOid(2) }
        assertOutOfBounds { record().getAttributeOid(-1) }
    }

    @Test
    fun `multirange get outside its ranges is COLUMN_NOT_FOUND`() {
        assertOutOfBounds { multirange()[1] }
        assertOutOfBounds { multirange()[-1] }
    }

    @Test
    fun `a position that is there still answers with the value`() {
        assertEquals(20, array().get<Int>(1))
        assertEquals("Via Sacra", composite().get<String>(1))
        assertEquals(25, composite().getAttributeOid(1))
        assertEquals("Ostia", composite().apply { this[0] = "Ostia" }.get<String>(0))
        assertEquals("denarii", record().get<String>(1))
        assertEquals(25, record().getAttributeOid(1))
        assertEquals(1, multirange()[0].lowerBound<Int>())
    }

    @Test
    fun `the message names the position, the container and how many attributes it has`() {
        val exception = assertFailsWith<MappingException> { composite().get<String>(7) }
        logger.error(exception) { "" }
        assertTrue(exception.details.contains("7"), exception.details)
        assertTrue(exception.details.contains("residence"), exception.details)
        assertTrue(exception.details.contains("2 attributes"), exception.details)
    }
}
