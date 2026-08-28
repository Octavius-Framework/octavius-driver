package io.github.octaviusframework.migrations

import io.github.octaviusframework.driver.jdbc.OctaviusDataSource
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.session.OctaviusSession

/**
 * The database the integration tests here run against, and the schema they are allowed to make a mess of.
 *
 * Every test opens a session of its own. Sharing one would make a single failed transaction knock over every
 * test after it, and a suite where one fault reads as ten proves nothing about nine of them.
 */
internal object MigrationTestDatabase {

    const val SCHEMA = "octavius_migrations_test"

    fun session(): OctaviusSession = getOctaviusSession(
        "jdbc:octavius://localhost:5432/octavius_test",
        OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
        }
    )

    /** Wipes the test schema and puts an empty one back. */
    fun reset() {
        session().use { session ->
            session.createNativeQuery(
                """
                DROP SCHEMA IF EXISTS "$SCHEMA" CASCADE;
                CREATE SCHEMA "$SCHEMA";
                """.trimIndent()
            ).execute()
        }
    }

    /** The same database behind a `DataSource`, which is how the migrator is meant to be reached. */
    fun dataSource(): OctaviusDataSource = OctaviusDataSource().apply {
        url = "jdbc:octavius://localhost:5432/octavius_test"
        user = "postgres"
        password = "1234"
    }

    fun drop() {
        session().use { it.createNativeQuery("""DROP SCHEMA IF EXISTS "$SCHEMA" CASCADE""").execute() }
    }
}
