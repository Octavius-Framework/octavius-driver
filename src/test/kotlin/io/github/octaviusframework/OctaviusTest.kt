package io.github.octaviusframework

import io.github.octaviusframework.io.toByteArrayBE
import java.sql.DriverManager
import java.util.Properties
import io.github.octaviusframework.jdbc.OctaviusConnection
import io.github.octaviusframework.query.get
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import kotlin.test.assertEquals

class OctaviusTest {

    @Test
    fun test() {
        println("Zaczynamy test!")
        
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")
        
        val connection = DriverManager.getConnection("jdbc:octavius://localhost:5432/octavius_test", props)
        val octaviusConn = connection.unwrap(OctaviusConnection::class.java)

        val result = octaviusConn.queryExecutor.query("SELECT 1, 'abc', 4.5::float8")
        val row = result.first()
        assertEquals(1, row.get(0))
        assertEquals("abc", row.get(1))
        assertEquals(4.5, row.get(2))

        val result2 = octaviusConn.queryExecutor.query("SELECT $1 as test_int, $2 as test_float, $1 as test_int2", listOf(23u,700u), listOf(1.toByteArrayBE(), 2.4f.toByteArrayBE())).first()
        assertEquals(1, result2.get(0))
        assertEquals(2.4f, result2.get(1))
        assertEquals(1, result2.get(2))
    }
}
