package io.github.octaviusframework

import io.github.octaviusframework.container.ContainerSerializers
import io.github.octaviusframework.container.PgComposite
import io.github.octaviusframework.container.PgArray
import io.github.octaviusframework.io.PgByteWriter
import io.github.octaviusframework.io.toByteArray
import io.github.octaviusframework.jdbc.OctaviusConnection
import io.github.octaviusframework.query.get
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.Properties
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

class SerializationTest {

    @Test
    fun testCompositeZeroCopySerialization() {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val connection = DriverManager.getConnection("jdbc:octavius://localhost:5432/postgres", props)
        val octaviusConn = connection.unwrap(OctaviusConnection::class.java)

        octaviusConn.queryExecutor.execute("DROP TYPE IF EXISTS ser_test_composite CASCADE")
        octaviusConn.queryExecutor.execute("CREATE TYPE ser_test_composite AS (id int, name text)")
        octaviusConn.reloadTypes()

        val row = octaviusConn.queryExecutor.query("SELECT ROW(12345, 'octavius_test')::ser_test_composite as my_comp").first()
        
        // Wyciągamy z warstwy pierwszej okno na surowe bajty (aby mieć wzorzec)
        val originalWindow = row.fields[0].rawValue!!
        val originalBytes = originalWindow.toByteArray()
        
        // Wyciągamy jako PgComposite (zbudowany parserem)
        val composite = row.get<PgComposite>("my_comp")
        assertNotNull(composite)
        
        // Serializujemy bez modyfikacji
        val writer1 = PgByteWriter()
        ContainerSerializers.serializeContainer(composite, writer1, row.typeRegistry)
        
        // Musi być identyczne bajt w bajt!
        assertContentEquals(originalBytes, writer1.toByteArray(), "Serializacja nienaruszonego kompozytu musi dać te same bajty")
        
        // TERAZ MODYFIKUJEMY WARSTWĘ 3
        composite.fields[0].value = 99999
        composite.fields[1].value = "changed_text"
        
        // Serializujemy ponownie
        val writer2 = PgByteWriter()
        ContainerSerializers.serializeContainer(composite, writer2, row.typeRegistry)
        val modifiedBytes = writer2.toByteArray()
        
        // Pobieramy wzorzec z bazy dla zmienionych wartości by porównać
        val expectedRow = octaviusConn.queryExecutor.query("SELECT ROW(99999, 'changed_text')::ser_test_composite as my_comp").first()
        val expectedBytes = expectedRow.fields[0].rawValue!!.toByteArray()
        
        assertContentEquals(expectedBytes, modifiedBytes, "Serializacja po modyfikacji w 3 warstwie musi odpowiadać nowym danym")
    }
    
    @Test
    fun testArraySerialization() {
        val props = Properties()
        props.setProperty("user", "postgres")
        props.setProperty("password", "1234")

        val connection = DriverManager.getConnection("jdbc:octavius://localhost:5432/postgres", props)
        val octaviusConn = connection.unwrap(OctaviusConnection::class.java)

        val row = octaviusConn.queryExecutor.query("SELECT ARRAY[1, 2, 3, 4, 5]::int[] as my_arr").first()
        
        val originalWindow = row.fields[0].rawValue!!
        val originalBytes = originalWindow.toByteArray()
        
        val array = row.get<PgArray>("my_arr")
        assertNotNull(array)
        
        // Serializacja zerocopy
        val writer1 = PgByteWriter()
        ContainerSerializers.serializeContainer(array, writer1, row.typeRegistry)
        assertContentEquals(originalBytes, writer1.toByteArray())
        
        // Modyfikacja warstwy 3 (np. podmieniamy drugi element)
        if (array.values == null) {
            array.values = MutableList(array.totalElements) { null }
        }
        array.values!![1] = 999
        
        val writer2 = PgByteWriter()
        ContainerSerializers.serializeContainer(array, writer2, row.typeRegistry)
        
        val expectedRow = octaviusConn.queryExecutor.query("SELECT ARRAY[1, 999, 3, 4, 5]::int[] as my_arr").first()
        val expectedBytes = expectedRow.fields[0].rawValue!!.toByteArray()
        
        assertContentEquals(expectedBytes, writer2.toByteArray())
    }
}
