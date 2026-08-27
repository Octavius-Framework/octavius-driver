package io.github.octaviusframework.client

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.octaviusframework.client.dynamic.DynamicDto
import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.MappingException
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers `dynamic_dto` end to end: one column holding several unrelated shapes, written from Kotlin and built
 * in SQL, and read back as the classes registered for them.
 */
class DynamicDtoTest {

    @OptIn(ExperimentalSerializationApi::class)
    private val snakeCase = Json { namingStrategy = JsonNamingStrategy.SnakeCase }

    sealed interface Benefit

    @Serializable
    data class LandGrant(val province: String, val iugera: Int) : Benefit

    @Serializable
    data class MilitaryPension(val legion: String, val annual: Int) : Benefit

    @Serializable
    data class Citation(val text: String)

    /** camelCase properties, so a payload SQL built the way SQL names things does not fit the default Json. */
    @Serializable
    data class Stipend(val provinceName: String, val annualAmount: Int)

    companion object {
        private lateinit var dataSource: HikariDataSource
        private lateinit var db: OctaviusClient

        @BeforeAll
        @JvmStatic
        fun setUp() {
            dataSource = HikariDataSource(HikariConfig().apply {
                jdbcUrl = "jdbc:octavius://localhost:5432/octavius_test"
                username = "postgres"
                password = "1234"
                maximumPoolSize = 2
            })
            db = OctaviusClient.fromDataSource(dataSource)

            // Installing after the pool was built is the harder case: the driver read its type catalogue when
            // it connected, so this only works if install() reloads it.
            db.dynamicTypes.install()
            db.dynamicTypes.install() // twice, because the DDL claims to be safe run twice

            db.dynamicTypes.register<LandGrant>("land_grant")
            db.dynamicTypes.register<MilitaryPension>("military_pension")
            db.dynamicTypes.register<Citation>("citation")
            db.dynamicTypes.register<Stipend>("stipend")

            db.rawQuery(
                """
                CREATE TABLE IF NOT EXISTS dyn_veterans (
                    id      SERIAL PRIMARY KEY,
                    name    TEXT NOT NULL,
                    benefit public.dynamic_dto
                )
                """.trimIndent()
            ).execute()
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            db.rawQuery("DROP TABLE IF EXISTS dyn_veterans").execute()
            db.close()
            dataSource.close()
        }
    }

    @BeforeEach
    fun clearTable() {
        db.rawQuery("TRUNCATE dyn_veterans RESTART IDENTITY").execute()
    }

    // Unwrapped, which is what the default mode makes possible and what nearly every test here goes through.
    // Wrapping is covered on its own below.
    private fun record(name: String, benefit: Any) {
        db.insertInto("dyn_veterans").values(listOf("name", "benefit"))
            .update("name" to name, "benefit" to benefit)
    }

    // --- The round trip ---------------------------------------------------------------------------

    @Test
    fun `a value written from Kotlin comes back as the class it was`() {
        val grant = LandGrant("Gallia Narbonensis", 120)
        record("Marcus", grant)

        val read = db.select("benefit").from("dyn_veterans").fetchFieldStrict<LandGrant>()

        assertEquals(grant, read)
    }

    @Test
    fun `one column holds several shapes and reads back as their common supertype`() {
        // This is what a COMPOSITE cannot do: the column's shape is decided per row, not per schema.
        record("Marcus", LandGrant("Gallia Narbonensis", 120))
        record("Lucius", MilitaryPension("X Fretensis", 900))
        record("Gaius", LandGrant("Hispania", 40))

        val benefits = db.select("benefit").from("dyn_veterans").orderBy("id")
            .fetchFields<Benefit>()

        assertEquals(
            listOf(
                LandGrant("Gallia Narbonensis", 120),
                MilitaryPension("X Fretensis", 900),
                LandGrant("Hispania", 40)
            ),
            benefits
        )
    }

    @Test
    fun `a wrapped value writes the same as an unwrapped one`() {
        // toDynamicDto stops being required under the default mode, but it does not stop working: it is still
        // what settles a class registered as a composite as well, and it takes the same path here.
        val grant = LandGrant("Gallia Narbonensis", 120)
        db.insertInto("dyn_veterans").values(listOf("name", "benefit"))
            .update("name" to "Marcus", "benefit" to db.dynamicTypes.toDynamicDto(grant))

        assertEquals(grant, db.select("benefit").from("dyn_veterans").fetchFieldStrict<LandGrant>())
    }

    @Test
    fun `a value built in SQL reads back the same way`() {
        // Nothing about the read path needs the value to have come from Kotlin: SQL naming the type and
        // building the payload is the whole contract, which is what makes ad-hoc projections work.
        val benefit = db.rawQuery(
            "SELECT dynamic_dto('land_grant', jsonb_build_object('province', @p, 'iugera', @i))"
        ).fetchFieldStrict<Benefit>("p" to "Britannia", "i" to 75)

        assertEquals(LandGrant("Britannia", 75), benefit)
    }

    @Test
    fun `a class with no relation to its stored name works the same`() {
        record("Marcus", Citation("ob civem servatum"))

        assertEquals(
            Citation("ob civem servatum"),
            db.select("benefit").from("dyn_veterans").fetchFieldStrict<Citation>()
        )
    }

