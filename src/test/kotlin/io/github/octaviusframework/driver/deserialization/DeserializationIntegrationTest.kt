package io.github.octaviusframework.driver.deserialization

import io.github.octaviusframework.driver.jdbc.getOctaviusConnection
import io.github.octaviusframework.driver.mapping.result.ResultConverter
import io.github.octaviusframework.driver.mapping.result.DeserializationContext
import io.github.octaviusframework.driver.query.get
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.containter.PgComposite
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import kotlin.reflect.KType
import kotlin.reflect.typeOf


class DeserializationIntegrationTest {

    data class IntegrationAddress(val street: String, val city: String)
    data class IntegrationUser(val id: Int, val name: String, val address: IntegrationAddress)

    @Test
    fun testRealDatabaseDeserialization() {
        val octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")

        try {
            octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS integ_address CASCADE")
            octaviusConn.queryExecutor.execute("CREATE TYPE integ_address AS (street text, city text)")

            octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS integ_user CASCADE")
            octaviusConn.queryExecutor.execute("CREATE TYPE integ_user AS (id int, name text, address integ_address)")

            octaviusConn.reloadTypes()
            octaviusConn.typeRegistry.registerCompositeType<IntegrationAddress>("integ_address")
            octaviusConn.typeRegistry.registerCompositeType<IntegrationUser>("integ_user")

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
            octaviusConn.close()
        }
    }

    @Test
    fun testRealDatabaseArrayDeserialization() {
        val octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")

        try {
            octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS integ_address CASCADE")
            octaviusConn.queryExecutor.execute("CREATE TYPE integ_address AS (street text, city text)")

            octaviusConn.reloadTypes()
            octaviusConn.typeRegistry.registerCompositeType<IntegrationAddress>("integ_address")

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
            octaviusConn.close()
        }
    }

    @Test
    fun testJsonDeserialization() {
        val octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")

        try {
            val result = octaviusConn.queryExecutor.query("SELECT '{\"key\": \"value1\"}'::json AS js, '{\"key2\": \"value2\"}'::jsonb AS jsb").first()

            val js = result.get<JsonElement>("js")
            val jsb = result.get<JsonElement>("jsb")
            val jsAny = result.get<Any>("js")
            val jsbAny = result.get<Any>("jsb")

            assertNotNull(js)
            assertNotNull(jsb)

            println("jsAny type is: ${jsAny.javaClass.name}")
            println("jsbAny type is: ${jsbAny.javaClass.name}")

            val jsonObjectJs = js as JsonObject
            val jsonObjectJsb = jsb as JsonObject

            assertEquals("value1", jsonObjectJs["key"]?.let { (it as JsonPrimitive).content })
            assertEquals("value2", jsonObjectJsb["key2"]?.let { (it as JsonPrimitive).content })
            
            if (jsAny is JsonObject) {
                assertEquals("value1", jsAny["key"]?.let { (it as JsonPrimitive).content })
            } else {
                fail("jsAny is not a JsonObject, it is \${jsAny?.javaClass?.name}")
            }
            if (jsbAny is JsonObject) {
                assertEquals("value2", jsbAny["key2"]?.let { (it as JsonPrimitive).content })
            } else {
                fail("jsbAny is not a JsonObject, it is ${jsbAny.javaClass.name}")
            }

        } finally {
            octaviusConn.close()
        }
    }

    enum class TestStatus { ACTIVE, INACTIVE, UNKNOWN }
    data class TestUserData(val code: String, val status: TestStatus)

    @Test
    fun testExplicitEnumAndCompositeConverters() {
        val octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")

        try {
            // Rejestracja własnych, jawnych konwerterów
            octaviusConn.typeRegistry.registerResultConverter(object : ResultConverter<TestStatus> {
                override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
                    return expectedType.classifier == TestStatus::class || sourceType.name == "test_status_enum"
                }
                override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType): TestStatus {
                    val str = source.toString()
                    return TestStatus.entries.find { it.name.equals(str, ignoreCase = true) } ?: TestStatus.UNKNOWN
                }
            })

            octaviusConn.typeRegistry.registerCompositeType<TestUserData>(
                name = "test_user_data",
                resultConverter = object : ResultConverter<TestUserData> {
                    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
                        return expectedType.classifier == TestUserData::class || sourceType.name == "test_user_data"
                    }
                    override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType): TestUserData {
                        require(source is PgComposite)
                        val code = source.get<String>("code")
                        val statusRaw = source.get<Any?>("status")
                        val statusType = source.typeRegistry.types[source.type.attributes["status"]]!!
                        val status = if (statusRaw != null) {
                            context.convert(statusRaw, typeOf<TestStatus>(), statusType)
                        } else {
                            TestStatus.UNKNOWN
                        }
                        return TestUserData(code, status)
                    }
                }
            )

            // Utworzenie typów w bazie
            octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS test_root_composite CASCADE")
            octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS test_user_data CASCADE")
            octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS test_status_enum CASCADE")

            octaviusConn.queryExecutor.execute("CREATE TYPE test_status_enum AS ENUM ('ACTIVE', 'INACTIVE', 'UNKNOWN')")
            octaviusConn.queryExecutor.execute("CREATE TYPE test_user_data AS (code text, status test_status_enum)")
            octaviusConn.queryExecutor.execute("CREATE TYPE test_root_composite AS (main_status test_status_enum, user_data test_user_data)")

            // Odświeżenie rejestru typów, aby OID wczytały się do pamięci
            octaviusConn.reloadTypes()

            // Zbudowanie zapytania, w którym tworzymy nasz kompozyt testowy
            val result = octaviusConn.queryExecutor.query(
                "SELECT ROW('ACTIVE'::test_status_enum, ROW('CD123', 'INACTIVE')::test_user_data)::test_root_composite AS my_map"
            ).first()

            // Odbieramy kolumnę 'my_map' jako Map<String, Any?>
            val mappedResult = result.get<Map<String, Any?>>("my_map")

            assertNotNull(mappedResult)
            assertEquals(2, mappedResult.size)

            val mainStatusVal = mappedResult["main_status"]
            assertTrue(mainStatusVal is TestStatus)
            assertEquals(TestStatus.ACTIVE, mainStatusVal)

            val userDataVal = mappedResult["user_data"]
            assertTrue(userDataVal is TestUserData)
            val userData = userDataVal as TestUserData
            assertEquals("CD123", userData.code)
            assertEquals(TestStatus.INACTIVE, userData.status)

        } finally {
            try {
                octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS test_root_composite CASCADE")
                octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS test_user_data CASCADE")
                octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS test_status_enum CASCADE")
            } catch (e: Exception) {}
            octaviusConn.close()
        }
    }
}
