package io.github.octaviusframework.driver.converter.parameter.standard

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.identifier.QualifiedName
import io.github.octaviusframework.driver.type.UNRESOLVED_OID
import io.github.octaviusframework.driver.type.TypeManager
import kotlinx.serialization.json.JsonElement

import kotlin.reflect.KClass

class JsonElementParameterConverter : ParameterConverter<JsonElement> {
    override val supportedClass: KClass<JsonElement> = JsonElement::class

    override fun canConvert(sourceClass: KClass<*>, expectedOid: Int, typeManager: TypeManager): Boolean {
        return supportedClass.java.isAssignableFrom(sourceClass.java)
    }

    override fun getDefaultOid(typeManager: TypeManager): Int {
        return typeManager.resolveOid("jsonb", "pg_catalog")
    }

    override fun convert(source: JsonElement, expectedOid: Int, context: SerializationContext, typeManager: TypeManager): Any {
        return source.toString()
    }
}
