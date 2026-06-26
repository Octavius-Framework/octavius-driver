package io.github.octaviusframework.driver

import io.github.octaviusframework.driver.io.toByteArrayBE
import io.github.octaviusframework.driver.jdbc.getOctaviusConnection
import io.github.octaviusframework.driver.mapping.result.ResultMapper
import io.github.octaviusframework.driver.query.get
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

class OctaviusTest {

    @Test
    fun test() {
        println("Zaczynamy test!")
        
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")
        
        val octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", props)

        val result = octaviusConn.createNativeQuery("SELECT 1, 'abc', 4.5::float8").fetchAll()
        val row = result.first()
        assertEquals(1, row.get(0))
        assertEquals("abc", row.get(1))
        assertEquals(4.5, row.get(2))

        val result2 = octaviusConn.queryExecutor.query("SELECT $1 as test_int, $2 as test_float, $1 as test_int2", listOf(23u,700u), listOf(1.toByteArrayBE(), 2.4f.toByteArrayBE()), ResultMapper(octaviusConn.converterRegistry)).first()
        assertEquals(1, result2.get(0))
        assertEquals(2.4f, result2.get(1))
        assertEquals(1, result2.get(2))
    }
}
