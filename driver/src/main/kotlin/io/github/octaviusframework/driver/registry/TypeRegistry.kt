package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.codec.dynamic.DynamicDomainCodec
import io.github.octaviusframework.driver.codec.dynamic.DynamicEnumCodec
import io.github.octaviusframework.driver.codec.standard.*
import io.github.octaviusframework.driver.converter.ReflectionCompositeCache
import io.github.octaviusframework.driver.converter.parameter.array.CollectionArrayParameterConverter
import io.github.octaviusframework.driver.converter.parameter.array.PrimitiveArrayParameterConverter
import io.github.octaviusframework.driver.converter.parameter.composite.ReflectionCompositeParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverterRegistry
import io.github.octaviusframework.driver.converter.parameter.standard.JsonElementParameterConverter
import io.github.octaviusframework.driver.converter.result.array.CollectionArrayConverter
import io.github.octaviusframework.driver.converter.result.array.PrimitiveArrayConverter
import io.github.octaviusframework.driver.converter.result.composite.MapCompositeConverter
import io.github.octaviusframework.driver.converter.result.composite.ReflectionCompositeConverter
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverterRegistry
import io.github.octaviusframework.driver.converter.result.record.MapRecordConverter
import io.github.octaviusframework.driver.converter.result.row.MapRowConverter
import io.github.octaviusframework.driver.converter.result.row.ReflectionRowConverter
import io.github.octaviusframework.driver.converter.result.standard.JsonElementConverter
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.identifier.CaseConvention
import io.github.octaviusframework.driver.identifier.QualifiedName
import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass

