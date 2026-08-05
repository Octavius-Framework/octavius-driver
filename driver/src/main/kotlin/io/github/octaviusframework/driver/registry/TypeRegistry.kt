package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.type.PgType
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TypeRegistry {
    val lock = ReentrantLock()

    @Volatile
    internal var isLoaded: Boolean = false

    val converterRegistry = ConverterRegistry()

    @Volatile
    var dictionary: TypeDictionary = TypeDictionary.EMPTY

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
