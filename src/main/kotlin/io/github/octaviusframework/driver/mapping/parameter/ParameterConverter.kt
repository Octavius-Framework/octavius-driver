package io.github.octaviusframework.driver.mapping.parameter

import io.github.octaviusframework.driver.type.TypeRegistry
interface ParameterConverter<T : Any> {
    fun canConvert(source: Any, expectedOid: UInt?, typeRegistry: TypeRegistry): Boolean
    fun convert(source: Any, expectedOid: UInt?, context: SerializationContext, typeRegistry: TypeRegistry): Any?
}
