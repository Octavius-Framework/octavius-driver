package io.github.octaviusframework.driver.codec

import kotlin.reflect.KClass

interface TypeCodec<T : Any> {
    val pgTypeName: String
    val pgSchema: String get() = "pg_catalog"
    val oid: Int? get() = null
    val kotlinClass: KClass<T>
    val isDefaultForKotlinType: Boolean get() = false

    val fromBinary: (ByteArray, Int, Int) -> T
    val toBinary: (T, PgByteWriter) -> Unit
}

