package io.github.octaviusframework.driver.converter

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.jdbc.getOctaviusConnection
import io.github.octaviusframework.driver.query.get
import io.github.octaviusframework.driver.type.PgStandardType
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.container.PgComposite
import io.github.octaviusframework.driver.type.withPgType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class JsonElementIntegrationTest {

    data class MetadataHolder(
        val id: Int,
        val metadata: JsonElement
    )

    class MetadataHolderResultConverter : ResultConverter<PgComposite, MetadataHolder> {
        override val supportedSourceClass = PgComposite::class
        override fun canConvert(source: PgComposite, expectedType: KType, sourceType: PgType): Boolean {
            return expectedType.classifier == MetadataHolder::class
        }

        private val jsonElementType = typeOf<JsonElement>()

        override fun convert(
            source: PgComposite,
            expectedType: KType,
            context: DeserializationContext,
            sourceType: PgType
        ): MetadataHolder {
            val composite = source
            return MetadataHolder(
                id = composite.get("id"),
                metadata = context.convert(
                    composite.get("metadata"),
                    jsonElementType,
                    composite.getAttributeType("metadata")
                )
            )
        }
    }

    class MetadataHolderParameterConverter : ParameterConverter<MetadataHolder> {
        override fun canConvert(source: Any, expectedOid: Int?, typeManager: TypeManager): Boolean {
            return source is MetadataHolder
        }

        override fun convert(
            source: Any,
            expectedOid: Int?,
            context: SerializationContext,
            typeManager: TypeManager
        ): Any {
            val holder = source as MetadataHolder
            val composite = if (expectedOid != null) {
                typeManager.createComposite(expectedOid)
            } else {
                typeManager.createComposite("metadata_holder")
            }
            composite["id"] = holder.id
            composite["metadata"] = context.convert(holder.metadata, composite.getAttributeOid("metadata"))
            return composite
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            val conn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
            try {
                conn.createNativeQuery("DROP TABLE IF EXISTS test_json_elements CASCADE").execute()
                conn.createNativeQuery("CREATE TABLE test_json_elements (id int PRIMARY KEY, data jsonb)").execute()

                conn.createNativeQuery("DROP TYPE IF EXISTS metadata_holder CASCADE").execute()
                conn.createNativeQuery("CREATE TYPE metadata_holder AS (id int, metadata jsonb)").execute()
            } finally {
                conn.close()
            }
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            val conn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
            try {
                conn.createNativeQuery("DROP TABLE IF EXISTS test_json_elements CASCADE").execute()
                conn.createNativeQuery("DROP TYPE IF EXISTS metadata_holder CASCADE").execute()
            } finally {
                conn.close()
            }
        }
    }

    @Test
    fun testJsonElementAsParameterAndResult() {
        val conn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            val inputJson = buildJsonObject {
                put("key", JsonPrimitive("value123"))
                put("number", JsonPrimitive(42))
            }

            conn.createNamedQuery("INSERT INTO test_json_elements (id, data) VALUES (@id, @data)")
                .update(mapOf("id" to 1, "data" to inputJson))

            val row = conn.createNamedQuery("SELECT data FROM test_json_elements WHERE id = @id")
                .fetchOne(mapOf("id" to 1))

            val outputJson = row.get<JsonElement>("data")
            assertTrue(outputJson is JsonObject)
            assertEquals("value123", (outputJson as JsonObject)["key"]?.let { (it as JsonPrimitive).content })
            assertEquals("42", outputJson["number"]?.let { (it as JsonPrimitive).content })
        } finally {
            conn.close()
        }
    }

    @Test
    fun testJsonElementInsideComposite() {
        val conn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            conn.reloadTypes()

            // Rejestrujemy ręczne konwertery dla naszego kompozytu
            conn.types.registerResultConverter(MetadataHolderResultConverter())
            conn.types.registerParameterConverter(MetadataHolderParameterConverter())

            val inputJson = buildJsonObject {
                put("status", JsonPrimitive("active"))
            }
            val holder = MetadataHolder(100, inputJson)

            val row = conn.createNamedQuery("SELECT @holder as res")
                .fetchOne("holder" to holder)

            val outputHolder = row.get<MetadataHolder>("res")
            assertEquals(100, outputHolder.id)
            val outputJson = outputHolder.metadata as JsonObject
            assertEquals("active", (outputJson["status"] as JsonPrimitive).content)
        } finally {
            conn.close()
        }
    }

    @Test
    fun testJsonElementListAsParameter() {
        val conn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            val list = listOf(
                buildJsonObject { put("key1", JsonPrimitive("val1")) },
                buildJsonObject { put("key2", JsonPrimitive("val2")) }
            )

            // Przekazujemy listę bez jawnego typu, powinno zostać wywnioskowane jako jsonb[]
            val row = conn.createNamedQuery("SELECT @list as res")
                .fetchOne("list" to list)

            val outputList = row.get<List<JsonElement>>("res")
            assertEquals(2, outputList.size)
            assertEquals("val1", (outputList[0] as JsonObject)["key1"]?.let { (it as JsonPrimitive).content })
            assertEquals("val2", (outputList[1] as JsonObject)["key2"]?.let { (it as JsonPrimitive).content })
        } finally {
            conn.close()
        }
    }

    @Test
    fun testJsonElementWithExplicitType() {
        val conn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            val inputJson = buildJsonObject {
                put("key", JsonPrimitive("explicit"))
            }

            val row = conn.createNamedQuery("SELECT pg_typeof(@data)::text as type_name, @data as res")
                .fetchOne("data" to inputJson.withPgType(PgStandardType.JSON))

            val typeName = row.get<String>("type_name")
            assertEquals("json", typeName)

            val outputJson = row.get<JsonElement>("res")
            assertEquals("explicit", (outputJson as JsonObject)["key"]?.let { (it as JsonPrimitive).content })
        } finally {
            conn.close()
        }
    }
}