    @Test
    fun `the raw form is still reachable for anyone who wants the payload`() {
        record("Marcus", LandGrant("Gallia Narbonensis", 120))

        val raw = db.select("benefit").from("dyn_veterans").fetchFieldStrict<DynamicDto>()

        assertEquals("land_grant", raw.typeName)
        // The payload comes back as the text the column holds, so it is JSON and it parses - which is what
        // says the read handed over jsonb's own output rather than something's toString().
        assertEquals(
            "Gallia Narbonensis",
            Json.parseToJsonElement(raw.dataPayload).jsonObject.getValue("province").jsonPrimitive.content
        )
    }

    @Test
    fun `asking for a Map gets the composite's own attributes`() {
        record("Marcus", LandGrant("Gallia Narbonensis", 120))

        val asMap = db.select("benefit").from("dyn_veterans").fetchFieldStrict<Map<String, Any?>>()

        assertEquals(setOf("type_name", "data_payload"), asMap.keys)
        assertEquals("land_grant", asMap["type_name"])
    }

    @Test
    fun `a null column is still null`() {
        db.insertInto("dyn_veterans").values(listOf("name")).update("name" to "Sine Praemio")

        assertEquals(null, db.select("benefit").from("dyn_veterans").fetchFieldStrict<Benefit?>())
    }

    // --- A different Json, for one query --------------------------------------------------------------

    @Test
    fun `a payload named the way SQL names things reads under a Json given to that query alone`() {
        val out = db.rawQuery(
            "SELECT dynamic_dto('stipend', jsonb_build_object('province_name', @p, 'annual_amount', @a))"
        )
            .registerResultConverter(db.dynamicTypes.resultConverter(snakeCase))
            .fetchFieldStrict<Stipend>("p" to "Aegyptus", "a" to 500)

        assertEquals(Stipend("Aegyptus", 500), out)
    }

    @Test
    fun `and the client's own Json still refuses it everywhere else`() {
        // Which is what says the converter was the query's and not the connection's: the same SQL, one line
        // shorter, has to fail - the default Json being strict about a key the class does not declare.
        assertFailsWith<MappingException> {
            db.rawQuery(
                "SELECT dynamic_dto('stipend', jsonb_build_object('province_name', @p, 'annual_amount', @a))"
            ).fetchFieldStrict<Stipend>("p" to "Aegyptus", "a" to 500)
        }
    }

    @Test
    fun `the write side takes a Json the same two ways`() {
        val stipend = Stipend("Cyrenaica", 220)

        // Once through the wrapper, which takes one directly...
        db.insertInto("dyn_veterans").values(listOf("name", "benefit"))
            .update("name" to "Marcus", "benefit" to db.dynamicTypes.toDynamicDto(stipend, snakeCase))
        // ...and once unwrapped, through a converter given to this query alone.
        db.insertInto("dyn_veterans").values(listOf("name", "benefit"))
            .registerParameterConverter(db.dynamicTypes.parameterConverter(snakeCase))
            .update("name" to "Lucius", "benefit" to stipend)

        assertEquals(
            listOf("Cyrenaica", "Cyrenaica"),
            db.select("(benefit).data_payload ->> 'province_name'").from("dyn_veterans").orderBy("id")
                .fetchFields<String>()
        )
    }

    // --- What it refuses --------------------------------------------------------------------------

    @Test
    fun `a name nothing was registered under says so`() {
        val thrown = assertFailsWith<MappingException> {
            db.rawQuery("SELECT dynamic_dto('triumph', jsonb_build_object('x', 1))")
                .fetchFieldStrict<Benefit>()
        }
        assertTrue(thrown.details.contains("'triumph'"), "the message should name the type it could not find")
    }

    @Test
    fun `a value that is not what the caller asked for says which is which`() {
        record("Lucius", MilitaryPension("X Fretensis", 900))

        val thrown = assertFailsWith<MappingException> {
            db.select("benefit").from("dyn_veterans").fetchFieldStrict<LandGrant>()
        }
        assertTrue(thrown.details.contains("MilitaryPension"))
        assertTrue(thrown.details.contains("LandGrant"))
    }

    @Test
    fun `a payload that does not fit the class says so rather than half-filling it`() {
        val thrown = assertFailsWith<MappingException> {
            db.rawQuery("SELECT dynamic_dto('land_grant', jsonb_build_object('province', 'Gallia'))")
                .fetchFieldStrict<Benefit>()
        }
        assertTrue(thrown.details.contains("land_grant"))
    }

    @Test
    fun `wrapping an unregistered class points at the fix`() {
        data class Unregistered(val x: Int)

        val thrown = assertFailsWith<InvalidOperationException> {
            db.dynamicTypes.toDynamicDto(Unregistered(1))
        }
        assertTrue(thrown.details!!.contains("register"))
    }

    @Test
    fun `two classes cannot take one name`() {
        assertFailsWith<InvalidOperationException> {
            db.dynamicTypes.register<Citation>("land_grant")
        }
    }

    @Test
    fun `registering the same class twice is harmless`() {
        db.dynamicTypes.register<LandGrant>("land_grant")
        record("Marcus", LandGrant("Gallia Narbonensis", 120))
        assertIs<LandGrant>(db.select("benefit").from("dyn_veterans").fetchFieldStrict<Benefit>())
    }
}
