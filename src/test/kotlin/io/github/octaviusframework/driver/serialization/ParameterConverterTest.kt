package io.github.octaviusframework.driver.serialization

import io.github.octaviusframework.driver.jdbc.OctaviusConnection
import io.github.octaviusframework.driver.jdbc.getOctaviusConnection
import io.github.octaviusframework.driver.mapping.result.ResultMapper
import io.github.octaviusframework.driver.query.ParameterSerializer
import io.github.octaviusframework.driver.query.get
import io.github.octaviusframework.driver.query.getEntireRowAs
import io.github.octaviusframework.driver.type.containter.PgArray
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParameterConverterTest {

    private lateinit var octaviusConn: OctaviusConnection
    private lateinit var parameterSerializer: ParameterSerializer

    data class SimpleAddress(val city: String, val zip: String)
    data class ComplexUser(val id: Int, val name: String, val address: SimpleAddress, val tags: List<String>)

    @BeforeAll
    fun setup() {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")
        octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", props)

        octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS simple_address CASCADE")
        octaviusConn.queryExecutor.execute("CREATE TYPE simple_address AS (city text, zip text)")
        
        octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS complex_user CASCADE")
        octaviusConn.queryExecutor.execute("CREATE TYPE complex_user AS (id int, name text, address simple_address, tags text[])")
        
        octaviusConn.reloadTypes()
        octaviusConn.typeRegistry.registerAutoCompositeType<SimpleAddress>("simple_address")
        octaviusConn.typeRegistry.registerAutoCompositeType<ComplexUser>("complex_user")
        
        val dummyRow = octaviusConn.createNativeQuery("SELECT 1").fetchAll().first()
        parameterSerializer = ParameterSerializer(dummyRow.typeRegistry, dummyRow.typeRegistry.parameterConverterRegistry)
    }

    @Test
    fun testDataClassToCompositeConversion() {
        val address = SimpleAddress("Warsaw", "00-001")
        val user = ComplexUser(42, "Kacper", address, listOf("developer", "kotlin"))

        val param = parameterSerializer.serializeWithOid(user)
        
        assertTrue(param.oid > 0u, "OID should be resolved for the ComplexUser")

        val rows = octaviusConn.queryExecutor.query(
            "SELECT ($1).*",
            paramTypes = listOf(param.oid),
            paramValues = listOf(param.value),
            mapper = ResultMapper(octaviusConn.converterRegistry)
        )
        
        val returnedUser = rows.first().getEntireRowAs<ComplexUser>()
        assertNotNull(returnedUser)
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
        val param = parameterSerializer.serializeWithOid(list)

        val rows = octaviusConn.queryExecutor.query(
            "SELECT $1::text[] as res",
            paramTypes = listOf(param.oid),
            paramValues = listOf(param.value),
            mapper = ResultMapper(octaviusConn.converterRegistry)
        )

        val returnedArray = rows.first().get<PgArray>("res")
        assertNotNull(returnedArray)
        assertEquals("one", returnedArray.get<String>(0))
        assertEquals("two", returnedArray.get<String>(1))
        assertEquals("three", returnedArray.get<String>(2))
    }
}
