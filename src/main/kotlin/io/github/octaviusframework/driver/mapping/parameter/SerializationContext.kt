package io.github.octaviusframework.driver.mapping.parameter

interface SerializationContext {
    fun convert(source: Any, expectedOid: UInt?): Any?
}
