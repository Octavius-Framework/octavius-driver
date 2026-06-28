package io.github.octaviusframework.driver.type

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.codec.dynamic.DynamicEnumCodec
import io.github.octaviusframework.driver.codec.dynamic.DynamicDomainCodec
import io.github.octaviusframework.driver.codec.standard.*
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.mapping.parameter.CollectionArrayParameterConverter
import io.github.octaviusframework.driver.mapping.parameter.ParameterConverter
import io.github.octaviusframework.driver.mapping.parameter.ParameterConverterRegistry
import io.github.octaviusframework.driver.mapping.parameter.ReflectionCompositeParameterConverter
import io.github.octaviusframework.driver.mapping.result.JsonElementConverter
import io.github.octaviusframework.driver.mapping.result.ResultConverter
import io.github.octaviusframework.driver.mapping.result.ResultConverterRegistry
import io.github.octaviusframework.driver.mapping.result.array.CollectionArrayConverter
import io.github.octaviusframework.driver.mapping.result.composite.MapCompositeConverter
import io.github.octaviusframework.driver.mapping.result.composite.ReflectionCompositeConverter
import io.github.octaviusframework.driver.mapping.result.row.MapRowConverter
import io.github.octaviusframework.driver.mapping.result.row.ReflectionRowConverter
import kotlin.reflect.KClass

class TypeRegistry {
    val converterRegistry = ResultConverterRegistry().apply {
        addConverter(MapCompositeConverter())
        addConverter(CollectionArrayConverter())
        addConverter(ReflectionCompositeConverter())
        addConverter(ReflectionRowConverter())
        addConverter(MapRowConverter())
        addConverter(JsonElementConverter())
    }

    val parameterConverterRegistry = ParameterConverterRegistry().apply {
        addConverter(CollectionArrayParameterConverter())
        addConverter(ReflectionCompositeParameterConverter())
    }

    fun registerResultConverter(converter: ResultConverter<*>) {
        converterRegistry.addConverter(converter)
    }

    fun registerParameterConverter(converter: ParameterConverter<*>) {
        parameterConverterRegistry.addConverter(converter)
    }

    @Volatile
    var types: Map<UInt, PgType> = emptyMap()

    @Volatile
    private var codecsByOid: Map<UInt, TypeCodec<*>> = emptyMap()

    @Volatile
    private var codecsByName: Map<QualifiedName, TypeCodec<*>> = emptyMap()

