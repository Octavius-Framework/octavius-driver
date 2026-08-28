package io.github.octaviusframework.migrations

import io.github.octaviusframework.migrations.fixtures.staticinit.StaticInitWitness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class MigrationDiscoveryTest {

    private val fixtures = "io.github.octaviusframework.migrations.fixtures"

    private fun discover(
        sqlLocations: List<String> = emptyList(),
        codePackages: List<String> = emptyList()
    ) = MigrationDiscovery.discover(MigratorConfig(sqlLocations = sqlLocations, codePackages = codePackages))

    // ------------------------------------------------------------- from the classpath

    @Test
    fun `finds the sql migrations on the classpath`() {
        val found = discover(sqlLocations = listOf("testmigrations/basic"))

        assertEquals(listOf("1", "2.1", null), found.map { it.version?.canonical })
        assertEquals(listOf("create castra", "add index", "rebuild views"), found.map { it.description })
    }

    @Test
    fun `ignores a file that is not a migration`() {
        // There is a README.md sitting next to those three.
        val found = discover(sqlLocations = listOf("testmigrations/basic"))
        assertTrue(found.none { it.script.endsWith(".md") }, "found: ${found.map { it.script }}")
    }

    @Test
    fun `the classpath prefix is optional`() {
        assertEquals(
            discover(sqlLocations = listOf("testmigrations/basic")).map { it.script },
            discover(sqlLocations = listOf("classpath:testmigrations/basic")).map { it.script }
        )
    }

    @Test
    fun `a sql migration carries its text and its checksum`() {
        val migration = discover(sqlLocations = listOf("testmigrations/basic")).first() as DiscoveredMigration.Sql

        assertTrue(migration.content.contains("CREATE TABLE castra"))
        assertEquals(MigrationChecksum.of(migration.content), migration.checksum)
    }

    @Test
    fun `the no-transaction directive reaches the discovered migration`() {
        val migration = discover(sqlLocations = listOf("testmigrations/notx")).single() as DiscoveredMigration.Sql
        assertFalse(migration.transactional)
    }

    @Test
    fun `identity is the file name and provenance is the path`() {
        val migration = discover(sqlLocations = listOf("testmigrations/basic")).first()

        assertEquals("V1__create_castra.sql", migration.script)
        assertEquals("classpath:testmigrations/basic/V1__create_castra.sql", migration.origin)
    }

    // ------------------------------------------------------------- from a directory

    @Test
    fun `finds the sql migrations in a directory`(@TempDir dir: Path) {
        dir.resolve("V8__from_disk.sql").writeText("CREATE TABLE ex_disco (id int);")

        val found = discover(sqlLocations = listOf("filesystem:$dir"))

        assertEquals(1, found.size)
        assertEquals("V8__from_disk.sql", found.single().script)
        assertEquals("8", found.single().version?.text)
    }

    @Test
    fun `the suffix is matched whatever its case`(@TempDir dir: Path) {
        // Not written down anywhere, because nobody should have to know it - but a file named this way on a
        // case-insensitive filesystem being silently skipped would be worse than the rule being invisible.
        dir.resolve("V8__shouting.SQL").writeText("SELECT 1;")

        assertEquals("V8__shouting.SQL", discover(sqlLocations = listOf("filesystem:$dir")).single().script)
    }

    @Test
    fun `a file with another extension is not a migration`(@TempDir dir: Path) {
        dir.resolve("V8__not_really.psql").writeText("SELECT 1;")
        dir.resolve("V9__really.sql").writeText("SELECT 1;")

        assertEquals(listOf("V9__really.sql"), discover(sqlLocations = listOf("filesystem:$dir")).map { it.script })
    }

    @Test
    fun `walks into subdirectories`(@TempDir dir: Path) {
        dir.resolve("nested").createDirectories()
        dir.resolve("nested/V9__deeper.sql").writeText("SELECT 1;")

        assertEquals("V9__deeper.sql", discover(sqlLocations = listOf("filesystem:$dir")).single().script)
    }

    @Test
    fun `a directory that is not there is refused, not scanned into nothing`(@TempDir dir: Path) {
        val missing = dir.resolve("nowhere")

        val e = assertThrows<MigrationException> { discover(sqlLocations = listOf("filesystem:$missing")) }

        assertEquals(MigrationExceptionReason.CONFIGURATION, e.reason)
        assertTrue(e.details!!.contains("nowhere"), "the refusal should name the path: ${e.details}")
    }

    // ------------------------------------------------------------- from classes

    @Test
    fun `finds the code migrations and reads them from their names`() {
        val found = discover(codePackages = listOf("$fixtures.good"))

        assertEquals(listOf("5", "6.1", null), found.map { it.version?.canonical })
        assertEquals(listOf("Backfill provinces", "Rename legions", "Rebuild indexes"), found.map { it.description })
    }

    @Test
    fun `a code migration is a name and no checksum`() {
        val migration = discover(codePackages = listOf("$fixtures.good")).first()

        assertTrue(migration is DiscoveredMigration.Code)
        assertEquals("$fixtures.good.V5__Backfill_provinces", migration.script)
        assertNull(migration.checksum)
    }

    @Test
    fun `a class with no no-argument constructor is refused at the scan`() {
        val e = assertThrows<MigrationException> { discover(codePackages = listOf("$fixtures.noctor")) }

        assertEquals(MigrationExceptionReason.INVALID_MIGRATION, e.reason)
        assertTrue(e.details!!.contains("V7__Needs_an_argument"), "should name the class: ${e.details}")
    }

    @Test
    fun `a class whose name is not a migration name is refused`() {
        val e = assertThrows<MigrationException> { discover(codePackages = listOf("$fixtures.badname")) }
        assertEquals(MigrationExceptionReason.INVALID_MIGRATION, e.reason)
    }

    @Test
    fun `the scan loads a migration class without running its static initialiser`() {
        // The property the whole design rests on, and the reason the class is loaded at the scan at all:
        // loading is not initialising. If this ever flips, discovery starts running arbitrary code at
        // startup for migrations that ran months ago.
        assertFalse(
            StaticInitWitness.ran,
            "something initialised the fixture before this test - it cannot say anything then"
        )

        val found = discover(codePackages = listOf("$fixtures.staticinit"))

        assertEquals(1, found.size)
        assertFalse(StaticInitWitness.ran, "the scan must not have run the class's static initialiser")

        // And the other half: building it does.
        (found.single() as DiscoveredMigration.Code).migrationClass.getDeclaredConstructor().newInstance()
        assertTrue(StaticInitWitness.ran, "constructing the migration should have run it")
    }

    @Test
    fun `a code migration carries the class the scan found`() {
        val migration = discover(codePackages = listOf("$fixtures.good")).first() as DiscoveredMigration.Code

        assertEquals(migration.className, migration.migrationClass.name)
        assertTrue(
            OctaviusMigration::class.java.isAssignableFrom(migration.migrationClass),
            "the scan should only carry classes that are migrations"
        )
    }

    // ------------------------------------------------------------- order

    @Test
    fun `versioned migrations run in version order and repeatable ones last`() {
        val found = discover(
            sqlLocations = listOf("testmigrations/basic"),
            codePackages = listOf("$fixtures.good")
        )

        assertEquals(listOf("1", "2.1", "5", "6.1", null, null), found.map { it.version?.canonical })
        // Repeatables at the end because they lean on the schema the versioned ones just built, and
        // among themselves by description - a class and a file order against each other by what they
        // say, not by whether a package name sorts before a file name.
        assertEquals(
            listOf("Rebuild indexes", "rebuild views"),
            found.takeLast(2).map { it.description }
        )
    }

    // ------------------------------------------------------------- refusals

    @Test
    fun `the same repeatable in two locations is refused`(@TempDir dir: Path) {
        // A repeatable has no version, so nothing but the script rule can catch this one - which is what
        // makes it the case worth testing. Two versioned files sharing a name share a version too, and would
        // be caught either way.
        dir.resolve("R__rebuild_views.sql").writeText("SELECT 1;")

        val e = assertThrows<MigrationException> {
            discover(sqlLocations = listOf("testmigrations/basic", "filesystem:$dir"))
        }

        assertEquals(MigrationExceptionReason.DUPLICATE_MIGRATION, e.reason)
        assertTrue(e.details!!.contains("R__rebuild_views.sql"), "should name the file: ${e.details}")
    }

    @Test
    fun `two migrations claiming one version are refused`(@TempDir dir: Path) {
        // Not the same file name, and trailing zeroes do not make 1.0 a different version from 1.
        dir.resolve("V1.0__also_first.sql").writeText("SELECT 1;")

        val e = assertThrows<MigrationException> {
            discover(sqlLocations = listOf("testmigrations/basic", "filesystem:$dir"))
        }

        assertEquals(MigrationExceptionReason.DUPLICATE_MIGRATION, e.reason)
    }

    @Test
    fun `a sql file and a class claiming one version are refused`(@TempDir dir: Path) {
        dir.resolve("V5__also_fifth.sql").writeText("SELECT 1;")

        val e = assertThrows<MigrationException> {
            discover(sqlLocations = listOf("filesystem:$dir"), codePackages = listOf("$fixtures.good"))
        }

        assertEquals(MigrationExceptionReason.DUPLICATE_MIGRATION, e.reason)
    }

    @Test
    fun `nowhere to look is refused`() {
        val e = assertThrows<MigrationException> { discover() }
        assertEquals(MigrationExceptionReason.CONFIGURATION, e.reason)
    }

    @Test
    fun `a location that matches nothing is not an error`() {
        // An application whose migrations are all still ahead of it is a real state. It gets a warning.
        assertTrue(discover(sqlLocations = listOf("testmigrations/nothing-here")).isEmpty())
    }
}
