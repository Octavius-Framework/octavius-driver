package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.converter.result.array.CollectionArrayConverter
import io.github.octaviusframework.driver.converter.result.composite.ReflectionCompositeConverter
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverterRegistry
import io.github.octaviusframework.driver.converter.result.mapper.ResultMapper
import io.github.octaviusframework.driver.registry.TypeRegistry
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.container.ArrayDimension
import io.github.octaviusframework.driver.container.PgArray
import io.github.octaviusframework.driver.container.PgComposite
import io.github.octaviusframework.driver.type.TypeManager
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.typeOf
import kotlin.test.assertFailsWith

class MappingExceptionTest {

    private val dummyRegistry = TypeRegistry().apply {
        updateTypes(mapOf(
            1 to PgType.Base(1, "dummy", "public"),
            2 to PgType.Array(2, "dummy_array", "public", 1)
        ))
        converterRegistry.registerAutoCompositeType<Address>("address")
        converterRegistry.registerAutoCompositeType<Person>("person")
        converterRegistry.registerAutoCompositeType<Company>("company")
    }

    private fun createComposite(attributes: Map<String, Any?>): PgComposite {
        val type = PgType.Composite(1, "dummy", "public", LinkedHashMap(attributes.keys.associateWith { 1 }))
        val fields = attributes.values.toTypedArray()
        return PgComposite(type, fields, dummyRegistry)
    }

    private fun createArray(elements: List<Any?>): PgArray {
        return PgArray(
            arrayOid = 2,
            elementOid = 1,
            dimensions = listOf(ArrayDimension(elements.size, 1)),
            elements = elements.toMutableList()
        )
    }

    data class Address(val street: String, val city: String)
    data class Person(val name: String, val age: Int, val address: Address)
    data class Company(val name: String, val employees: List<Person>)

    @Test
    fun `test nested composite mapping exception path for missing attribute`() {
        val registry = ResultConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        registry.addConverter(CollectionArrayConverter())
        val deserializer = ResultMapper(registry, TypeManager(dummyRegistry))

        // create valid person
        val p1 = createComposite(
            mapOf(
                "name" to "A",
                "age" to 20,
                "address" to createComposite(mapOf("street" to "S1", "city" to "C1"))
            )
        )
        // create invalid person (address is missing city)
        val p2 = createComposite(
            mapOf(
                "name" to "B",
                "age" to 25,
                "address" to createComposite(mapOf("street" to "S2")) // missing 'city'
            )
        )

        val array = createArray(listOf(p1, p2))
        val companyComposite = createComposite(mapOf("name" to "Corp", "employees" to array))

        val ex = assertFailsWith<MappingException> {
            deserializer.deserialize<Company>(companyComposite, typeOf<Company>(), companyComposite.type)
        }

        val details = ex.getDetailedMessage()
        assertTrue(details.contains("Path: employees -> [1] -> address -> city"), "Expected path missing, got: $details")
        assertTrue(details.contains("Missing non-nullable attribute 'city' in composite"), "Expected missing attribute message, got: $details")
    }

    @Test
    fun `test nested composite mapping exception path for null in non-nullable property`() {
        val registry = ResultConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        registry.addConverter(CollectionArrayConverter())
        val deserializer = ResultMapper(registry, TypeManager(dummyRegistry))

        // create invalid person (name is null but expected String)
        val p1 = createComposite(
            mapOf(
                "name" to null,
                "age" to 20,
                "address" to createComposite(mapOf("street" to "S1", "city" to "C1"))
            )
        )

        val array = createArray(listOf(p1))
        val companyComposite = createComposite(mapOf("name" to "Corp", "employees" to array))

        val ex = assertFailsWith<MappingException> {
            deserializer.deserialize<Company>(companyComposite, typeOf<Company>(), companyComposite.type)
        }

        val details = ex.getDetailedMessage()
        assertTrue(details.contains("Path: employees -> [0] -> name"), "Expected path missing, got: $details")
        assertTrue(details.contains("Null value for non-nullable attribute 'name'"), "Expected null property message, got: $details")
    }

    @Test
    fun `test nested array mapping exception path for null in non-nullable array element`() {
        val registry = ResultConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        registry.addConverter(CollectionArrayConverter())
        val deserializer = ResultMapper(registry, TypeManager(dummyRegistry))

        // employees is List<Person> (non-nullable elements)
        // we put null as one of the elements
        val array = createArray(listOf(null))
        val companyComposite = createComposite(mapOf("name" to "Corp", "employees" to array))

        val ex = assertFailsWith<MappingException> {
            deserializer.deserialize<Company>(companyComposite, typeOf<Company>(), companyComposite.type)
        }

        val details = ex.getDetailedMessage()
        assertTrue(details.contains("Path: employees -> [0]"), "Expected path missing, got: $details")
        assertTrue(details.contains("Null array element for non-nullable type"), "Expected null element message, got: $details")
    }
}
