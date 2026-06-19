package io.github.octaviusframework.driver.deserialization

import io.github.octaviusframework.driver.mapping.result.ResultConverter
import io.github.octaviusframework.driver.mapping.result.DeserializationContext
import io.github.octaviusframework.driver.mapping.result.ResultConverterRegistry
import io.github.octaviusframework.driver.mapping.result.ResultMapper
import io.github.octaviusframework.driver.mapping.result.array.CollectionArrayConverter
import io.github.octaviusframework.driver.mapping.result.composite.MapCompositeConverter
import io.github.octaviusframework.driver.mapping.result.composite.ReflectionCompositeConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeRegistry
import io.github.octaviusframework.driver.type.containter.ArrayDimension
import io.github.octaviusframework.driver.type.containter.ContainerField
import io.github.octaviusframework.driver.type.containter.PgArray
import io.github.octaviusframework.driver.type.containter.PgComposite
import io.github.octaviusframework.driver.type.containter.PgContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KType
import kotlin.reflect.typeOf


class DeserializationTest {

    private val dummyRegistry = TypeRegistry().apply {
        types = mapOf(
            1u to PgType.Base(1u, "dummy", "public"),
            2u to PgType.Array(2u, "dummy_array", "public", 1u)
        )
        registerCompositeType<Address>("address")
        registerCompositeType<Person>("person")
        registerCompositeType<Company>("company")
        registerCompositeType<OptionalFields>("optional_fields")
    }

    private fun createComposite(attributes: Map<String, Any?>): PgComposite {
        val type = PgType.Composite(1u, "dummy", "public", LinkedHashMap(attributes.keys.associateWith { 1u }))
        val fields = attributes.values.map {
            if (it is PgContainer) {
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
            windows = null,
            containers = if (elements.isNotEmpty() && elements[0] is PgContainer) elements.map { it as? PgContainer }
                .toMutableList() else null,
            values = if (elements.isEmpty() || elements[0] !is PgContainer) elements.toMutableList() else null,
            typeRegistry = dummyRegistry
        )
    }

    data class Address(val street: String, val city: String)
    data class Person(val name: String, val age: Int, val address: Address)
    data class Company(val name: String, val employees: List<Person>)
    data class OptionalFields(val id: Int, val name: String = "Unknown", val desc: String?)

    @Test
    fun `test simple composite reflection mapping`() {
        val registry = ResultConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        val deserializer = ResultMapper(registry)

        val composite = createComposite(mapOf("street" to "Baker St", "city" to "London"))

        val address: Address? = deserializer.deserialize(composite, typeOf<Address>(), composite.type)
        assertNotNull(address)
        assertEquals("Baker St", address?.street)
        assertEquals("London", address?.city)
    }

    @Test
    fun `test nested composite reflection mapping`() {
        val registry = ResultConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        val deserializer = ResultMapper(registry)

        val addressComposite = createComposite(mapOf("street" to "Wall St", "city" to "NY"))
        val personComposite = createComposite(mapOf("name" to "John", "age" to 30, "address" to addressComposite))

        val person: Person? = deserializer.deserialize(personComposite, typeOf<Person>(), personComposite.type)
        assertNotNull(person)
        assertEquals("John", person?.name)
        assertEquals(30, person?.age)
        assertEquals("Wall St", person?.address?.street)
        assertEquals("NY", person?.address?.city)
    }

    @Test
    fun `test array to list mapping with nested composites`() {
        val registry = ResultConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        registry.addConverter(CollectionArrayConverter())
        val deserializer = ResultMapper(registry)

        val p1 = createComposite(
            mapOf(
                "name" to "A",
                "age" to 20,
                "address" to createComposite(mapOf("street" to "S1", "city" to "C1"))
            )
        )
        val p2 = createComposite(
            mapOf(
                "name" to "B",
                "age" to 25,
                "address" to createComposite(mapOf("street" to "S2", "city" to "C2"))
            )
        )

        val array = createArray(listOf(p1, p2))
        val companyComposite = createComposite(mapOf("name" to "Corp", "employees" to array))

        val company: Company? = deserializer.deserialize(companyComposite, typeOf<Company>(), companyComposite.type)
        assertNotNull(company)
        assertEquals("Corp", company?.name)
        assertEquals(2, company?.employees?.size)
        assertEquals("A", company?.employees?.get(0)?.name)
        assertEquals("S2", company?.employees?.get(1)?.address?.street)
    }

    @Test
    fun `test fallback to PgComposite for composite when Any is requested and explicit map conversion`() {
        val registry = ResultConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        registry.addConverter(MapCompositeConverter())
        val deserializer = ResultMapper(registry)

        val composite = createComposite(mapOf("key1" to "value1", "key2" to 42))

        val resultAny: Any? = deserializer.deserialize(composite, typeOf<Any>(), composite.type)
        assertTrue(resultAny is PgComposite)

        val resultMap: Map<String, Any?>? = deserializer.deserialize(composite, typeOf<Map<String, Any?>>(), composite.type)
        assertNotNull(resultMap)
        assertEquals("value1", resultMap!!["key1"])
        assertEquals(42, resultMap["key2"])
    }

    @Test
    fun `test missing non-nullable field throws exception`() {
        val registry = ResultConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        val deserializer = ResultMapper(registry)

        val composite = createComposite(mapOf("street" to "Baker St")) // Missing 'city'

        val ex = assertThrows(IllegalArgumentException::class.java) {
            deserializer.deserialize<Address>(composite, typeOf<Address>(), composite.type)
        }
        assertTrue(ex.message!!.contains("Missing non-nullable attribute"))
    }

    @Test
    fun `test optional parameters and nulls are handled correctly`() {
        val registry = ResultConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        val deserializer = ResultMapper(registry)

        val composite = createComposite(mapOf("id" to 10)) // missing name (has default), missing desc (nullable)

        val result: OptionalFields? = deserializer.deserialize(composite, typeOf<OptionalFields>(), composite.type)
        assertNotNull(result)
        assertEquals(10, result?.id)
        assertEquals("Unknown", result?.name)
        assertNull(result?.desc)
    }

    @Test
    fun `test local registry overrides global registry`() {
        val globalRegistry = ResultConverterRegistry()
        globalRegistry.addConverter(ReflectionCompositeConverter())
        val deserializer = ResultMapper(globalRegistry)

        val composite = createComposite(mapOf("street" to "Global", "city" to "City"))

        // Define a custom converter for Address
        val localConverter = object : ResultConverter<Address> {
            override fun canConvert(source: Any, expectedType: KType, sourceType: PgType) =
                expectedType.classifier == Address::class

            override fun convert(
                source: Any,
                expectedType: KType,
                context: DeserializationContext,
                sourceType: PgType
            ): Address {
                return Address("LocalOverride", "LocalCity")
            }
        }

        val localRegistry = ResultConverterRegistry(globalRegistry)
        localRegistry.addConverter(localConverter)

        val localDeserializer = ResultMapper(localRegistry)

        // Using local registry
        val address: Address? = localDeserializer.deserialize(composite, typeOf<Address>(), composite.type)
        assertEquals("LocalOverride", address?.street)

        // Using global only
        val globalAddress: Address? = deserializer.deserialize(composite, typeOf<Address>(), composite.type)
        assertEquals("Global", globalAddress?.street)
    }
}
