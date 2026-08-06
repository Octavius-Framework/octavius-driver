package io.github.octaviusframework.driver.converter.parameter.standard

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.identifier.QualifiedName
import kotlinx.serialization.json.JsonElement
import kotlin.reflect.KClass

class JsonElementParameterConverter : ParameterConverter<JsonElement> {

    override val supportedClass: KClass<JsonElement> = JsonElement::class

    override fun canConvert(sourceClass: KClass<*>, expectedOid: Int, context: SerializationContext): Boolean {
        return supportedClass.java.isAssignableFrom(sourceClass.java)
    }

    override fun getDefaultTypeName(context: SerializationContext): QualifiedName {
        return QualifiedName("pg_catalog", "jsonb")
    }

    override fun convert(source: JsonElement, expectedOid: Int, context: SerializationContext): Any {
        return source.toString()
    }
}
