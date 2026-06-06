package io.github.octaviusframework.types

import kotlin.reflect.KClass



class TypeRegistry {
    val types = mutableMapOf<UInt, PgType>()

    private val handlersByOid = mutableMapOf<UInt, TypeHandler<*>>()
    private val handlersByClass = mutableMapOf<KClass<*>, TypeHandler<*>>()

    init {
        // Rejestrujemy wbudowane typy
        registerBuiltins()
    }

    fun registerHandler(handler: TypeHandler<*>) {
        if (handler.isDefaultForKotlinType) {
            handlersByClass[handler.kotlinClass] = handler
        }
        handler.oid?.let { handlersByOid[it] = handler }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getHandlerByOid(oid: UInt): TypeHandler<T>? {
        return handlersByOid[oid] as TypeHandler<T>?
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getHandlerByClass(kClass: KClass<T>): TypeHandler<T>? {
        return handlersByClass[kClass] as TypeHandler<T>?
    }

    private fun registerBuiltins() {
        registerHandler(ShortHandler)
        registerHandler(IntHandler)
        registerHandler(LongHandler)
        registerHandler(FloatHandler)
        registerHandler(DoubleHandler)
        registerHandler(BooleanHandler)
        registerHandler(StringHandler)
        registerHandler(VarcharHandler)
        registerHandler(BpcharHandler)
        registerHandler(ByteArrayHandler)
        
        // DateTime
        registerHandler(InstantHandler)
        registerHandler(LocalDateTimeHandler)
        registerHandler(LocalDateHandler)
        registerHandler(LocalTimeHandler)
        
        // Json
        registerHandler(JsonbElementHandler)
        registerHandler(JsonElementHandler)
        
        // Additional
        registerHandler(UuidHandler)
        registerHandler(NumericHandler)
        registerHandler(UnitHandler)
    }

    fun clearOidMappings() {
        types.clear()
        handlersByOid.clear()
        registerBuiltins()
    }

    fun resolveOid(
        typeName: String,
        requestedSchema: String,
        searchPath: List<String>,
        isArray: Boolean = false
    ): Pair<UInt, QualifiedName> {
        // Find matching types by name
        val schemasForName = types.values
            .filter { it.name == typeName }
            .groupBy { it.schema }
            .mapValues { it.value.first().oid }

        if (schemasForName.isEmpty()) {
            throw io.github.octaviusframework.exceptions.OctaviusTypeException(
                messageEnum = io.github.octaviusframework.exceptions.TypeExceptionMessage.TYPE_NOT_FOUND,
                typeName = typeName,
                details = "Type '$typeName' not found in any scanned schemas"
            )
        }

        var resolvedOid: UInt? = null
        var resolvedSchema: String = ""

        // 1. If schema is explicitly requested
        if (requestedSchema.isNotBlank()) {
            resolvedOid = schemasForName[requestedSchema]
                ?: throw io.github.octaviusframework.exceptions.OctaviusTypeException(
                    messageEnum = io.github.octaviusframework.exceptions.TypeExceptionMessage.TYPE_NOT_FOUND,
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
                    throw io.github.octaviusframework.exceptions.OctaviusTypeException(
                        messageEnum = io.github.octaviusframework.exceptions.TypeExceptionMessage.TYPE_NOT_FOUND,
                        typeName = typeName,
                        details = "Type '$typeName' is ambiguous. Found in schemas: ${schemasForName.keys.joinToString()}. Please specify schema."
                    )
                }
            }
        }

        if (isArray) {
            val arrayType = types.values.firstOrNull { it is PgType.Array && it.elementOid == resolvedOid }
                ?: throw io.github.octaviusframework.exceptions.OctaviusTypeException(
                    messageEnum = io.github.octaviusframework.exceptions.TypeExceptionMessage.TYPE_NOT_FOUND,
                    typeName = typeName,
                    details = "Array type for '$typeName' not found in registry"
                )
            return arrayType.oid to QualifiedName(resolvedSchema, typeName, true)
        }

        return resolvedOid to QualifiedName(resolvedSchema, typeName, false)
    }
}
