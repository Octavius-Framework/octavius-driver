package io.github.octaviusframework.driver.converter.parameter.mapper

interface SerializationContext {
    fun convert(source: Any, expectedOid: Int?): Any?
    fun findConverter(source: Any, expectedOid: Int?): ParameterConverter<Any>?
}

