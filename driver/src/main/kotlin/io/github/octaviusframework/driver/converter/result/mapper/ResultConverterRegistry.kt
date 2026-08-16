package io.github.octaviusframework.driver.converter.result.mapper

import io.github.octaviusframework.driver.type.PgType
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Holds [ResultConverter]s and finds the one that claims a given value.
 *
 * Converters are indexed by their `supportedSourceClass`, and lookup tries the exact class first, then
 * those registered under `Any::class`, then the [parent] registry. Within each group the most recently
 * registered converter is asked first, so a registration overrides an earlier one without removing it.
 *
 * Registration is thread-safe and reads are lock-free: adding a converter replaces the map rather than
 * mutating it, so a lookup already in flight completes against the map it started with.
 *
 * @param parent The registry to fall back to when nothing here claims a value.
 */
class ResultConverterRegistry(
    private val parent: ResultConverterRegistry? = null
) {
    private val lock = ReentrantLock()

    @Volatile
    private var converters: Map<KClass<*>, List<ResultConverter<*, *>>> = emptyMap()

    /**
     * Registers a converter ahead of everything already here.
     *
     * @param converter The converter to add.
     */
    fun addConverter(converter: ResultConverter<*, *>) = lock.withLock {
        val newMap = converters.toMutableMap()
        val list = newMap.getOrDefault(converter.supportedSourceClass, emptyList()).toMutableList()
        // Adding to the beginning so that newer converters have higher priority
        list.add(0, converter)
        newMap[converter.supportedSourceClass] = list
        converters = newMap
    }

    /**
     * Finds the converter that claims a value, searching this registry and then its parent.
     *
     * @param sourceClass The class of the decoded value.
     * @param expectedType The Kotlin type wanted.
     * @param sourceType The PostgreSQL type of the value.
     * @param context Passed on to each candidate's `canConvert`.
     * @return The first converter to claim the value, or `null` if none does.
     */
    @Suppress("UNCHECKED_CAST")
    fun findConverter(sourceClass: KClass<*>, expectedType: KType, sourceType: PgType, context: DeserializationContext): ResultConverter<Any, *>? {
        val specificConverters: List<ResultConverter<*,*>>? = converters[sourceClass]
        if (specificConverters != null) {
            for (i in 0 until specificConverters.size) {
                @Suppress("UNCHECKED_CAST")
                val converter = specificConverters[i] as ResultConverter<Any, *>
                if (converter.canConvert(sourceClass, expectedType, sourceType, context)) {
                    return converter
                }
            }
        }

        val anyConverters: List<ResultConverter<*,*>>? = converters[Any::class]
        if (anyConverters != null) {
            for (i in 0 until anyConverters.size) {
                @Suppress("UNCHECKED_CAST")
                val converter = anyConverters[i] as ResultConverter<Any, *>
                if (converter.canConvert(sourceClass, expectedType, sourceType, context)) {
                    return converter
                }
            }
        }

        return parent?.findConverter(sourceClass, expectedType, sourceType, context)
    }
}
