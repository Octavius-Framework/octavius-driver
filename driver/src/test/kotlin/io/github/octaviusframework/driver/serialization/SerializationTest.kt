package io.github.octaviusframework.driver.serialization

import io.github.octaviusframework.driver.codec.PgByteWriter
import io.github.octaviusframework.driver.codec.dynamic.ContainerCodec
import io.github.octaviusframework.driver.container.ArrayDimension
import io.github.octaviusframework.driver.converter.result.mapper.ResultMapper
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.query.ParameterSerializer
import io.github.octaviusframework.driver.query.get
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.container.PgArray
import io.github.octaviusframework.driver.container.PgComposite
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

class SerializationTest {


    @Test
    fun testArraySerialization() {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val row = session.createNativeQuery("SELECT ARRAY[1, 2, 3, 4, 5]::int[] as my_arr").fetchOne()

        val array = row.get<PgArray>("my_arr")
        assertNotNull(array)

        // Serializacja zerocopy
        val writer1 = PgByteWriter()
        ContainerCodec.serializeContainer(array, writer1, row.typeRegistry)
        val originalBytes = writer1.toByteArray()

        // Modyfikacja warstwy 3 przez operator
        array[1] = 999

        val writer2 = PgByteWriter()
        ContainerCodec.serializeContainer(array, writer2, row.typeRegistry)

        val expectedRow = session.createNativeQuery("SELECT ARRAY[1, 999, 3, 4, 5]::int[] as my_arr").fetchOne()
        val expectedArray = expectedRow.get<PgArray>(0)
        val writer3 = PgByteWriter()
        ContainerCodec.serializeContainer(expectedArray, writer3, row.typeRegistry)
        val expectedBytes = writer3.toByteArray()

        assertContentEquals(expectedBytes, writer2.toByteArray())
    }

    @Test
    fun testFactoryAndSerializationRoundtrip() {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        session.createNativeQuery("DROP TYPE IF EXISTS ser_test_composite CASCADE").execute()
        session.createNativeQuery("CREATE TYPE ser_test_composite AS (id int, name text)").execute()
        session.reloadTypes()

        val dummyRow = session.createNativeQuery("SELECT 1").fetchOne()
        val typeRegistry = dummyRow.typeRegistry

        // 1. Zbudowanie kompozytu fabryką od zera
        val composite = session.types.createComposite("ser_test_composite")
        composite["id"] = 777
        composite["name"] = "factory_test"

        val writer1 = PgByteWriter()
        ContainerCodec.serializeContainer(composite, writer1, typeRegistry)
        val builtCompositeBytes = writer1.toByteArray()

        // Porównanie z bazą
        val expectedCompositeRow =
            session.createNativeQuery("SELECT ROW(777, 'factory_test')::ser_test_composite as my_comp").fetchOne()
        val expectedComposite = expectedCompositeRow.get<PgComposite>(0)
        val writerComp = PgByteWriter()
        ContainerCodec.serializeContainer(expectedComposite, writerComp, typeRegistry)

        assertContentEquals(
            writerComp.toByteArray(),
            builtCompositeBytes,
            "Zbudowany kompozyt musi zgadzać się z Postgresowym"
        )

        // 2. Zbudowanie tablicy ręcznie od zera
        val array = PgArray(
            arrayOid = 1007,
            elementOid = 23,
            dimensions = listOf(ArrayDimension(3, 1)),
            elements = mutableListOf(10, 20, 30),
            typeRegistry = typeRegistry
        )

        val writer2 = PgByteWriter()
        ContainerCodec.serializeContainer(array, writer2, typeRegistry)
        val builtArrayBytes = writer2.toByteArray()

        val expectedArrayRow = session.createNativeQuery("SELECT ARRAY[10, 20, 30]::int[]").fetchOne()
        val expectedArray = expectedArrayRow.get<PgArray>(0)
        val writerArr = PgByteWriter()
        ContainerCodec.serializeContainer(expectedArray, writerArr, typeRegistry)

        assertContentEquals(
            writerArr.toByteArray(),
            builtArrayBytes,
            "Zbudowana tablica musi zgadzać się z Postgresową"
        )
    }

