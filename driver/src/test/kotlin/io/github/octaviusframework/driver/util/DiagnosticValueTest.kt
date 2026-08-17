package io.github.octaviusframework.driver.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticValueTest {

    private data class Dto(val id: Int, val name: String)

    @Test
    fun `renders small values whole`() {
        assertEquals("null", formatDiagnosticValue(null))
        assertEquals("42", formatDiagnosticValue(42))
        assertEquals("Marcus", formatDiagnosticValue("Marcus"))
        assertEquals("Dto(id=1, name=Cicero)", formatDiagnosticValue(Dto(1, "Cicero")))
    }

    @Test
    fun `names a ByteArray instead of dumping it`() {
        assertEquals("ByteArray(4000000 bytes)", formatDiagnosticValue(ByteArray(4_000_000)))
    }

    @Test
    fun `cuts an oversized value`() {
        val rendered = formatDiagnosticValue("x".repeat(10_000))
        assertEquals(DIAGNOSTIC_VALUE_MAX_LENGTH + 3, rendered.length)
        assertTrue(rendered.endsWith("..."))
    }

    @Test
    fun `walks primitive arrays rather than printing an identity hash`() {
        assertEquals("[1, 2, 3]", formatDiagnosticValue(intArrayOf(1, 2, 3)))
        assertEquals("[true, false]", formatDiagnosticValue(booleanArrayOf(true, false)))
        assertEquals("[a, b]", formatDiagnosticValue(arrayOf("a", "b")))
    }

    @Test
    fun `counts the tail of a large collection instead of rendering it`() {
        assertEquals("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, ... +9990 more]", formatDiagnosticValue((0 until 10_000).toList()))
    }

    /**
     * The reason this file exists. A bulk write passes one array per column, so a ten-thousand
     * element parameter is the documented idiom rather than an abuse - and rendering it in full
     * before trimming would build a string of megabytes on the error path.
     */
    @Test
    fun `never materialises a huge container`() {
        val huge = List(100_000) { "x".repeat(1_000) }   // ~100 MB rendered in full
        val rendered = formatDiagnosticValue(huge)
        assertTrue(rendered.length <= DIAGNOSTIC_VALUE_MAX_LENGTH + 3, "was ${rendered.length} chars")
    }

    @Test
    fun `bounds a nested container by depth`() {
        // Three levels are walked; the fourth is reported by size instead of being descended into.
        val nested = listOf(listOf(listOf(listOf(1, 2, 3))))
        assertEquals("[[[(3 elements)]]]", formatDiagnosticValue(nested))
    }

    @Test
    fun `renders a ByteArray nested inside a collection`() {
        assertEquals("[ByteArray(8 bytes)]", formatDiagnosticValue(listOf(ByteArray(8))))
    }

    @Test
    fun `renders map entries`() {
        assertEquals("[a=1, b=2]", formatDiagnosticValue(linkedMapOf("a" to 1, "b" to 2)))
    }
}
