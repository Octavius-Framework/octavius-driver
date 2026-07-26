package io.github.octaviusframework.driver.jdbc

import io.github.octaviusframework.driver.ssl.SslMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Properties

class OctaviusDataSourceInitializationTest {

    @Test
    fun `should parse url correctly and set properties`() {
        val ds = OctaviusDataSource()
        ds.url = "jdbc:octavius://myhost:5433/mydb?user=testuser&password=testpass&ssl=true&sslmode=require"

        assertEquals("myhost", ds.serverName)
        assertEquals(5433, ds.portNumber)
        assertEquals("mydb", ds.databaseName)
        assertEquals("testuser", ds.user)
        assertEquals("testpass", ds.password)
        assertEquals("true", ds.ssl)
        assertEquals(SslMode.REQUIRE, ds.sslmode)
    }

    @Test
    fun `should merge properties when url is set after explicit setters`() {
        val ds = OctaviusDataSource()
        ds.user = "explicituser"
        ds.loginTimeout = 30
        
        // This will override explicituser to urluser, because url parsing merges into existing
        ds.url = "jdbc:octavius://myhost:5432/mydb?user=urluser"

        assertEquals("urluser", ds.user)
        assertEquals(30, ds.loginTimeout)
        assertEquals("myhost", ds.serverName)
        assertEquals(5432, ds.portNumber)
    }

    @Test
    fun `should keep explicit properties when set after url`() {
        val ds = OctaviusDataSource()
        ds.url = "jdbc:octavius://myhost:5432/mydb?user=urluser"
        ds.user = "explicituser"

        assertEquals("explicituser", ds.user)
        assertEquals("myhost", ds.serverName)
    }
}
