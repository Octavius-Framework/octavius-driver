package io.github.octaviusframework.driver.codec

import io.github.octaviusframework.driver.io.ByteArrayWindow
import kotlin.reflect.KClass

interface TypeCodec<T : Any> {
    val pgTypeName: String
    val pgSchema: String get() = "pg_catalog"
    val oid: UInt? get() = null
    val kotlinClass: KClass<T>
    val isDefaultForKotlinType: Boolean get() = false

    val fromBinary: (ByteArrayWindow) -> T
    val toBinary: (T) -> ByteArray
}
