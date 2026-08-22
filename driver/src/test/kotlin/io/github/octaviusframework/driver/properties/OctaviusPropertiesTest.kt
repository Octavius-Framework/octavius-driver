package io.github.octaviusframework.driver.properties

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Properties

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
    fun `should parse a bracketed IPv6 host with a port`() {
        val props = OctaviusProperties.parse("jdbc:octavius://[2001:db8::1]:5433/res_publica?user=consul")

        // The brackets are the URL's, not the address's: what is stored is what gets connected to
        // and matched against a certificate.
        assertEquals("2001:db8::1", props.serverName)
        assertEquals(5433, props.portNumber)
        assertEquals("res_publica", props.databaseName)
        assertEquals("consul", props.user)
    }

    @Test
    fun `should parse a bracketed IPv6 host without a port`() {
        val props = OctaviusProperties.parse("jdbc:octavius://[::1]/res_publica")

        // Every colon here belongs to the address, so no port is stated and the default applies later.
        assertEquals("::1", props.serverName)
        assertNull(props.portNumber)
        assertEquals("res_publica", props.databaseName)
    }

    @Test
    fun `should round trip an IPv6 host through toUrl`() {
        val original = OctaviusProperties()
        original.serverName = "::1"
        original.portNumber = 5433
        original.databaseName = "res_publica"

        val parsed = OctaviusProperties.parse(original.toUrl())

        assertEquals("::1", parsed.serverName)
        assertEquals(5433, parsed.portNumber)
        assertEquals("res_publica", parsed.databaseName)
    }

    @Test
    fun `url should win over info where it states something`() {
        val info = Properties()
        info.setProperty("serverName", "from-info")
        info.setProperty("portNumber", "1111")
        info.setProperty("databaseName", "db-from-info")
        info.setProperty("user", "user-from-info")

        val props = OctaviusProperties.parse("jdbc:octavius://from-url:2222/db-from-url?user=user-from-url", info)

        assertEquals("from-url", props.serverName)
        assertEquals(2222, props.portNumber)
        assertEquals("db-from-url", props.databaseName)
        assertEquals("user-from-url", props.user)
    }

    @Test
    fun `url should leave info alone where it states nothing`() {
        val info = Properties()
        info.setProperty("serverName", "from-info")
        info.setProperty("portNumber", "1111")
        info.setProperty("databaseName", "db-from-info")

        val props = OctaviusProperties.parse("jdbc:octavius://?user=consul", info)

        assertEquals("from-info", props.serverName)
        assertEquals(1111, props.portNumber)
        assertEquals("db-from-info", props.databaseName)
        assertEquals("consul", props.user)
    }

    @Test
    fun `a url without a database should not reset the one info supplied`() {
        val info = Properties()
        info.setProperty("databaseName", "db-from-info")

        val props = OctaviusProperties.parse("jdbc:octavius://from-url:2222", info)

        assertEquals("from-url", props.serverName)
        assertEquals(2222, props.portNumber)
        assertEquals("db-from-info", props.databaseName)
    }

    @Test
    fun `a url without a port should not reset the one info supplied`() {
        val info = Properties()
        info.setProperty("portNumber", "1111")

        val props = OctaviusProperties.parse("jdbc:octavius://from-url/db", info)

        assertEquals("from-url", props.serverName)
        assertEquals(1111, props.portNumber)
        assertEquals("db", props.databaseName)
    }

    @Test
    fun `should leave host port and database unset when the url states none of them`() {
        val props = OctaviusProperties.parse("jdbc:octavius://")

        assertNull(props.serverName)
        assertNull(props.portNumber)
        assertNull(props.databaseName)
    }

    @Test
    fun `should read query parameters from a url that omits the database`() {
        val props = OctaviusProperties.parse("jdbc:octavius://db.local:5433?user=consul&socketTimeout=30")

        assertEquals("db.local", props.serverName)
        assertEquals(5433, props.portNumber)
        assertNull(props.databaseName)
        assertEquals("consul", props.user)
        assertEquals(30, props.socketTimeout)
    }

    @Test
    fun `a query parameter should win over the authority it follows`() {
        val props = OctaviusProperties.parse("jdbc:octavius://from-authority:2222/db?host=from-query&port=3333")

        assertEquals("from-query", props.serverName)
        assertEquals(3333, props.portNumber)
    }

    @Test
    fun `a url that is not ours should leave info untouched`() {
        val info = Properties()
        info.setProperty("serverName", "from-info")
        info.setProperty("databaseName", "db-from-info")

        val props = OctaviusProperties.parse("jdbc:postgresql://elsewhere:9999/other", info)

        assertEquals("from-info", props.serverName)
        assertEquals("db-from-info", props.databaseName)
    }

    @Test
    fun `should read an application name under either spelling`() {
        val underscored = OctaviusProperties.parse("jdbc:octavius://localhost:5432/res_publica?application_name=LegioXIII")
        val camelCased = OctaviusProperties.parse("jdbc:octavius://localhost:5432/res_publica?applicationName=LegioXIII")

        assertEquals("LegioXIII", underscored.applicationName)
        assertEquals("LegioXIII", camelCased.applicationName)

        // Recognised now, so neither spelling is left among the pass-through parameters
        assertEquals(emptyMap<String, String>(), underscored.additionalProperties)
        assertEquals(emptyMap<String, String>(), camelCased.additionalProperties)
    }

    @Test
    fun `should render the application name under PostgreSQL's own spelling`() {
        val original = OctaviusProperties()
        original.serverName = "localhost"
        original.applicationName = "Legio XIII"

        val url = original.toUrl()

        assertTrue(url.contains("application_name=Legio+XIII"), url)
        assertEquals("Legio XIII", OctaviusProperties.parse(url).applicationName)
    }

    @Test
    fun `the application name property should win over one left in additionalProperties`() {
        val props = OctaviusProperties()
        props.additionalProperties["application_name"] = "by-hand"
        props.applicationName = "typed"

        // Both would render under the same key, and the one the startup message sends is the one shown
        val url = props.toUrl()

        assertFalse(url.contains("by-hand"), url)
        assertEquals("typed", OctaviusProperties.parse(url).applicationName)
    }

    @Test
    fun `merge should carry the application name without erasing it`() {
        val base = OctaviusProperties()
        base.applicationName = "base"

        base.merge(OctaviusProperties())
        assertEquals("base", base.applicationName)

        base.merge(OctaviusProperties().apply { applicationName = "overlay" })
        assertEquals("overlay", base.applicationName)
        assertEquals("overlay", base.copy().applicationName)
    }

    @Test
    fun `should ignore a query entry that has no value`() {
        val props = OctaviusProperties.parse("jdbc:octavius://localhost:5432/res_publica?ssl&=orphan&user=consul")

        assertNull(props.ssl)
        assertEquals("consul", props.user)
        assertEquals(emptyMap<String, String>(), props.additionalProperties)
    }
}
