package io.github.octaviusframework

import io.github.octaviusframework.io.toByteArrayBE
import java.sql.DriverManager
import java.util.Properties
import io.github.octaviusframework.jdbc.OctaviusConnection
import io.github.octaviusframework.query.get
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CompositeTest {

    @Test
    fun test() {
        println("Zaczynamy test!")

        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val connection = DriverManager.getConnection("jdbc:octavius://localhost:5432/postgres", props)
        val octaviusConn = connection.unwrap(OctaviusConnection::class.java)

        println("Tworzę testowy kompozyt w bazie...")
        octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS my_custom_composite CASCADE")
        octaviusConn.queryExecutor.execute("CREATE TYPE my_custom_composite AS (id int, name text)")
        octaviusConn.reloadTypes()
        val result = octaviusConn.queryExecutor.query("SELECT ROW(5, 'aaaaaa')::my_custom_composite").first()

    }
}
