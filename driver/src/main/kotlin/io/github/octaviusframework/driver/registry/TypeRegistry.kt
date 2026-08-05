package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.type.PgType
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Registry holding the mapping of database types, data codecs, and type converters.
 *
 * `TypeRegistry` manages the state for mapping PostgreSQL Object Identifiers (OIDs) to
 * their corresponding type definitions ([PgType]), as well as providing resolution
 * mechanisms for parameter encoders, result decoders, and data serialization.
 */
class TypeRegistry {
    /**
     * A lock used to ensure thread-safe updates to the registry's internal state.
     */
    val lock = ReentrantLock()

    @Volatile
    internal var isLoaded: Boolean = false

    /**
     * The registry handling custom mappings and converters between Kotlin objects and PostgreSQL types.
     */
    val converterRegistry = ConverterRegistry()

    /**
     * An immutable snapshot dictionary representing the current mapping of PostgreSQL OIDs and types.
     */
    @Volatile
    var dictionary: TypeDictionary = TypeDictionary.EMPTY

    /**
     * An immutable snapshot dictionary for database binary/text codecs.
     */
    @Volatile
    var codecs: CodecDictionary = CodecDictionary.createWithBuiltins()

    /**
     * Registers a custom codec. If OID and schema are missing (dynamic type in multi-schema),
     * it will be mapped globally for deserialization but resolved in-flight for serialization.
     */
    fun registerCodec(codec: TypeCodec<*>) = lock.withLock {
        this.codecs = this.codecs.withRegisteredCodec(codec, this.dictionary)
    }

    /**
     * Replaces the entire type map with a new instance, ensuring thread-safety.
     * Additionally applies custom codecs waiting for an OID.
     */
    fun updateTypes(newTypes: Map<Int, PgType>) {
        dictionary = TypeDictionary.build(newTypes)
        codecs = codecs.buildUpdated(newTypes, this)
    }
}
