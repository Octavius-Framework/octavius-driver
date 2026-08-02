package io.github.octaviusframework.driver.converter.parameter.standard

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass

class JsonElementParameterConverter : ParameterConverter<JsonElement> {

    override val supportedClass: KClass<JsonElement> = JsonElement::class

    override fun canConvert(sourceClass: KClass<*>, expectedOid: Int, context: SerializationContext): Boolean {
        return supportedClass.java.isAssignableFrom(sourceClass.java)
    }

    override fun getDefaultOid(context: SerializationContext): Int {
        return context.typeManager.resolveOid("jsonb", "pg_catalog")
    }

    override fun convert(source: JsonElement, expectedOid: Int, context: SerializationContext): Any {
        return source.toString()
    }
}
