package io.github.octaviusframework.driver.serialization

import io.github.octaviusframework.driver.container.PgArray
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.session.OctaviusSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParameterConverterTest {

    private lateinit var session: OctaviusSession

    data class SimpleAddress(val city: String, val zip: String)
    data class ComplexUser(val id: Int, val name: String, val address: SimpleAddress, val tags: List<String>)

    @BeforeAll
    fun setup() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"
        session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        session.createNativeQuery("DROP TYPE IF EXISTS simple_address CASCADE").execute()
        session.createNativeQuery("CREATE TYPE simple_address AS (city text, zip text)").execute()

        session.createNativeQuery("DROP TYPE IF EXISTS complex_user CASCADE").execute()
        session.createNativeQuery("CREATE TYPE complex_user AS (id int, name text, address simple_address, tags text[])").execute()
        
        session.reloadTypes()
        session.typeManager.registerAutoComposite<SimpleAddress>("simple_address")
        session.typeManager.registerAutoComposite<ComplexUser>("complex_user")
    }

    @AfterAll
    fun teardown() {
        session.close()
    }

    @Test
    fun testDataClassToCompositeConversion() {
        val address = SimpleAddress("Warsaw", "00-001")
        val user = ComplexUser(42, "Kacper", address, listOf("developer", "kotlin"))

        val returnedUser = session.createNativeQuery("SELECT ($1).*")
            .fetchObjectStrict<ComplexUser>(user)
        assertEquals(42, returnedUser.id)
        assertEquals("Kacper", returnedUser.name)
        assertEquals("Warsaw", returnedUser.address.city)
        assertEquals("00-001", returnedUser.address.zip)
        assertEquals(2, returnedUser.tags.size)
        assertEquals("developer", returnedUser.tags[0])
        assertEquals("kotlin", returnedUser.tags[1])
    }

    @Test
    fun testSimpleListConversion() {
        val list = listOf("one", "two", "three")
        val returnedArray = session.createNativeQuery("SELECT $1 as res")
            .fetchRows(list)
            .first()
            .get<PgArray>("res")
        assertNotNull(returnedArray)
        assertEquals("one", returnedArray.get<String>(0))
        assertEquals("two", returnedArray.get<String>(1))
        assertEquals("three", returnedArray.get<String>(2))
    }
}

