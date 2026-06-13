package io.github.octaviusframework.converter

import io.github.octaviusframework.deserialization.DeserializationContext
import io.github.octaviusframework.deserialization.PgConverter
import io.github.octaviusframework.types.PgType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KType

class JsonElementConverter : PgConverter<JsonElement> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        if (kClass == JsonElement::class) return true
        if (kClass == Any::class && (sourceType.name == "json" || sourceType.name == "jsonb")) return true
        return false
    }

    override fun convert(
        source: Any,
        expectedType: KType,
        context: DeserializationContext,
        sourceType: PgType
    ): JsonElement {
        if (source is String) {
            return Json.parseToJsonElement(source)
        }
        throw IllegalArgumentException("Cannot convert $source to JsonElement")
    }
}