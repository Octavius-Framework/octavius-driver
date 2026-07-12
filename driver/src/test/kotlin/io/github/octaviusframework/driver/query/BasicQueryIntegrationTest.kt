package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals

class BasicQueryIntegrationTest {

    @Test
    fun test() {
        println("Zaczynamy test!")
        
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")
        
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val result = session.createNativeQuery("SELECT 1, 'abc', 4.5::float8").fetchAll()
        val row = result.first()
        assertEquals(1, row.get(0))
        assertEquals("abc", row.get(1))
        assertEquals(4.5, row.get(2))

        val result2 = session.createNativeQuery("SELECT $1 as test_int, $2 as test_float, $1 as test_int2")
            .fetchOne(1, 2.4f)
        assertEquals(1, result2.get(0))
        assertEquals(2.4f, result2.get(1))
        assertEquals(1, result2.get(2))
    }
}
