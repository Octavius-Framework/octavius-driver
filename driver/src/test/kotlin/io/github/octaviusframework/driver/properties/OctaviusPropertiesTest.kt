package io.github.octaviusframework.driver.properties

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OctaviusPropertiesTest {

    @Test
    fun `should parse host port database and query parameters`() {
        val props = OctaviusProperties.parse("jdbc:octavius://db.local:5433/res_publica?user=consul&socketTimeout=30")

        assertEquals("db.local", props.serverName)
        assertEquals(5433, props.portNumber)
        assertEquals("res_publica", props.databaseName)
        assertEquals("consul", props.user)
        assertEquals(30, props.socketTimeout)
    }

    @Test
    fun `should keep a value containing an equals sign`() {
        val props = OctaviusProperties.parse("jdbc:octavius://localhost:5432/res_publica?password=sen=atus=&user=consul")

        assertEquals("sen=atus=", props.password)
        assertEquals("consul", props.user)
    }

    @Test
    fun `should keep an equals sign inside a startup parameter`() {
        val props = OctaviusProperties.parse("jdbc:octavius://localhost:5432/res_publica?options=-c search_path=curia")

        assertEquals("-c search_path=curia", props.additionalProperties["options"])
    }

    @Test
    fun `should survive a round trip through toUrl`() {
        val original = OctaviusProperties()
        original.serverName = "localhost"
        original.portNumber = 5432
        original.databaseName = "res_publica"
        original.sslpassword = "sen=atus"
        original.additionalProperties["options"] = "-c search_path=curia"

        val parsed = OctaviusProperties.parse(original.toUrl())

        assertEquals("sen=atus", parsed.sslpassword)
        assertEquals("-c search_path=curia", parsed.additionalProperties["options"])
    }

    @Test
    fun `should ignore a query entry that has no value`() {
        val props = OctaviusProperties.parse("jdbc:octavius://localhost:5432/res_publica?ssl&=orphan&user=consul")

        assertNull(props.ssl)
        assertEquals("consul", props.user)
        assertEquals(emptyMap<String, String>(), props.additionalProperties)
    }
}
