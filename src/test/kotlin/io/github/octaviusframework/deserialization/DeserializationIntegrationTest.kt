package io.github.octaviusframework.deserialization

import io.github.octaviusframework.container.PgArray
import io.github.octaviusframework.container.PgComposite
import io.github.octaviusframework.jdbc.OctaviusConnection
import io.github.octaviusframework.jdbc.unwrap
import io.github.octaviusframework.query.get
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.Properties
import kotlin.reflect.typeOf

class DeserializationIntegrationTest {

    data class IntegrationAddress(val street: String, val city: String)
    data class IntegrationUser(val id: Int, val name: String, val address: IntegrationAddress)

    private fun getConnection(): java.sql.Connection? {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        return try {
            DriverManager.getConnection("jdbc:octavius://localhost:5432/octavius_test", props)
        } catch (e: Exception) {
            println("Brak dostępu do bazy, ignorowanie testu: ${e.message}")
            null
        }
    }

    @Test
    fun testRealDatabaseDeserialization() {
        val connection = getConnection() ?: return
        val octaviusConn = connection.unwrap(OctaviusConnection::class.java)

        try {
            octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS integ_address CASCADE")
            octaviusConn.queryExecutor.execute("CREATE TYPE integ_address AS (street text, city text)")

            octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS integ_user CASCADE")
            octaviusConn.queryExecutor.execute("CREATE TYPE integ_user AS (id int, name text, address integ_address)")

            octaviusConn.reloadTypes()

            val result = octaviusConn.queryExecutor.query("SELECT ROW(10, 'Jan Kowalski', ROW('Marszałkowska', 'Warszawa')::integ_address)::integ_user AS usr").first()
            
            // Oczekujemy, że mechanizm automatycznie użyje domyślnego deserializera zaimplementowanego w Row.get
            val parsedUser = result.get<IntegrationUser>("usr")

            assertNotNull(parsedUser)
            assertEquals(10, parsedUser.id)
            assertEquals("Jan Kowalski", parsedUser.name)
            assertEquals("Marszałkowska", parsedUser.address.street)
            assertEquals("Warszawa", parsedUser.address.city)
            
        } finally {
            try {
                octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS integ_user CASCADE")
                octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS integ_address CASCADE")
            } catch (e: Exception) {
            }
            connection.close()
        }
    }

    @Test
    fun testRealDatabaseArrayDeserialization() {
        val connection = getConnection() ?: return
        val octaviusConn = connection.unwrap<OctaviusConnection>()

        try {
            octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS integ_address CASCADE")
            octaviusConn.queryExecutor.execute("CREATE TYPE integ_address AS (street text, city text)")

            octaviusConn.reloadTypes()

            val result = octaviusConn.queryExecutor.query("SELECT ARRAY[ROW('M1', 'W1')::integ_address, ROW('M2', 'W2')::integ_address] AS addresses").first()

            // Oczekujemy, że mechanizm automatycznie użyje domyślnego deserializera zaimplementowanego w Row.get
            val parsedList = result.get<List<IntegrationAddress>>("addresses")

            assertNotNull(parsedList)
            assertEquals(2, parsedList.size)
            assertEquals("M1", parsedList[0].street)
            assertEquals("W2", parsedList[1].city)
            
        } finally {
            try {
                octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS integ_address CASCADE")
            } catch (e: Exception) {
            }
            connection.close()
        }
    }
}
