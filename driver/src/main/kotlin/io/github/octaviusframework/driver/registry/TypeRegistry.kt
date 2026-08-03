package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.codec.dynamic.DynamicDomainCodec
import io.github.octaviusframework.driver.codec.dynamic.DynamicEnumCodec
import io.github.octaviusframework.driver.codec.dynamic.DynamicContainerCodec
import io.github.octaviusframework.driver.codec.standard.*
import io.github.octaviusframework.driver.container.PgArray
import io.github.octaviusframework.driver.container.PgComposite
import io.github.octaviusframework.driver.container.PgMultirange
import io.github.octaviusframework.driver.container.PgRange
import io.github.octaviusframework.driver.container.PgRecord
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
import io.github.octaviusframework.driver.exception.TypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
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
    private var codecsByOid: IntObjectMap<TypeCodec<*>> = IntObjectMap()


    @Volatile
    private var codecsByClass: Map<KClass<*>, TypeCodec<*>> = emptyMap()

    @Volatile
    private var codecToOid: Map<TypeCodec<*>, Int> = emptyMap()

    @Volatile
    private var customDynamicCodecs: List<TypeCodec<*>> = emptyList()

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

    init {
        val newOidMap = IntObjectMap<TypeCodec<*>>()
        val newClassMap = mutableMapOf<KClass<*>, TypeCodec<*>>()
        val newCodecToOid = mutableMapOf<TypeCodec<*>, Int>()
        registerBuiltins(newOidMap, newClassMap, newCodecToOid)
        codecsByOid = newOidMap
        codecsByClass = newClassMap
        codecToOid = newCodecToOid
    }

    private fun registerBuiltins(
        oidMap: IntObjectMap<TypeCodec<*>>,
        classMap: MutableMap<KClass<*>, TypeCodec<*>>,
        codecToOidMap: MutableMap<TypeCodec<*>, Int>
    ) {
        fun register(codec: TypeCodec<*>) {
            if (codec.isDefaultForKotlinType) {
                classMap[codec.kotlinClass] = codec

                if (codec.kotlinClass.isSealed) {
                    codec.kotlinClass.sealedSubclasses.forEach { subclass ->
                        classMap[subclass] = codec
                    }
                }
            }
            if (codec.oid != null) {
                oidMap[codec.oid!!] = codec
                codecToOidMap[codec] = codec.oid!!
            }
        }
        // Postgres Internal Types
        register(OidCodec)
        register(NameCodec)
        register(CharCodec)

        register(SmallIntCodec)
        register(IntCodec)
        register(BigIntCodec)
        register(RealCodec)
        register(DoubleCodec)
        register(BooleanCodec)
        register(TextCodec)
        register(VarcharCodec)
        register(BpcharCodec)
        register(ByteaCodec)
        register(UnknownCodec)

        // DateTime
        register(TimestamptzCodec)
        register(TimestampCodec)
        register(DateCodec)
        register(TimeCodec)
        register(IntervalCodec)

        // Json
        register(JsonbCodec)
        register(JsonCodec)

        register(UuidCodec)
        register(NumericCodec)
        register(VoidCodec)
    }

    /**
     * Registers a custom codec. If OID and schema are missing (dynamic type in multi-schema),
     * it will be mapped globally for deserialization but resolved in-flight for serialization.
     */
    fun registerCodec(codec: TypeCodec<*>) = lock.withLock {
        val newOidMap = IntObjectMap(codecsByOid)
        val newClassMap = codecsByClass.toMutableMap()
        val newCodecToOid = codecToOid.toMutableMap()

        if (codec.isDefaultForKotlinType) {
            newClassMap[codec.kotlinClass] = codec
            if (codec.kotlinClass.isSealed) {
                codec.kotlinClass.sealedSubclasses.forEach { subclass ->
                    newClassMap[subclass] = codec
                }
            }
        }

        if (codec.oid != null) {
            newOidMap[codec.oid!!] = codec
            newCodecToOid[codec] = codec.oid!!
        } else if (codec.pgSchema.isNotBlank()) {
            val resolvedOid = resolveOid(codec.pgTypeName, codec.pgSchema, searchPath = emptyList())
            newOidMap[resolvedOid] = codec
            newCodecToOid[codec] = resolvedOid
        } else {
            customDynamicCodecs = customDynamicCodecs + codec

            dictionary.types.forEach { oid, type ->
                if (type.name == codec.pgTypeName) {
                    newOidMap[oid] = codec
                }
            }
        }

        codecsByOid = newOidMap
        codecsByClass = newClassMap
        codecToOid = newCodecToOid
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getCodecByOid(oid: Int): TypeCodec<T>? {
        return codecsByOid[oid] as TypeCodec<T>?
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getCodecByClass(kClass: KClass<T>): TypeCodec<T>? {
        return codecsByClass[kClass] as TypeCodec<T>?
    }

    fun getOidForCodec(codec: TypeCodec<*>): Int? {
        return codecToOid[codec]
    }


    /**
     * Replaces the entire type map with a new instance, ensuring thread-safety.
     * Additionally applies custom codecs waiting for an OID.
     */
    fun updateTypes(newTypes: Map<Int, PgType>) {
        val newOidMap = IntObjectMap(codecsByOid)
        val newCodecToOid = codecToOid.toMutableMap()

        // Preallocate to avoid any rehashes during initialization (load factor 0.75)
        val intMap = IntObjectMap<PgType>((newTypes.size / 0.75).toInt() + 1)
        val newTypesByName = mutableMapOf<String, MutableMap<String, Int>>()
        val newArrayTypesByElementOid = IntObjectMap<PgType.Array>()
        val newRangeTypesByElementOid = IntObjectMap<PgType.Range>()
        val newMultirangeTypesByRangeOid = IntObjectMap<PgType.Multirange>()

        for ((oid, type) in newTypes) {
            intMap[oid] = type
            newTypesByName.getOrPut(type.name) { mutableMapOf() }[type.schema] = oid
            when (type) {
                is PgType.Array -> newArrayTypesByElementOid[type.elementOid] = type
                is PgType.Range -> newRangeTypesByElementOid[type.subtypeOid] = type
                is PgType.Multirange -> newMultirangeTypesByRangeOid[type.rangeOid] = type
                else -> {}
            }
        }

        for (codec in customDynamicCodecs) {
            for ((oid, type) in newTypes) {
                if (type.name == codec.pgTypeName && (codec.pgSchema.isEmpty() || codec.pgSchema == type.schema)) {
                    newOidMap[oid] = codec
                }
            }
        }

        for ((oid, type) in newTypes) {
            if (!newOidMap.containsKey(oid)) {
                val codec = when (type) {
                    is PgType.Enum -> DynamicEnumCodec(oid, type.name, type.schema)
                    is PgType.Domain -> DynamicDomainCodec<Any>(oid, type.name, type.schema, type.baseTypeOid, this)
                    is PgType.Array -> DynamicContainerCodec(oid, type.name, type.schema, PgArray::class, this)
                    is PgType.Composite -> DynamicContainerCodec(oid, type.name, type.schema, PgComposite::class, this)
                    is PgType.Record -> DynamicContainerCodec(oid, type.name, type.schema, PgRecord::class, this)
                    is PgType.Range -> DynamicContainerCodec(oid, type.name, type.schema, PgRange::class, this)
                    is PgType.Multirange -> DynamicContainerCodec(
                        oid,
                        type.name,
                        type.schema,
                        PgMultirange::class,
                        this
                    )
                    else -> null
                }
                if (codec != null) {
                    newOidMap[oid] = codec
                    newCodecToOid[codec] = oid
                }
            }
        }

        dictionary = TypeDictionary(
            intMap,
            newTypesByName,
            newArrayTypesByElementOid,
            newRangeTypesByElementOid,
            newMultirangeTypesByRangeOid
        )

        codecsByOid = newOidMap
        codecToOid = newCodecToOid
    }


    fun resolveOid(
        typeName: String,
        requestedSchema: String,
        isArray: Boolean = false,
        searchPath: List<String>
    ): Int {
        return dictionary.resolveOid(typeName, requestedSchema, isArray, searchPath)
    }
}
