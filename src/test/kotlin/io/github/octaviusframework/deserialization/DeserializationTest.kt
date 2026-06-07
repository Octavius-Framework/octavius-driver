package io.github.octaviusframework.deserialization

import io.github.octaviusframework.container.ArrayDimension
import io.github.octaviusframework.container.ContainerField
import io.github.octaviusframework.container.PgArray
import io.github.octaviusframework.container.PgComposite
import io.github.octaviusframework.types.PgType
import io.github.octaviusframework.types.TypeRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.reflect.typeOf

class DeserializationTest {

    private val dummyRegistry = TypeRegistry()

    private fun createComposite(attributes: Map<String, Any?>): PgComposite {
        val type = PgType.Composite(1u, "dummy", "public", LinkedHashMap(attributes.keys.associateWith { 1u }))
        val fields = attributes.values.map { 
            if (it is io.github.octaviusframework.container.PgContainer) {
                ContainerField(rawValue = null, container = it, value = null)
            } else {
                ContainerField(rawValue = null, container = null, value = it)
            }
        }
        return PgComposite(type, fields, dummyRegistry)
    }

    private fun createArray(elements: List<Any?>): PgArray {
        return PgArray(
            arrayOid = 2u,
            elementOid = 1u,
            dimensions = listOf(ArrayDimension(elements.size, 1)),
            hasNulls = elements.any { it == null },
            windows = null,
            containers = if (elements.isNotEmpty() && elements[0] is io.github.octaviusframework.container.PgContainer) elements.map { it as? io.github.octaviusframework.container.PgContainer }.toMutableList() else null,
            values = if (elements.isEmpty() || elements[0] !is io.github.octaviusframework.container.PgContainer) elements.toMutableList() else null,
            typeRegistry = dummyRegistry
        )
    }

    data class Address(val street: String, val city: String)
    data class Person(val name: String, val age: Int, val address: Address)
    data class Company(val name: String, val employees: List<Person>)
    data class OptionalFields(val id: Int, val name: String = "Unknown", val desc: String?)

    @Test
    fun `test simple composite reflection mapping`() {
        val registry = ConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        val deserializer = ObjectDeserializer(registry)

        val composite = createComposite(mapOf("street" to "Baker St", "city" to "London"))
        
        val address: Address? = deserializer.deserialize(composite, typeOf<Address>())
        assertNotNull(address)
        assertEquals("Baker St", address?.street)
        assertEquals("London", address?.city)
    }

    @Test
    fun `test nested composite reflection mapping`() {
        val registry = ConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        val deserializer = ObjectDeserializer(registry)

        val addressComposite = createComposite(mapOf("street" to "Wall St", "city" to "NY"))
        val personComposite = createComposite(mapOf("name" to "John", "age" to 30, "address" to addressComposite))

        val person: Person? = deserializer.deserialize(personComposite, typeOf<Person>())
        assertNotNull(person)
        assertEquals("John", person?.name)
        assertEquals(30, person?.age)
        assertEquals("Wall St", person?.address?.street)
        assertEquals("NY", person?.address?.city)
    }

    @Test
    fun `test array to list mapping with nested composites`() {
        val registry = ConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        registry.addConverter(CollectionArrayConverter())
        val deserializer = ObjectDeserializer(registry)

        val p1 = createComposite(mapOf("name" to "A", "age" to 20, "address" to createComposite(mapOf("street" to "S1", "city" to "C1"))))
        val p2 = createComposite(mapOf("name" to "B", "age" to 25, "address" to createComposite(mapOf("street" to "S2", "city" to "C2"))))
        
        val array = createArray(listOf(p1, p2))
        val companyComposite = createComposite(mapOf("name" to "Corp", "employees" to array))

        val company: Company? = deserializer.deserialize(companyComposite, typeOf<Company>())
        assertNotNull(company)
        assertEquals("Corp", company?.name)
        assertEquals(2, company?.employees?.size)
        assertEquals("A", company?.employees?.get(0)?.name)
        assertEquals("S2", company?.employees?.get(1)?.address?.street)
    }

    @Test
    fun `test fallback to map for composite when Any is requested`() {
        val registry = ConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        registry.addConverter(MapCompositeConverter())
        registry.addConverter(AnyConverter())
        val deserializer = ObjectDeserializer(registry)

        val composite = createComposite(mapOf("key1" to "value1", "key2" to 42))
        
        val result: Any? = deserializer.deserialize(composite, typeOf<Any>())
        assertTrue(result is Map<*, *>)
        val map = result as Map<*, *>
        assertEquals("value1", map["key1"])
        assertEquals(42, map["key2"])
    }

    @Test
    fun `test missing non-nullable field throws exception`() {
        val registry = ConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        val deserializer = ObjectDeserializer(registry)

        val composite = createComposite(mapOf("street" to "Baker St")) // Missing 'city'

        val ex = assertThrows(IllegalArgumentException::class.java) {
            deserializer.deserialize<Address>(composite, typeOf<Address>())
        }
        assertTrue(ex.message!!.contains("Missing non-nullable attribute"))
    }

    @Test
    fun `test optional parameters and nulls are handled correctly`() {
        val registry = ConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        val deserializer = ObjectDeserializer(registry)

        val composite = createComposite(mapOf("id" to 10)) // missing name (has default), missing desc (nullable)

        val result: OptionalFields? = deserializer.deserialize(composite, typeOf<OptionalFields>())
        assertNotNull(result)
        assertEquals(10, result?.id)
        assertEquals("Unknown", result?.name)
        assertNull(result?.desc)
    }

    @Test
    fun `test local registry overrides global registry`() {
        val globalRegistry = ConverterRegistry()
        globalRegistry.addConverter(ReflectionCompositeConverter())
        val deserializer = ObjectDeserializer(globalRegistry)

        val composite = createComposite(mapOf("street" to "Global", "city" to "City"))
        
        // Define a custom converter for Address
        val localConverter = object : PgConverter<Address> {
            override fun canConvert(source: Any, expectedType: kotlin.reflect.KType) = 
                expectedType.classifier == Address::class
            
            override fun convert(source: Any, expectedType: kotlin.reflect.KType, context: DeserializationContext): Address {
                return Address("LocalOverride", "LocalCity")
            }
        }

        val localRegistry = ConverterRegistry(globalRegistry)
        localRegistry.addConverter(localConverter)

        // Using local registry
        val address: Address? = deserializer.deserialize(composite, typeOf<Address>(), localRegistry)
        assertEquals("LocalOverride", address?.street)

        // Using global only
        val globalAddress: Address? = deserializer.deserialize(composite, typeOf<Address>())
        assertEquals("Global", globalAddress?.street)
    }
}