class TypeRegistry {
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
    }

    val parameterConverterRegistry = ParameterConverterRegistry().apply {
        addConverter(PrimitiveArrayParameterConverter())
        addConverter(CollectionArrayParameterConverter())
        addConverter(ReflectionCompositeParameterConverter())
        addConverter(JsonElementParameterConverter())
    }

    fun registerResultConverter(converter: ResultConverter<*, *>) {
        converterRegistry.addConverter(converter)
    }

    fun registerParameterConverter(converter: ParameterConverter<*>) {
        parameterConverterRegistry.addConverter(converter)
    }

    @Volatile
    var types: IntObjectMap<PgType> = IntObjectMap()

    @Volatile
    private var codecsByOid: IntObjectMap<TypeCodec<*>> = IntObjectMap()



    @Volatile
    private var codecsByClass: Map<KClass<*>, TypeCodec<*>> = emptyMap()

    @Volatile
    private var codecToOid: Map<TypeCodec<*>, Int> = emptyMap()

    @Volatile
    var registeredComposites: Map<KClass<*>, CompositeRegistration> = emptyMap()

    inline fun <reified T : Any> registerAutoCompositeType(
        name: String,
        schema: String = "",
        pgConvention: CaseConvention = CaseConvention.SNAKE_CASE_LOWER,
        kotlinConvention: CaseConvention = CaseConvention.CAMEL_CASE
    ) {
        val newMap = registeredComposites.toMutableMap()
        newMap[T::class] = CompositeRegistration(QualifiedName(schema, name), pgConvention, kotlinConvention)
        registeredComposites = newMap

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

        register(ShortCodec)
        register(IntCodec)
        register(LongCodec)
        register(FloatCodec)
        register(DoubleCodec)
        register(BooleanCodec)
        register(StringCodec)
        register(VarcharCodec)
        register(BpcharCodec)
        register(ByteArrayCodec)
        register(UnknownCodec)

        // DateTime
        register(InstantCodec)
        register(LocalDateTimeCodec)
        register(LocalDateCodec)
        register(LocalTimeCodec)

        // Json
        register(JsonbCodec)
        register(JsonCodec)

        register(UuidCodec)
        register(NumericCodec)
        register(UnitCodec)
    }

    /**
     * Registers a custom codec. If OID is unknown (dynamic type),
     * it will be matched by name immediately, and also remembered during
     * subsequent dictionary reloads (reloadTypes).
     */
    fun registerCodec(codec: TypeCodec<*>, searchPath: List<String> = emptyList()) {
        val newOidMap = IntObjectMap(codecsByOid)
        val newClassMap = codecsByClass.toMutableMap()
        val newCodecToOid = codecToOid.toMutableMap()
        
        if (codec.isDefaultForKotlinType) {
            newClassMap[codec.kotlinClass] = codec
        }

        if (codec.oid != null) {
            newOidMap[codec.oid!!] = codec
            newCodecToOid[codec] = codec.oid!!
        } else {
            val resolvedOid = resolveOid(codec.pgTypeName, codec.pgSchema, searchPath = searchPath)
            newOidMap[resolvedOid] = codec
            newCodecToOid[codec] = resolvedOid
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
        return codecToOid[codec] ?: codec.oid
    }

    /**
     * Replaces the entire type map with a new instance, ensuring thread-safety.
     * Additionally applies custom codecs waiting for an OID.
     */
    fun updateTypes(newTypes: Map<Int, PgType>, searchPath: List<String> = emptyList()) {
        val newOidMap = IntObjectMap(codecsByOid)
        val newCodecToOid = codecToOid.toMutableMap()
        
        for ((codec, previousOid) in codecToOid) {
            if (codec.oid == null) {
                val resolvedOid = resolveOid(
                    codec.pgTypeName,
                    codec.pgSchema,
                    searchPath = searchPath,
                    sourceTypes = newTypes.values
                )
                newOidMap[resolvedOid] = codec
                newCodecToOid[codec] = resolvedOid
            }
        }

        for ((oid, type) in newTypes) {
            if (type is PgType.Enum && !newOidMap.containsKey(oid)) {
                val enumCodec = DynamicEnumCodec(oid, type.name, type.schema)
                newOidMap[oid] = enumCodec
                newCodecToOid[enumCodec] = oid
            } else if (type is PgType.Domain && !newOidMap.containsKey(oid)) {
                val domainCodec = DynamicDomainCodec<Any>(oid, type.name, type.schema, type.baseTypeOid, this)
                newOidMap[oid] = domainCodec
                newCodecToOid[domainCodec] = oid
            }
        }

        // Preallocate to avoid any rehashes during initialization (load factor 0.75)
        val intMap = IntObjectMap<PgType>((newTypes.size / 0.75).toInt() + 1)
        for ((oid, type) in newTypes) {
            intMap[oid] = type
        }
        types = intMap
        codecsByOid = newOidMap
        codecToOid = newCodecToOid
    }


    fun resolveOid(
        typeName: String,
        requestedSchema: String,
        isArray: Boolean = false,
        searchPath: List<String>,
        sourceTypes: Iterable<PgType> = types.values
    ): Int {
        // Find matching types by name
        val schemasForName = sourceTypes
            .filter { it.name == typeName }
            .groupBy { it.schema }
            .mapValues { it.value.first().oid }

        if (schemasForName.isEmpty()) {
            throw OctaviusTypeException(
                messageEnum = TypeExceptionMessage.TYPE_NOT_FOUND,
                typeName = typeName,
                details = "Type '$typeName' not found in any scanned schemas"
            )
        }

        var resolvedOid: Int? = null

        // 1. If schema is explicitly requested
        if (requestedSchema.isNotBlank()) {
            resolvedOid = schemasForName[requestedSchema]
                ?: throw OctaviusTypeException(
                    messageEnum = TypeExceptionMessage.TYPE_NOT_FOUND,
                    typeName = typeName,
                    details = "Type '$typeName' not found in requested schema '$requestedSchema'"
                )
        } else {
            // 2. If schema is empty, look in search_path (first match wins)
            for (schema in searchPath) {
                val oid = schemasForName[schema]
                if (oid != null) {
                    resolvedOid = oid
                    break
                }
            }

            // 3. If not in search_path, check for unambiguous match
            if (resolvedOid == null) {
                if (schemasForName.size == 1) {
                    val entry = schemasForName.entries.first()
                    resolvedOid = entry.value
                } else {
                    throw OctaviusTypeException(
                        messageEnum = TypeExceptionMessage.TYPE_NOT_FOUND,
                        typeName = typeName,
                        details = "Type '$typeName' is ambiguous. Found in schemas: ${schemasForName.keys.joinToString()}. Please specify schema."
                    )
                }
            }
        }

        if (isArray) {
            val arrayType = types.values.firstOrNull { it is PgType.Array && it.elementOid == resolvedOid }
                ?: throw OctaviusTypeException(
                    messageEnum = TypeExceptionMessage.TYPE_NOT_FOUND,
                    typeName = typeName,
                    details = "Array type for '$typeName' not found in registry"
                )
            return arrayType.oid
        }

        return resolvedOid
    }
}

