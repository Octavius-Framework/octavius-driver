package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.type.TypeManager

interface ParameterConverter<T : Any> {
    fun canConvert(source: Any, expectedOid: UInt?, typeManager: TypeManager): Boolean
    fun convert(source: Any, expectedOid: UInt?, context: SerializationContext, typeManager: TypeManager): Any?
}
