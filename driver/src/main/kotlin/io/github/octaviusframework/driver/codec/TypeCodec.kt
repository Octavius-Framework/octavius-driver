package io.github.octaviusframework.driver.codec

import kotlin.reflect.KClass

/**
 * Interface representing a codec that handles serialization and deserialization
 * between a Kotlin type and a PostgreSQL binary format.
 *
 * @param T The Kotlin type this codec handles.
 */
interface TypeCodec<T : Any> {
    /** PostgreSQL type name. */
    val pgTypeName: String
    /** PostgreSQL schema name */
    val pgSchema: String get() = ""

    /** Optional OID of the PostgreSQL type. */
    val oid: Int? get() = null
    /** The Kotlin class this codec handles. */
    val kotlinClass: KClass<T>
    /** Whether this codec is the default for its Kotlin type. */
    val isDefaultForKotlinType: Boolean get() = false

    /** Function to deserialize a value from a PostgreSQL binary format. */
    val fromBinary: (ByteArray, Int, Int) -> T
    /** Function to serialize a value to a PostgreSQL binary format. */
    val toBinary: (T, PgByteWriter) -> Unit
}

