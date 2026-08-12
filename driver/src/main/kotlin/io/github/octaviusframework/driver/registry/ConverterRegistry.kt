package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.util.reflection.ReflectionCache
import io.github.octaviusframework.driver.converter.parameter.array.CollectionArrayParameterConverter
import io.github.octaviusframework.driver.converter.parameter.array.PrimitiveArrayParameterConverter
import io.github.octaviusframework.driver.converter.parameter.composite.ReflectionCompositeParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverterRegistry
import io.github.octaviusframework.driver.converter.parameter.range.MultiRangeParameterConverter
import io.github.octaviusframework.driver.converter.parameter.range.RangeParameterConverter
import io.github.octaviusframework.driver.converter.parameter.standard.JsonElementParameterConverter
import io.github.octaviusframework.driver.converter.result.array.CollectionArrayConverter
import io.github.octaviusframework.driver.converter.result.array.PrimitiveArrayConverter
import io.github.octaviusframework.driver.converter.result.composite.MapCompositeConverter
import io.github.octaviusframework.driver.converter.result.composite.ReflectionCompositeConverter
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverterRegistry
import io.github.octaviusframework.driver.converter.result.range.MultiRangeResultConverter
import io.github.octaviusframework.driver.converter.result.range.RangeResultConverter
import io.github.octaviusframework.driver.converter.result.record.MapRecordConverter
import io.github.octaviusframework.driver.converter.result.row.MapRowConverter
import io.github.octaviusframework.driver.converter.result.row.ReflectionRowConverter
import io.github.octaviusframework.driver.converter.result.standard.JsonElementConverter
import io.github.octaviusframework.driver.identifier.QualifiedName
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

/**
 * A registry managing the converters used for mapping between Kotlin objects and PostgreSQL types.
 *
 * It holds both [ResultConverterRegistry] for handling incoming data from the database,
 * and [ParameterConverterRegistry] for handling outbound query parameters. 
 * Furthermore, it keeps track of registered composite type mappings.
 */
class ConverterRegistry {
    /**
     * The registry responsible for result conversion (PostgreSQL -> Kotlin).
     */
    val resultConverterRegistry = ResultConverterRegistry().apply {
        addConverter(MapCompositeConverter)
        addConverter(PrimitiveArrayConverter)
        addConverter(CollectionArrayConverter)
        addConverter(ReflectionCompositeConverter)
        addConverter(ReflectionRowConverter)
        addConverter(MapRowConverter)
        addConverter(MapRecordConverter)
        addConverter(JsonElementConverter)
        addConverter(RangeResultConverter)
        addConverter(MultiRangeResultConverter)
    }

    /**
     * The registry responsible for parameter conversion (Kotlin -> PostgreSQL).
     */
    val parameterConverterRegistry = ParameterConverterRegistry().apply {
        addConverter(PrimitiveArrayParameterConverter)
        addConverter(CollectionArrayParameterConverter)
        addConverter(ReflectionCompositeParameterConverter)
        addConverter(JsonElementParameterConverter)
        addConverter(RangeParameterConverter)
        addConverter(MultiRangeParameterConverter)
    }

    /**
     * Registers a custom [ResultConverter].
     *
     * @param converter the converter to register.
     */
    fun registerResultConverter(converter: ResultConverter<*, *>) {
        resultConverterRegistry.addConverter(converter)
    }

    /**
     * Registers a custom [ParameterConverter].
     *
     * @param converter the converter to register.
     */
    fun registerParameterConverter(converter: ParameterConverter<*>) {
        parameterConverterRegistry.addConverter(converter)
    }

    val lock = ReentrantLock()

    /**
     * A thread-safe map holding registration details for custom Kotlin composite data classes.
     */
    @Volatile
    var registeredComposites: Map<KClass<*>, QualifiedName> = emptyMap()

    /**
     * A thread-safe map mapping database composite names to their corresponding Kotlin classes.
     */
    @Volatile
    var compositeClassByName: Map<QualifiedName, KClass<*>> = emptyMap()

    /**
     * Registers a Kotlin class to be automatically mapped to and from a PostgreSQL composite type.
     *
     * @param kClass the Kotlin data class to register.
     * @param name the name of the composite type in PostgreSQL.
     * @param schema the schema of the composite type (defaults to an empty string for the search path).
     */
    fun registerAutoCompositeType(
        kClass: KClass<*>,
        name: String,
        schema: String = ""
    ) = lock.withLock {
        val newMap = registeredComposites.toMutableMap()
        val qName = QualifiedName(schema, name)
        newMap[kClass] = qName
        registeredComposites = newMap

        val newNameMap = compositeClassByName.toMutableMap()
        newNameMap[qName] = kClass
        compositeClassByName = newNameMap

        ReflectionCache.getOrCreateDataObjectMetadata(kClass)
    }
}
