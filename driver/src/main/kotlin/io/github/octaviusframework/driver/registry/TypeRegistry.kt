package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.converter.ReflectionCompositeCache
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
import io.github.octaviusframework.driver.identifier.CaseConvention
import io.github.octaviusframework.driver.identifier.QualifiedName
import io.github.octaviusframework.driver.type.PgType
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

class TypeRegistry {
    val lock = ReentrantLock()

    @Volatile
    internal var isLoaded: Boolean = false

    val converterRegistry = ResultConverterRegistry().apply {
        addConverter(MapCompositeConverter())
        addConverter(PrimitiveArrayConverter())
        addConverter(CollectionArrayConverter())
        addConverter(ReflectionCompositeConverter())
        addConverter(ReflectionRowConverter())
        addConverter(MapRowConverter())
        addConverter(MapRecordConverter())
        addConverter(JsonElementConverter())
        addConverter(RangeResultConverter())
        addConverter(MultiRangeResultConverter())
    }

    val parameterConverterRegistry = ParameterConverterRegistry().apply {
        addConverter(PrimitiveArrayParameterConverter())
        addConverter(CollectionArrayParameterConverter())
        addConverter(ReflectionCompositeParameterConverter())
        addConverter(JsonElementParameterConverter())
        addConverter(RangeParameterConverter())
        addConverter(MultiRangeParameterConverter())
    }

    fun registerResultConverter(converter: ResultConverter<*, *>) {
        converterRegistry.addConverter(converter)
    }

    fun registerParameterConverter(converter: ParameterConverter<*>) {
        parameterConverterRegistry.addConverter(converter)
    }

    @Volatile
    var dictionary: TypeDictionary = TypeDictionary.EMPTY

    @Volatile
    var codecs: CodecDictionary = CodecDictionary.createWithBuiltins()

    @Volatile
    var registeredComposites: Map<KClass<*>, CompositeRegistration> = emptyMap()

    @Volatile
    var compositeClassByName: Map<QualifiedName, KClass<*>> = emptyMap()

    inline fun <reified T : Any> registerAutoCompositeType(
        name: String,
        schema: String = "",
        pgConvention: CaseConvention = CaseConvention.SNAKE_CASE_LOWER,
        kotlinConvention: CaseConvention = CaseConvention.CAMEL_CASE
    ) = lock.withLock {
        val newMap = registeredComposites.toMutableMap()
        val qName = QualifiedName(schema, name)
        newMap[T::class] = CompositeRegistration(qName, pgConvention, kotlinConvention)
        registeredComposites = newMap

        val newNameMap = compositeClassByName.toMutableMap()
        newNameMap[qName] = T::class
        compositeClassByName = newNameMap

        ReflectionCompositeCache.getOrCreateDataObjectMetadata(T::class, pgConvention, kotlinConvention)
    }

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
