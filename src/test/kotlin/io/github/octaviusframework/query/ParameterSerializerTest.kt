package io.github.octaviusframework.query

import io.github.octaviusframework.io.ByteArrayWindow
import io.github.octaviusframework.types.TypeRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParameterSerializerTest {

    @Test
    fun testBasicRoundTrip() {
        val registry = TypeRegistry() 
        val serializer = ParameterSerializer(registry)

        // Test for Null
        val nullBytes = serializer.serialize(null)
        assertNull(nullBytes, "Serialization of null should return null")

        // Test for Integer
        val intVal = 12345
        val intBytes = serializer.serialize(intVal)
        assertNotNull(intBytes)
        val intHandler = registry.getSerializerByClass(Int::class)!!
        val parsedInt = intHandler.fromBinary(ByteArrayWindow(intBytes, 0, intBytes.size))
        assertEquals(intVal, parsedInt, "Integer roundtrip should match original value")

        // Test for String
        val stringVal = "test_string_123"
        val stringBytes = serializer.serialize(stringVal)
        assertNotNull(stringBytes)
        val stringHandler = registry.getSerializerByClass(String::class)!!
        val parsedString = stringHandler.fromBinary(ByteArrayWindow(stringBytes, 0, stringBytes.size))
        assertEquals(stringVal, parsedString, "String roundtrip should match original value")

        // Test for Boolean
        val boolVal = true
        val boolBytes = serializer.serialize(boolVal)
        assertNotNull(boolBytes)
        val boolHandler = registry.getSerializerByClass(Boolean::class)!!
        val parsedBool = boolHandler.fromBinary(ByteArrayWindow(boolBytes, 0, boolBytes.size))
        assertEquals(boolVal, parsedBool, "Boolean roundtrip should match original value")

        // Test for Double
        val doubleVal = 3.14159
        val doubleBytes = serializer.serialize(doubleVal)
        assertNotNull(doubleBytes)
        val doubleHandler = registry.getSerializerByClass(Double::class)!!
        val parsedDouble = doubleHandler.fromBinary(ByteArrayWindow(doubleBytes, 0, doubleBytes.size))
        assertEquals(doubleVal, parsedDouble, "Double roundtrip should match original value")
    }

    @Test
    fun testByteArrayRoundTrip() {
        val registry = TypeRegistry()
        val serializer = ParameterSerializer(registry)

        val byteArrayVal = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte())
        val bytes = serializer.serialize(byteArrayVal)
        assertNotNull(bytes)
        val handler = registry.getSerializerByClass(ByteArray::class)!!
        val parsedByteArray = handler.fromBinary(ByteArrayWindow(bytes, 0, bytes.size))
        
        assertEquals(byteArrayVal.toList(), parsedByteArray.toList(), "ByteArray roundtrip should match original value")
    }
}