    @Volatile
    private var codecsByClass: Map<KClass<*>, TypeCodec<*>> = emptyMap()

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
    }

    init {
        val newOidMap = mutableMapOf<UInt, TypeCodec<*>>()
        val newClassMap = mutableMapOf<KClass<*>, TypeCodec<*>>()
        registerBuiltins(newOidMap, newClassMap)
        codecsByOid = newOidMap
        codecsByClass = newClassMap
    }

    private fun registerBuiltins(
        oidMap: MutableMap<UInt, TypeCodec<*>>,
        classMap: MutableMap<KClass<*>, TypeCodec<*>>
    ) {
        fun register(serializer: TypeCodec<*>) {
            if (serializer.isDefaultForKotlinType) {
                classMap[serializer.kotlinClass] = serializer
            }
            if (serializer.oid != null) {
                oidMap[serializer.oid!!] = serializer
            }
        }

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

        // DateTime
        register(InstantCodec)
        register(LocalDateTimeCodec)
        register(LocalDateCodec)
        register(LocalTimeCodec)

        // Json
        register(JsonbCodec)
        register(JsonCodec)

        // Additional
        register(UuidCodec)
        register(NumericCodec)
        register(UnitCodec)
    }

    /**
     * Registers a custom serializer. If OID is unknown (dynamic type),
     * it will be matched by name immediately, and also remembered during
     * subsequent dictionary reloads (reloadTypes).
     */
    fun registerCodec(codec: TypeCodec<*>, searchPath: List<String> = emptyList()) {
        val newOidMap = codecsByOid.toMutableMap()
        val newClassMap = codecsByClass.toMutableMap()
        val newNameMap = codecsByName.toMutableMap()

        if (codec.isDefaultForKotlinType) {
            newClassMap[codec.kotlinClass] = codec
        }

        val qName = QualifiedName(codec.pgSchema, codec.pgTypeName)
        newNameMap[qName] = codec

        if (codec.oid != null) {
            newOidMap[codec.oid!!] = codec
        } else {
            if (types.isNotEmpty()) {
                val (resolvedOid, resolvedQName) = resolveOid(codec.pgTypeName, codec.pgSchema, searchPath)
                newOidMap[resolvedOid] = codec
                newNameMap[resolvedQName] = codec
            }
        }

        codecsByOid = newOidMap
        codecsByClass = newClassMap
        codecsByName = newNameMap
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getCodecByOid(oid: UInt): TypeCodec<T>? {
        return codecsByOid[oid] as TypeCodec<T>?
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getCodecByClass(kClass: KClass<T>): TypeCodec<T>? {
        return codecsByClass[kClass] as TypeCodec<T>?
    }

    /**
     * Replaces the entire type map with a new instance, ensuring thread-safety.
     * Additionally applies custom serializers waiting for an OID.
     */
    fun updateTypes(newTypes: Map<UInt, PgType>, searchPath: List<String> = emptyList()) {
        val newOidMap = codecsByOid.toMutableMap()
        val newNameMap = codecsByName.toMutableMap()
        for ((name, codec) in codecsByName) {
            if (codec.oid == null) {
                val (resolvedOid, resolvedQName) = resolveOid(
                    name.name,
                    name.schema,
                    searchPath,
                    sourceTypes = newTypes
                )
                newOidMap[resolvedOid] = codec
                newNameMap[resolvedQName] = codec
            }
        }

        for ((oid, type) in newTypes) {
            if (type is PgType.Enum && !newOidMap.containsKey(oid)) {
                val enumCodec = DynamicEnumCodec(oid, type.name, type.schema)
                newOidMap[oid] = enumCodec
                newNameMap[QualifiedName(type.schema, type.name, false)] = enumCodec
            } else if (type is PgType.Domain && !newOidMap.containsKey(oid)) {
                val domainCodec = DynamicDomainCodec<Any>(oid, type.name, type.schema, type.baseTypeOid, this)
                newOidMap[oid] = domainCodec
                newNameMap[QualifiedName(type.schema, type.name, false)] = domainCodec
            }
        }

        types = newTypes
        codecsByOid = newOidMap
        codecsByName = newNameMap
    }


    fun resolveOid(
        typeName: String,
        requestedSchema: String,
        searchPath: List<String>,
        isArray: Boolean = false,
        sourceTypes: Map<UInt, PgType> = types
    ): Pair<UInt, QualifiedName> {
        // Find matching types by name
        val schemasForName = sourceTypes.values
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

        var resolvedOid: UInt? = null
        var resolvedSchema = ""

        // 1. If schema is explicitly requested
        if (requestedSchema.isNotBlank()) {
            resolvedOid = schemasForName[requestedSchema]
                ?: throw OctaviusTypeException(
                    messageEnum = TypeExceptionMessage.TYPE_NOT_FOUND,
                    typeName = typeName,
                    details = "Type '$typeName' not found in requested schema '$requestedSchema'"
                )
            resolvedSchema = requestedSchema
        } else {
            // 2. If schema is empty, look in search_path (first match wins)
            for (schema in searchPath) {
                val oid = schemasForName[schema]
                if (oid != null) {
                    resolvedOid = oid
                    resolvedSchema = schema
                    break
                }
            }

            // 3. If not in search_path, check for unambiguous match
            if (resolvedOid == null) {
                if (schemasForName.size == 1) {
                    val entry = schemasForName.entries.first()
                    resolvedSchema = entry.key
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
            return arrayType.oid to QualifiedName(resolvedSchema, typeName, true)
        }

        return resolvedOid to QualifiedName(resolvedSchema, typeName, false)
    }
}
