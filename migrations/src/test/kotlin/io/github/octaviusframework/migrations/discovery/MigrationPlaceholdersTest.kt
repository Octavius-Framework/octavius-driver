package io.github.octaviusframework.migrations.discovery

import io.github.octaviusframework.migrations.MigrationException
import io.github.octaviusframework.migrations.MigrationExceptionReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MigrationPlaceholdersTest {

    private fun resolve(content: String, vararg placeholders: Pair<String, String>) =
        MigrationPlaceholders.resolve(content, "V1__x.sql", placeholders.toMap())

    // ------------------------------------------------------------- switched off

    @Test
    fun `an empty map leaves the file exactly as it was`() {
        // The whole cost of this feature to a project that never asked for it: a seed row holding a template
        // is not a migration anybody has to go and escape.
        val content = $$"""INSERT INTO templates (body) VALUES ('Salve, ${name}');"""
        assertEquals(content, MigrationPlaceholders.resolve(content, "V1__x.sql", emptyMap()))
    }

    // ------------------------------------------------------------- substitution

    @Test
    fun `a placeholder is replaced by its value`() {
        assertEquals(
            "GRANT USAGE ON SCHEMA roma TO legio;",
            resolve($$"""GRANT USAGE ON SCHEMA ${schema} TO ${role};""", "schema" to "roma", "role" to "legio")
        )
    }

    @Test
    fun `the same placeholder is replaced everywhere it appears`() {
        assertEquals(
            "CREATE SCHEMA roma; GRANT USAGE ON SCHEMA roma TO legio;",
            resolve($$"""CREATE SCHEMA ${s}; GRANT USAGE ON SCHEMA ${s} TO legio;""", "s" to "roma")
        )
    }

    @Test
    fun `the value goes in as the text it is, quoting and all`() {
        // A paste, not a parameter: nothing is quoted on the way in, which is why the values belong to the
        // deployment. Whatever is in the map is what the server reads.
        assertEquals(
            "INSERT INTO settings VALUES ('two words');",
            resolve($$"""INSERT INTO settings VALUES ('${label}');""", "label" to "two words")
        )
    }

    @Test
    fun `a value that looks like a placeholder is not substituted again`() {
        // One pass. Otherwise a value could reach back for another key, and what a file expanded to would
        // depend on the order the map happened to be read in.
        assertEquals(
            $$"""INSERT INTO templates VALUES ('${inner}');""",
            resolve(
                $$"""INSERT INTO templates VALUES ('${outer}');""",
                "outer" to $$"""${inner}""",
                "inner" to "never reached"
            )
        )
    }

    // ------------------------------------------------------------- the escape

    @Test
    fun `a backslash makes it ordinary text`() {
        assertEquals(
            $$"""INSERT INTO templates (body) VALUES ('Salve, ${name}');""",
            resolve(
                $$"""INSERT INTO templates (body) VALUES ('Salve, \${name}');""",
                "name" to "should not be used here"
            )
        )
    }

    @Test
    fun `the escape does not stop the placeholders around it`() {
        assertEquals(
            $$"""INSERT INTO templates VALUES ('roma', 'Salve, ${name}');""",
            resolve(
                $$"""INSERT INTO templates VALUES ('${schema}', 'Salve, \${name}');""",
                "schema" to "roma",
                "name" to "unused"
            )
        )
    }

    // ------------------------------------------------------------- refusals

    @Test
    fun `a placeholder with no value is refused`() {
        // The failure this exists for: left standing, it would sit inside a string literal, be stored
        // verbatim, and nothing would ever say so.
        val e = assertThrows<MigrationException> {
            resolve($$"""INSERT INTO settings VALUES ('${tenant}');""", "schema" to "roma")
        }

        assertEquals(MigrationExceptionReason.INVALID_MIGRATION, e.reason)
        assertTrue(e.details!!.contains("tenant"), "it should name the placeholder: ${e.details}")
        assertTrue(e.details.contains("schema"), "it should list what is configured: ${e.details}")
    }

    @Test
    fun `the refusal names the line the placeholder is on`() {
        val e = assertThrows<MigrationException> {
            resolve(
                $$"""
                SELECT 1;
                SELECT 2;
                GRANT ALL ON SCHEMA ${schema} TO legio;
                """.trimIndent(),
                "role" to "legio"
            )
        }
        assertTrue(e.details!!.contains("line 3"), "it should point at line 3: ${e.details}")
    }
}
