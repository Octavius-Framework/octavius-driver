package io.github.octaviusframework.driver.converter.parameter.standard

import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.type.PgTyped
import io.github.octaviusframework.driver.type.QualifiedName
import io.github.octaviusframework.driver.type.TypeManager
import kotlinx.serialization.json.JsonElement

class JsonElementParameterConverter : ParameterConverter<JsonElement> {
    override fun canConvert(source: Any, expectedOid: Int?, typeManager: TypeManager): Boolean {
        return source is JsonElement
    }

    override fun convert(source: Any, expectedOid: Int?, context: SerializationContext, typeManager: TypeManager): Any? {
        val element = source as JsonElement
        val str = element.toString()
        if (expectedOid == null) {
            return PgTyped(str, QualifiedName("pg_catalog", "jsonb", false))
        }
        return str
    }
}
