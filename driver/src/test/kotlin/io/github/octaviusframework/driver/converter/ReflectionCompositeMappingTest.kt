package io.github.octaviusframework.driver.converter

import io.github.octaviusframework.driver.annotation.PgName
import io.github.octaviusframework.driver.converter.parameter.composite.ReflectionCompositeParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.converter.result.composite.ReflectionCompositeConverter
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverterRegistry
import io.github.octaviusframework.driver.converter.result.mapper.ResultMapper
import io.github.octaviusframework.driver.identifier.CaseConvention
import io.github.octaviusframework.driver.registry.TypeRegistry
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.container.PgComposite
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

class ReflectionCompositeMappingTest {

    data class Person(
        val firstName: String,
        val lastName: String,
        @PgName("age_in_years") val age: Int
    )

    val type = PgType.Composite(
        3, "person_type", "public", LinkedHashMap(
            mapOf(
                "first_name" to 1,
                "last_name" to 1,
                "age_in_years" to 2
            )
        )
    )

    private val dummyRegistry = TypeRegistry().apply {
        updateTypes(mapOf(
            1 to PgType.Base(1, "text", "public"),
            2 to PgType.Base(2, "int4", "public"),
            3 to type
        ))
    }
    
    private val dummyTypeManager = TypeManager(dummyRegistry)

    private fun registerPersonComposite(
        pgConvention: CaseConvention,
        kotlinConvention: CaseConvention
    ): PgType.Composite {
        dummyRegistry.converterRegistry.registerAutoCompositeType<Person>("person_type", "public", pgConvention, kotlinConvention)
        return type
    }

    private fun createComposite(type: PgType.Composite, attributes: Map<String, Any?>): PgComposite {
        val fields = type.attributes.map { (key, _) ->
            attributes[key]
        }.toTypedArray()
        return PgComposite(type, fields, dummyRegistry)
    }

    @Test
    fun `test deserialization with MapKey and conventions`() {
        val type = registerPersonComposite(CaseConvention.SNAKE_CASE_LOWER, CaseConvention.CAMEL_CASE)

        val registry = ResultConverterRegistry()
        registry.addConverter(ReflectionCompositeConverter())
        val deserializer = ResultMapper(registry,
            dummyTypeManager
        )

        val composite = createComposite(type, mapOf(
            "first_name" to "John",
            "last_name" to "Doe",
            "age_in_years" to 30
        ))

        val person: Person? = deserializer.deserialize(composite, typeOf<Person>(), type)
        assertNotNull(person)
        assertEquals("John", person?.firstName)
        assertEquals("Doe", person?.lastName)
        assertEquals(30, person?.age)
    }

    @Test
    fun `test serialization with MapKey and conventions`() {
        val type = registerPersonComposite(CaseConvention.SNAKE_CASE_LOWER, CaseConvention.CAMEL_CASE)
        val converter = ReflectionCompositeParameterConverter()
        val dummyTypeManager = TypeManager(dummyRegistry)
        val context = object : SerializationContext {
            override val typeManager = dummyTypeManager
            override fun convert(source: Any, expectedOid: Int, pathSegment: String?): Any? = source
            override fun findConverter(source: Any, expectedOid: Int): ParameterConverter<Any>? = null
            override fun findConverterByClass(
                sourceClass: KClass<*>,
                expectedOid: Int
            ): ParameterConverter<Any>? = null
        }

        val person = Person("Jane", "Smith", 28)

        
        assertTrue(converter.canConvert(person::class, type.oid, context))

        val serialized = converter.convert(person, type.oid, context) as PgComposite

        assertNotNull(serialized)
        assertEquals(type, serialized.type)
        assertEquals("Jane", serialized.get(0))
        assertEquals("Smith", serialized.get(1))
        assertEquals(28, serialized.get(2))
    }
}

