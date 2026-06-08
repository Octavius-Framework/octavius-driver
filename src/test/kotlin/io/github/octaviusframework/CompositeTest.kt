package io.github.octaviusframework


import java.util.Properties
import io.github.octaviusframework.jdbc.getOctaviusConnection
import org.junit.jupiter.api.Test

class CompositeTest {

    @Test
    fun test() {
        println("Zaczynamy test!")

        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", props)

        println("Tworzę testowy kompozyt w bazie...")
        octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS my_custom_composite CASCADE")
        octaviusConn.queryExecutor.execute("CREATE TYPE my_custom_composite AS (id int, name text)")
        octaviusConn.reloadTypes()
        val result = octaviusConn.queryExecutor.query("SELECT ROW(5, 'aaaaaa')::my_custom_composite").first()

    }
}
