package io.github.octaviusframework.driver.converter.result.standard

import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KClass
import kotlin.reflect.KType

internal object JsonElementConverter : ResultConverter<String, JsonElement> {

    override val supportedSourceClass = String::class

    override fun canConvert(sourceClass: KClass<*>, expectedType: KType, sourceType: PgType, context: DeserializationContext): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        if (kClass == JsonElement::class || kClass == JsonObject::class || kClass == JsonArray::class || kClass == JsonPrimitive::class) return true
        if (kClass == Any::class && (sourceType.schema == "pg_catalog" && (sourceType.name == "json" || sourceType.name == "jsonb"))) return true
        return false
    }

    override fun convert(
        source: String,
        expectedType: KType,
        sourceType: PgType,
        context: DeserializationContext
    ): JsonElement {
        return Json.parseToJsonElement(source)
    }
}