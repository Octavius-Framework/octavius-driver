package io.github.octaviusframework.serializer

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The mapping every case here asserts was read off a running PostgreSQL 18 rather than worked out: a date
 * written through the driver in binary and rendered back with `::text`, which puts no parser in between.
 */
class PgDateTextTest {

    private fun bothWays(iso: String, pg: String) {
        assertEquals(pg, PgDateText.fromIso(iso), "$iso should be written as $pg")
        assertEquals(iso, PgDateText.toIso(pg), "$pg should be read back as $iso")
    }

    @Test
    fun `an ordinary year is left alone in both directions`() {
        bothWays("2024-03-15", "2024-03-15")
        bothWays("0001-01-01", "0001-01-01")
        bothWays("9999-12-31", "9999-12-31")
    }

    @Test
    fun `a year past four digits loses the sign PostgreSQL will not read`() {
        bothWays("+10000-01-02", "10000-01-02")
        // 5874897 is what a `date` reaches, so this one is storable - and was not castable before.
        bothWays("+5874897-12-31", "5874897-12-31")
    }

    @Test
    fun `ISO year zero is one BC`() {
        // ISO counts through a year zero and PostgreSQL does not, which is the whole off-by-one.
        bothWays("0000-01-02", "0001-01-02 BC")
    }

    @Test
    fun `a negative ISO year is one further back in BC`() {
        bothWays("-0001-01-02", "0002-01-02 BC")
        // -4712 is what a `date` reaches going back, and PostgreSQL calls it 4713 BC.
        bothWays("-4712-01-01", "4713-01-01 BC")
    }

    @Test
    fun `a time and an offset ride along untouched, and BC goes last`() {
        bothWays("+10000-01-02T03:04:05", "10000-01-02T03:04:05")
        bothWays("-0001-01-02T03:04:05", "0002-01-02T03:04:05 BC")
        bothWays("-0001-01-02T03:04:05Z", "0002-01-02T03:04:05Z BC")
        bothWays("+100000-01-01T00:00:00Z", "100000-01-01T00:00:00Z")
    }

    @Test
    fun `fractional seconds survive`() {
        bothWays("-0001-12-31T23:59:59.999999999Z", "0002-12-31T23:59:59.999999999Z BC")
    }

    @Test
    fun `reading is lenient, so a payload already written in ISO still decodes`() {
        // Built in SQL, or written before this translation existed. Only writing is opinionated.
        assertEquals("+10000-01-02", PgDateText.toIso("+10000-01-02"))
        assertEquals("-0001-01-02", PgDateText.toIso("-0001-01-02"))
        assertEquals("2024-03-15", PgDateText.toIso("2024-03-15"))
    }

    @Test
    fun `a lowercase era suffix is read too`() {
        assertEquals("0000-01-02", PgDateText.toIso("0001-01-02 bc"))
    }

    @Test
    fun `text with no leading year is handed back unchanged`() {
        assertEquals("infinity", PgDateText.fromIso("infinity"))
        assertEquals("infinity", PgDateText.toIso("infinity"))
        assertEquals("", PgDateText.fromIso(""))
        assertEquals("", PgDateText.toIso(""))
    }
}
