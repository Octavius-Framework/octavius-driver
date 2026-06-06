package io.github.octaviusframework.types

import io.github.octaviusframework.exceptions.OctaviusTypeException
import io.github.octaviusframework.exceptions.TypeExceptionMessage
import kotlin.reflect.KClass



class TypeRegistry {
    @Volatile
    var types: Map<UInt, PgType> = emptyMap()

    @Volatile
    private var serializersByOid: Map<UInt, TypeSerializer<*>> = emptyMap()

    @Volatile
    private var serializersByName: Map<QualifiedName, TypeSerializer<*>> = emptyMap()
    
    @Volatile
    private var serializersByClass: Map<KClass<*>, TypeSerializer<*>> = emptyMap()

    init {
        val newOidMap = mutableMapOf<UInt, TypeSerializer<*>>()
        val newClassMap = mutableMapOf<KClass<*>, TypeSerializer<*>>()
        registerBuiltins(newOidMap, newClassMap)
        serializersByOid = newOidMap
        serializersByClass = newClassMap
    }

    private fun registerBuiltins(
        oidMap: MutableMap<UInt, TypeSerializer<*>>,
        classMap: MutableMap<KClass<*>, TypeSerializer<*>>
    ) {
        fun register(serializer: TypeSerializer<*>) {
            if (serializer.isDefaultForKotlinType) {
                classMap[serializer.kotlinClass] = serializer
            }
            if (serializer.oid != null) {
                oidMap[serializer.oid!!] = serializer
            }
        }

        register(ShortSerializer)
        register(IntSerializer)
        register(LongSerializer)
        register(FloatSerializer)
        register(DoubleSerializer)
        register(BooleanSerializer)
        register(StringSerializer)
        register(VarcharSerializer)
        register(BpcharSerializer)
        register(ByteArraySerializer)
        
        // DateTime
        register(InstantSerializer)
        register(LocalDateTimeSerializer)
        register(LocalDateSerializer)
        register(LocalTimeSerializer)
        
        // Json
        register(JsonbElementSerializer)
        register(JsonElementSerializer)
        
        // Additional
        register(UuidSerializer)
        register(NumericSerializer)
        register(UnitSerializer)
    }

    /**
     * Rejestruje własny serializator. Jeżeli OID jest nieznane (typ dynamiczny),
     * zostanie dopasowane po nazwie natychmiast, a także zapamiętane przy
     * kolejnych przeładowaniach słownika (reloadTypes).
     */
    fun registerSerializer(serializer: TypeSerializer<*>, searchPath: List<String> = emptyList()) {
        val newOidMap = serializersByOid.toMutableMap()
        val newClassMap = serializersByClass.toMutableMap()
        val newNameMap = serializersByName.toMutableMap()
        
        if (serializer.isDefaultForKotlinType) {
            newClassMap[serializer.kotlinClass] = serializer
        }
        
        val qName = QualifiedName(serializer.pgSchema, serializer.pgTypeName)
        newNameMap[qName] = serializer

        if (serializer.oid != null) {
            newOidMap[serializer.oid!!] = serializer
        } else {
            if (types.isNotEmpty()) {
                val (resolvedOid, resolvedQName) = resolveOid(serializer.pgTypeName, serializer.pgSchema, searchPath)
                newOidMap[resolvedOid] = serializer
                newNameMap[resolvedQName] = serializer
            }
        }
        
        serializersByOid = newOidMap
        serializersByClass = newClassMap
        serializersByName = newNameMap
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getSerializerByOid(oid: UInt): TypeSerializer<T>? {
        return serializersByOid[oid] as TypeSerializer<T>?
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getSerializerByClass(kClass: KClass<T>): TypeSerializer<T>? {
        return serializersByClass[kClass] as TypeSerializer<T>?
    }

    /**
     * Zastępuje całą mapę typów nową instancją, gwarantując thread-safety.
     * Dodatkowo aplikuje customowe serializatory oczekujące na OID.
     */
    fun updateTypes(newTypes: Map<UInt, PgType>, searchPath: List<String> = emptyList()) {
        val newOidMap = serializersByOid.toMutableMap()
        val newNameMap = serializersByName.toMutableMap()
        for ((name, serializer) in serializersByName) {
            if (serializer.oid == null) {
                val (resolvedOid, resolvedQName) = resolveOid(name.name, name.schema, searchPath, sourceTypes = newTypes)
                newOidMap[resolvedOid] = serializer
                newNameMap[resolvedQName] = serializer
            }
        }
        types = newTypes
        serializersByOid = newOidMap
        serializersByName = newNameMap
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
        var resolvedSchema: String = ""

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
