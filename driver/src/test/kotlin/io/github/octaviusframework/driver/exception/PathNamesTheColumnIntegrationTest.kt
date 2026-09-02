package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A failure inside a composite column has to read the same way whichever route reached it.
 *
 * `fetchObjects` maps the row through `ReflectionRowConverter`, which names each column as it descends;
 * `fetchField` and `Row.get` go through `Row.get`, which used to hand the value to the mapper with no segment
 * at all - so the same broken value arrived naming the attribute and never the column it was in.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PathNamesTheColumnIntegrationTest {

    /** `city` is `text` in the database, so reading it as an `Int` fails inside the composite. */
    data class PathAddress(val street: String, val city: Int)

    private fun session() = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")

    private val sql = "SELECT ROW('Via Sacra', 'Roma')::path_addr AS residence"

    @BeforeAll
    fun setup() {
        session().use {
            it.createNativeQuery("DROP TYPE IF EXISTS path_addr CASCADE").execute()
            it.createNativeQuery("CREATE TYPE path_addr AS (street text, city text)").execute()
        }
    }

    @AfterAll
    fun teardown() {
        session().use { it.createNativeQuery("DROP TYPE IF EXISTS path_addr CASCADE").execute() }
    }

    @Test
    fun `the column is on the path whichever route reached the failure`() {
        session().use { s ->
            s.reloadTypes()
            s.typeManager.registerAutoComposite<PathAddress>("path_addr")

            val throughFetchField = assertFailsWith<MappingException> {
                s.createNativeQuery(sql).fetchField<PathAddress>()
            }
            val throughRowGet = assertFailsWith<MappingException> {
                s.createNativeQuery(sql).fetchRowStrict().get<PathAddress>("residence")
            }

            assertEquals(listOf("residence", "city"), throughFetchField.path.asReversed())
            assertEquals(throughFetchField.path, throughRowGet.path, "the two routes should read alike")
        }
    }
}
