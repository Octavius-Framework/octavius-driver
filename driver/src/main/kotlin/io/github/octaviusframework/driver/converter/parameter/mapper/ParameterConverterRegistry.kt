package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.identifier.QualifiedName
import io.github.octaviusframework.driver.type.PgTyped
import io.github.octaviusframework.driver.type.isKnownOid
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

class ParameterConverterRegistry(
    private val parent: ParameterConverterRegistry? = null
) {
    private val lock = ReentrantLock()

    @Volatile
    private var converters: List<ParameterConverter<*>> = emptyList()

    fun addConverter(converter: ParameterConverter<*>) = lock.withLock {
        val newList = converters.toMutableList()
        newList.add(0, converter)
        converters = newList
    }

    fun convert(source: Any, expectedOid: Int, context: SerializationContext): Any {
        for (i in 0 until converters.size) {
            val converter = converters[i]
            if (converter.canConvert(source::class, expectedOid, context)) {
                @Suppress("UNCHECKED_CAST")
                var result = (converter as ParameterConverter<Any>).convert(source, expectedOid, context)
                if (result !is PgTyped && !expectedOid.isKnownOid) {
                    val defaultOid = converter.getDefaultOid(context)
                    if (defaultOid.isKnownOid) {
                        val type = context.typeManager.typeDictionary.getPgType(defaultOid)
                        result = PgTyped(result, QualifiedName(type.schema, type.name, false))
                    }
                }
                return result
            }
        }

        return parent?.convert(source, expectedOid, context) ?: source
    }

    fun findConverter(source: Any, expectedOid: Int, context: SerializationContext): ParameterConverter<Any>? {
        return findConverterByClass(source::class, expectedOid, context)
    }

    fun findConverterByClass(sourceClass: KClass<*>, expectedOid: Int, context: SerializationContext): ParameterConverter<Any>? {
        for (i in 0 until converters.size) {
            val converter = converters[i]
            if (converter.canConvert(sourceClass, expectedOid, context)) {
                @Suppress("UNCHECKED_CAST")
                return converter as ParameterConverter<Any>
            }
        }
        return parent?.findConverterByClass(sourceClass, expectedOid, context)
    }
}