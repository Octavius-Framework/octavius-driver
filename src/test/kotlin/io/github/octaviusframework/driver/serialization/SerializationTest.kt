package io.github.octaviusframework.driver.serialization

import io.github.octaviusframework.driver.codec.PgByteWriter
import io.github.octaviusframework.driver.codec.dynamic.ContainerCodec
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.jdbc.getOctaviusConnection
import io.github.octaviusframework.driver.converter.result.mapper.ResultMapper
import io.github.octaviusframework.driver.query.ParameterSerializer
import io.github.octaviusframework.driver.query.get
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.type.container.PgArray
import io.github.octaviusframework.driver.type.container.PgComposite
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

class SerializationTest {

    @Test
    fun testCompositeZeroCopySerialization() {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", props)

        octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS ser_test_composite CASCADE")
        octaviusConn.queryExecutor.execute("CREATE TYPE ser_test_composite AS (id int, name text)")
        octaviusConn.reloadTypes()

        val row = octaviusConn.createNativeQuery("SELECT ROW(12345, 'octavius_test')::ser_test_composite as my_comp").fetchAll()
            .first()

        val composite = row.get<PgComposite>("my_comp")
        assertNotNull(composite)

        val writer1 = PgByteWriter()
        ContainerCodec.serializeContainer(composite, writer1, row.typeRegistry)
        val originalBytesArr = writer1.toByteArray()

        // TERAZ MODYFIKUJEMY WARSTWĘ 3 Z POMOCĄ OPERATORA SET
        composite["id"] = 99999
        composite["name"] = "changed_text"

        // Serializujemy ponownie
        val writer2 = PgByteWriter()
        ContainerCodec.serializeContainer(composite, writer2, row.typeRegistry)
        val modifiedBytes = writer2.toByteArray()

        // Pobieramy wzorzec z bazy dla zmienionych wartości by porównać
        val expectedRow =
            octaviusConn.createNativeQuery("SELECT ROW(99999, 'changed_text')::ser_test_composite as my_comp").fetchAll().first()
        val expectedComposite = expectedRow.get<PgComposite>(0)
        val writer3 = PgByteWriter()
        ContainerCodec.serializeContainer(expectedComposite, writer3, row.typeRegistry)
        val expectedBytes = writer3.toByteArray()

        assertContentEquals(
            expectedBytes,
            modifiedBytes,
            "Serializacja po modyfikacji w 3 warstwie musi odpowiadać nowym danym"
        )
    }

