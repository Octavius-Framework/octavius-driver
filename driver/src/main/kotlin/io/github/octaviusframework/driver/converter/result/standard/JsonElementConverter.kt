package io.github.octaviusframework.driver.converter.result.standard

import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass
import kotlin.reflect.KType

class JsonElementConverter : ResultConverter<String, JsonElement> {
    override val supportedSourceClass = String::class

    override fun canConvert(source: String, expectedType: KType, sourceType: PgType): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        if (kClass == JsonElement::class) return true
        if (kClass == Any::class && (sourceType.name == "json" || sourceType.name == "jsonb")) return true
        return false
    }

    override fun convert(
        source: String,
        expectedType: KType,
        context: DeserializationContext,
        sourceType: PgType
    ): JsonElement {
        return Json.parseToJsonElement(source)
    }
}