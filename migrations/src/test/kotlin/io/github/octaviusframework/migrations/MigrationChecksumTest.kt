package io.github.octaviusframework.migrations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MigrationChecksumTest {

    private val script = "CREATE TABLE castra (id serial PRIMARY KEY);\nCREATE INDEX i ON castra (id);\n"

    // ------------------------------------------------ what a checkout is allowed to change

    @Test
    fun `line endings do not change the checksum`() {
        // The one that bites for real: Windows with core.autocrlf against Linux CI, same file, two byte
        // sequences, and a mismatch reported on a migration nobody touched.
        assertEquals(MigrationChecksum.of(script), MigrationChecksum.of(script.replace("\n", "\r\n")))
        assertEquals(MigrationChecksum.of(script), MigrationChecksum.of(script.replace("\n", "\r")))
    }

    @Test
    fun `a byte-order mark does not change the checksum`() {
        assertEquals(MigrationChecksum.of(script), MigrationChecksum.of("\uFEFF$script"))
    }

    @Test
    fun `whitespace at the end of the file does not change the checksum`() {
        assertEquals(MigrationChecksum.of(script), MigrationChecksum.of(script.trimEnd()))
        assertEquals(MigrationChecksum.of(script), MigrationChecksum.of("$script\n\n   \t"))
    }

    @Test
    fun `all of them at once still do not change it`() {
        val mangled = "\uFEFF" + script.replace("\n", "\r\n").trimEnd() + "\r\n\r\n  "
        assertEquals(MigrationChecksum.of(script), MigrationChecksum.of(mangled))
    }

    // ------------------------------------------------ what an edit is

    @Test
    fun `changed content changes the checksum`() {
        assertNotEquals(
            MigrationChecksum.of(script),
            MigrationChecksum.of(script.replace("castra", "castrum"))
        )
    }

    @Test
    fun `whitespace inside the file counts as an edit`() {
        // Normalising this away too would be over-reaching: reformatting a migration the database has a
        // record of is a change to it, and the checksum exists to say so.
        assertNotEquals(
            MigrationChecksum.of(script),
            MigrationChecksum.of(script.replace("CREATE TABLE", "CREATE  TABLE"))
        )
    }

    @Test
    fun `an added line changes the checksum`() {
        assertNotEquals(MigrationChecksum.of(script), MigrationChecksum.of(script + "ANALYZE castra;\n"))
    }

    // ------------------------------------------------ the range it is stored in

    @Test
    fun `checksums are never negative and do use the upper half of the range`() {
        val checksums = (1..500).map { MigrationChecksum.of("SELECT $it FROM castra WHERE id = $it") }

        assertTrue(checksums.all { it >= 0 }, "CRC32 is unsigned; a negative one means it was squeezed into an Int")
        // Without this the test above could pass on 500 small numbers and prove nothing about the range.
        assertTrue(
            checksums.any { it > Int.MAX_VALUE },
            "no sample exceeded Int.MAX_VALUE, so this says nothing about what an Int would have done"
        )
    }
}