    @Test
    fun testArraySerialization() {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", props)

        val row = octaviusConn.createNativeQuery("SELECT ARRAY[1, 2, 3, 4, 5]::int[] as my_arr").fetchAll().first()

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

        val expectedRow = octaviusConn.createNativeQuery("SELECT ARRAY[1, 999, 3, 4, 5]::int[] as my_arr").fetchAll().first()
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

        val octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", props)

        //octaviusConn.setSearchPath("te\"st.schemy")
        octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS ser_test_composite CASCADE")
        octaviusConn.queryExecutor.execute("CREATE TYPE ser_test_composite AS (id int, name text)")
        octaviusConn.reloadTypes()

        val dummyRow = octaviusConn.createNativeQuery("SELECT 1").fetchAll().first()
        val typeRegistry = dummyRow.typeRegistry

        // 1. Zbudowanie kompozytu fabryką od zera
        val composite = octaviusConn.types.createComposite("ser_test_composite")
        composite["id"] = 777
        composite["name"] = "factory_test"

        val writer1 = PgByteWriter()
        ContainerCodec.serializeContainer(composite, writer1, typeRegistry)
        val builtCompositeBytes = writer1.toByteArray()

        // Porównanie z bazą
        val expectedCompositeRow =
            octaviusConn.createNativeQuery("SELECT ROW(777, 'factory_test')::ser_test_composite as my_comp").fetchAll().first()
        val expectedComposite = expectedCompositeRow.get<PgComposite>(0)
        val writerComp = PgByteWriter()
        ContainerCodec.serializeContainer(expectedComposite, writerComp, typeRegistry)

        assertContentEquals(
            writerComp.toByteArray(),
            builtCompositeBytes,
            "Zbudowany kompozyt musi zgadzać się z Postgresowym"
        )

        // 2. Zbudowanie tablicy fabryką od zera
        val array = octaviusConn.types.createArray(1007u, 3) // 1007u = _int4
        array.setAll(10, 20, 30)

        val writer2 = PgByteWriter()
        ContainerCodec.serializeContainer(array, writer2, typeRegistry)
        val builtArrayBytes = writer2.toByteArray()

        val expectedArrayRow = octaviusConn.createNativeQuery("SELECT ARRAY[10, 20, 30]::int[]").fetchAll().first()
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

        val octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", props)

        val dummyRow = octaviusConn.createNativeQuery("SELECT 1").fetchAll().first()
        val typeRegistry = dummyRow.typeRegistry
        val array = octaviusConn.types.createArray(1007u, 3) // 1007u = _int4
        array.setAll(10, 20, 30)

        val writer = PgByteWriter()
        ContainerCodec.serializeContainer(array, writer, typeRegistry)
        val serializedArray = writer.toByteArray()

        val rows = octaviusConn.queryExecutor.query(
            "SELECT $1::int[] as test_col",
            paramTypes = listOf(0u),
            paramValues = listOf(serializedArray),
            mapper = ResultMapper(octaviusConn.converterRegistry)
        )

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

        val octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", props)

        // Tablica 2x3 (2 wiersze, 3 kolumny)
        val multiArray = octaviusConn.types.createArray(1007u, 2, 3)

        // Wypełniamy danymi:
        // [ [1, 2, 3], [4, 5, 6] ]
        multiArray.setDimension(intArrayOf(0), 1, 2, 3)
        multiArray.setDimension(intArrayOf(1), 4, 5, 6)

        val writer = PgByteWriter()
        val dummyRow = octaviusConn.createNativeQuery("SELECT 1").fetchAll().first()
        ContainerCodec.serializeContainer(multiArray, writer, dummyRow.typeRegistry)
        val serializedArray = writer.toByteArray()

        val rows = octaviusConn.createNativeQuery(
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

        val octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", props)

        val dummyRow = octaviusConn.createNativeQuery("SELECT 1").fetchAll().first()
        val typeRegistry = dummyRow.typeRegistry
        val typeManager = TypeManager(typeRegistry)
        val parameterMapper = io.github.octaviusframework.driver.converter.parameter.mapper.ParameterMapper(typeRegistry.parameterConverterRegistry, typeManager)
        val serializer = ParameterSerializer(typeManager, parameterMapper)

        // 1. Integer Round Trip
        val intVal = 424242
        val intParam = serializer.serializeWithOid(intVal)
        val rowsInt = octaviusConn.queryExecutor.query(
            "SELECT $1 as res",
            paramTypes = listOf(intParam.oid),
            paramValues = listOf(intParam.value),
            mapper = ResultMapper(octaviusConn.converterRegistry)
        )
        assertEquals(intVal, rowsInt.first().get<Int>("res"))

        // 2. String Round Trip
        val strVal = "Zażółć gęślą jaźń"
        val strParam = serializer.serializeWithOid(strVal)
        val rowsStr = octaviusConn.queryExecutor.query(
            "SELECT $1 as res",
            paramTypes = listOf(strParam.oid),
            paramValues = listOf(strParam.value),
            mapper = ResultMapper(octaviusConn.converterRegistry)
        )
        assertEquals(strVal, rowsStr.first().get<String>("res"))

        // 3. Boolean Round Trip
        val boolVal = true
        val boolParam = serializer.serializeWithOid(boolVal)
        val rowsBool = octaviusConn.queryExecutor.query(
            "SELECT $1 as res",
            paramTypes = listOf(boolParam.oid),
            paramValues = listOf(boolParam.value),
            mapper = ResultMapper(octaviusConn.converterRegistry)
        )
        assertEquals(boolVal, rowsBool.first().get<Boolean>("res"))

        // 4. Double Round Trip
        val doubleVal = 3.14159
        val doubleParam = serializer.serializeWithOid(doubleVal)
        val rowsDouble = octaviusConn.queryExecutor.query(
            "SELECT $1 as res",
            paramTypes = listOf(doubleParam.oid),
            paramValues = listOf(doubleParam.value),
            mapper = ResultMapper(octaviusConn.converterRegistry)
        )
        assertEquals(doubleVal, rowsDouble.first().get<Double>("res"))

        // 5. Container (PgArray) Round Trip
        val arrayVal = octaviusConn.types.createArray(1007u, 3) // 23 = int4
        arrayVal.setAll(10, 20, 30)

        val arrayParam = serializer.serializeWithOid(arrayVal)
        val rowsArray = octaviusConn.queryExecutor.query(
            "SELECT $1 as res",
            paramTypes = listOf(arrayParam.oid),
            paramValues = listOf(arrayParam.value),
            mapper = ResultMapper(octaviusConn.converterRegistry)
        )
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

        val octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", props)

        // 6. Record Map Serialization
        val dummyRow = octaviusConn.createNativeQuery("SELECT 1").fetchAll().first()
        val typeRegistry = dummyRow.typeRegistry
        val typeManager = TypeManager(typeRegistry)
        val parameterMapper = io.github.octaviusframework.driver.converter.parameter.mapper.ParameterMapper(typeRegistry.parameterConverterRegistry, typeManager)
        val serializer = ParameterSerializer(typeManager, parameterMapper)

        val recordMap = mapOf(
            "str_key" to "hello",
            "int_key" to 12345
        )

        val exception = assertThrows<OctaviusTypeException> {
            val recordParam = serializer.serializeWithOid(recordMap)

            octaviusConn.queryExecutor.query(
                "SELECT $1 as res",
                paramTypes = listOf(recordParam.oid),
                paramValues = listOf(recordParam.value),
                mapper = ResultMapper(octaviusConn.converterRegistry)
            )
        }
        
        assertEquals(TypeExceptionMessage.MISSING_CODEC, exception.messageEnum)
    }


    @Test
    fun testUnknownTypeSerialization() {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val octaviusConn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", props)

        val stringVal = "some literal value"
        val rows = octaviusConn.queryExecutor.query(
            "SELECT $1 as res",
            paramTypes = listOf(705u),
            paramValues = listOf(stringVal.toByteArray()),
            mapper = ResultMapper(octaviusConn.converterRegistry)
        )

        assertEquals(stringVal, rows.first().get<String>("res"))
    }
}