    @Test
    fun testQueryWithParameters() {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val array = listOf(10, 20, 30)

        val rows = session.createNativeQuery("SELECT $1::int[] as test_col").fetchAll(array)

        val returnedArray = rows.first().get<PgArray>("test_col")
        assertNotNull(returnedArray)
        assertEquals(10, returnedArray.get<Int>(0))
        assertEquals(20, returnedArray.get<Int>(1))
        assertEquals(30, returnedArray.get<Int>(2))
    }

    @Test
    fun testMultidimensionalArray() {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val dummyRow = session.createNativeQuery("SELECT 1").fetchOne()

        // Tablica 2x3 (2 wiersze, 3 kolumny)
        val multiArray = PgArray(
            arrayOid = 1007,
            elementOid = 23,
            dimensions = listOf(
                ArrayDimension(2, 1),
                ArrayDimension(3, 1)
            ),
            elements = mutableListOf(1, 2, 3, 4, 5, 6),
            typeRegistry = dummyRow.typeRegistry
        )

        val writer = PgByteWriter()
        ContainerCodec.serializeContainer(multiArray, writer, dummyRow.typeRegistry)
        val serializedArray = writer.toByteArray()

        val rows = session.createNativeQuery(
            "SELECT ARRAY[[1, 2, 3], [4, 5, 6]]::int[] as test_col"
        ).fetchAll()

        val expectedArray = rows.first().get<PgArray>(0)
        val writerArr = PgByteWriter()
        ContainerCodec.serializeContainer(expectedArray, writerArr, dummyRow.typeRegistry)

        assertContentEquals(
            writerArr.toByteArray(),
            serializedArray,
            "Zbudowana tablica wielowymiarowa musi zgadzać się z Postgresową"
        )
    }

    @Test
    fun testParameterSerializerDatabaseRoundTrip() {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        // 1. Integer Round Trip
        val intVal = 424242
        val rowsInt = session.createNativeQuery("SELECT $1 as res").fetchAll(intVal)
        assertEquals(intVal, rowsInt.first().get<Int>("res"))

        // 2. String Round Trip
        val strVal = "Zażółć gęślą jaźń"
        val rowsStr = session.createNativeQuery("SELECT $1 as res").fetchAll(strVal)
        assertEquals(strVal, rowsStr.first().get<String>("res"))

        // 3. Boolean Round Trip
        val boolVal = true
        val rowsBool = session.createNativeQuery("SELECT $1 as res").fetchAll(boolVal)
        assertEquals(boolVal, rowsBool.first().get<Boolean>("res"))

        // 4. Double Round Trip
        val doubleVal = 3.14159
        val rowsDouble = session.createNativeQuery("SELECT $1 as res").fetchAll(doubleVal)
        assertEquals(doubleVal, rowsDouble.first().get<Double>("res"))

        val arrayVal = listOf(10, 20, 30)

        val rowsArray = session.createNativeQuery("SELECT $1 as res").fetchAll(arrayVal)
        val returnedArray = rowsArray.first().get<PgArray>("res")
        assertNotNull(returnedArray)
        assertEquals(10, returnedArray.get<Int>(0))
        assertEquals(20, returnedArray.get<Int>(1))
        assertEquals(30, returnedArray.get<Int>(2))
    }
    @Test
    fun testRecordMapSerialization() {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        // 6. Record Map Serialization
        val recordMap = mapOf(
            "str_key" to "hello",
            "int_key" to 12345
        )

        val exception = assertThrows<OctaviusTypeException> {
            session.createNativeQuery("SELECT $1 as res").fetchAll(recordMap)
        }
        
        assertEquals(TypeExceptionMessage.MISSING_CODEC, exception.messageEnum)
    }


    @Test
    fun testUnknownTypeSerialization() {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val stringVal = "some literal value"
        val res = session.createNativeQuery("SELECT '$stringVal' as res").fetchField<String>()

        assertEquals(stringVal, res)
    }
}

