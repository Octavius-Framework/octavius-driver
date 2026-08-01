package io.github.octaviusframework.driver.converter.parameter.standard

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.identifier.QualifiedName
import io.github.octaviusframework.driver.type.PgTyped
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.type.isKnownOid
import kotlinx.serialization.json.JsonElement

import kotlin.reflect.KClass

class JsonElementParameterConverter : ParameterConverter<JsonElement> {
    override val supportedClass: KClass<JsonElement> = JsonElement::class

    override fun canConvert(sourceClass: KClass<*>, expectedOid: Int, typeManager: TypeManager): Boolean {
        return supportedClass.java.isAssignableFrom(sourceClass.java)
    }

    override fun convert(source: JsonElement, expectedOid: Int, context: SerializationContext, typeManager: TypeManager): Any {
        val str = source.toString()
        if (!expectedOid.isKnownOid) {
            return PgTyped(str, QualifiedName("pg_catalog", "jsonb", false))
        }
        return str
    }
}
