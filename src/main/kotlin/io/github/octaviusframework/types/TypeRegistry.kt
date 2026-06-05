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
}
