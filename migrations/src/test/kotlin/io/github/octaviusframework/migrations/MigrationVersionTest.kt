package io.github.octaviusframework.migrations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MigrationVersionTest {

    private fun version(text: String) = MigrationVersion.parse(text)

    // ---------------------------------------------------------------- ordering

    @Test
    fun `orders by number, not by text`() {
        // The reason this is a type at all: as text, "1.10" sorts before "1.9".
        assertTrue(version("1.9") < version("1.10"))
        assertTrue(version("2") > version("1.99"))
        assertTrue(version("1.0.1") > version("1"))
    }

    @Test
    fun `sorts a shuffled set into version order`() {
        val sorted = listOf("2", "1.10", "1.2", "10", "1.9", "1")
            .map { version(it) }
            .sorted()
            .map { it.text }

        assertEquals(listOf("1", "1.2", "1.9", "1.10", "2", "10"), sorted)
    }

    @Test
    fun `a timestamp version does not overflow`() {
        // Over Int.MAX_VALUE by four orders of magnitude. Held as Int these two would not compare at all.
        assertTrue(version("20260827120000") < version("20260827120001"))
        assertTrue(version("20260827120000") > version("9"))
    }

    // ---------------------------------------------------------------- identity

    @Test
    fun `trailing zeroes do not make a different version`() {
        assertEquals(version("1"), version("1.0"))
        assertEquals(version("1"), version("1.0.0"))
        assertEquals(version("1").hashCode(), version("1.0.0").hashCode())
    }

    @Test
    fun `a zero part that is not trailing counts`() {
        assertNotEquals(version("1.0.1"), version("1.1"))
        assertTrue(version("1.0.1") < version("1.1"))
    }

    @Test
    fun `version zero survives canonicalisation`() {
        assertEquals(version("0"), version("0.0"))
        assertTrue(version("0") < version("1"))
    }

    @Test
    fun `dot and underscore separate parts alike`() {
        // A class name cannot hold a dot, so both spellings have to mean the same version.
        assertEquals(version("2.1"), version("2_1"))
        assertEquals(version("1.2.3"), version("1_2_3"))
    }

    @Test
    fun `the canonical spelling writes underscores as dots`() {
        // A class name cannot hold a dot, and that grammar has no business reaching a column people query.
        val fromClass = version("2_1")
        assertEquals("2_1", fromClass.text)
        assertEquals("2.1", fromClass.canonical)
        assertEquals("2.1", fromClass.toString())
    }

    @Test
    fun `the canonical spelling keeps trailing zeroes`() {
        // Collapsing 1.0 to 1 would print one odd row among 1.0, 1.1, 1.2. Sameness is settled by parts.
        assertEquals("1.0", version("1.0").canonical)
        assertEquals(version("1"), version("1.0"))
    }

    @Test
    fun `the original spelling is what is kept for printing`() {
        val padded = version("1.0")
        assertEquals(version("1"), padded)
        assertEquals("1.0", padded.text)
        assertEquals("1.0", padded.toString())
    }

    // ---------------------------------------------------------------- refusals

    @Test
    fun `an empty version is refused`() {
        assertThrows<MigrationException> { version("") }
    }

    @Test
    fun `a part that is not a number is refused rather than dropped`() {
        // The failure this guards: "1.x.2" quietly becoming 1.2 by discarding what did not parse.
        val e = assertThrows<MigrationException> { version("1.x.2") }
        assertEquals(MigrationExceptionReason.INVALID_MIGRATION, e.reason)
        assertTrue(e.details!!.contains("\"x\""), "the refusal should name the part it choked on: ${e.details}")
    }

    @Test
    fun `a signed part is refused`() {
        assertThrows<MigrationException> { version("-1") }
        assertThrows<MigrationException> { version("1.-2") }
    }

    @Test
    fun `an empty part is refused`() {
        assertThrows<MigrationException> { version("1..2") }
        assertThrows<MigrationException> { version("1.") }
        assertThrows<MigrationException> { version(".1") }
    }

    @Test
    fun `a part too large for Long is refused`() {
        assertThrows<MigrationException> { version("99999999999999999999") }
    }
}
