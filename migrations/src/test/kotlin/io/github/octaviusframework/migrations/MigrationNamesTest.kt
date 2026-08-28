package io.github.octaviusframework.migrations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MigrationNamesTest {

    private fun parse(name: String) = MigrationNames.parse(name, "db/migration/$name.sql")

    // ---------------------------------------------------------------- versioned

    @Test
    fun `reads the version and the description`() {
        val parsed = parse("V2__add_indexes")
        assertEquals(MigrationVersion.parse("2"), parsed.version)
        assertEquals("add indexes", parsed.description)
    }

    @Test
    fun `reads a version with parts`() {
        assertEquals(MigrationVersion.parse("2.1"), parse("V2.1__add_indexes").version)
    }

    @Test
    fun `reads a timestamp version`() {
        assertEquals(MigrationVersion.parse("20260827"), parse("V20260827__seed").version)
    }

    @Test
    fun `a file and a class carry the same identity`() {
        // The point of one convention for both: a class never has to be constructed to find out what it is.
        val fromFile = MigrationNames.parse("V2.1__add_indexes", "V2.1__add_indexes.sql")
        val fromClass = MigrationNames.parse("V2_1__Add_indexes", "com.roma.migrations.V2_1__Add_indexes")

        assertEquals(fromFile.version, fromClass.version)
        assertEquals("add indexes", fromFile.description)
        assertEquals("Add indexes", fromClass.description)
    }

    @Test
    fun `every underscore in the description becomes a space`() {
        assertEquals("create table castra", parse("V3__create_table_castra").description)
    }

    // ---------------------------------------------------------------- repeatable

    @Test
    fun `a repeatable migration has no version`() {
        val parsed = parse("R__rebuild_views")
        assertNull(parsed.version)
        assertEquals("rebuild views", parsed.description)
    }

    // ---------------------------------------------------------------- refusals

    @Test
    fun `a name without the separator is refused`() {
        val e = assertThrows<MigrationException> { parse("V2_add_indexes") }
        assertEquals(MigrationExceptionReason.INVALID_MIGRATION, e.reason)
    }

    @Test
    fun `a name with no version after V is refused`() {
        assertThrows<MigrationException> { parse("V__add_indexes") }
    }

    @Test
    fun `a name with no description is refused`() {
        assertThrows<MigrationException> { parse("V2__") }
        assertThrows<MigrationException> { parse("R__") }
    }

    @Test
    fun `a version on a repeatable migration is refused`() {
        assertThrows<MigrationException> { parse("R2__rebuild_views") }
    }

    @Test
    fun `an unknown prefix is refused`() {
        assertThrows<MigrationException> { parse("X2__add_indexes") }
    }

    @Test
    fun `the prefix is case-sensitive`() {
        assertThrows<MigrationException> { parse("v2__add_indexes") }
        assertThrows<MigrationException> { parse("r__rebuild_views") }
    }

    @Test
    fun `a bad version inside a name is refused`() {
        assertThrows<MigrationException> { parse("V2.x__add_indexes") }
    }

    @Test
    fun `a refusal names the file it is about`() {
        // Without this the message says a name is wrong without saying which file to go and open.
        val e = assertThrows<MigrationException> {
            MigrationNames.parse("nonsense", "db/migration/nonsense.sql")
        }
        assertTrue(
            e.details!!.contains("db/migration/nonsense.sql"),
            "the refusal should name its origin: ${e.details}"
        )
    }
}
