package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.codec.dynamic.*
import io.github.octaviusframework.driver.codec.standard.*
import io.github.octaviusframework.driver.container.*
import kotlin.reflect.KClass
import io.github.octaviusframework.driver.type.PgType

class CodecDictionary private constructor(
    private val codecsByOid: IntObjectMap<TypeCodec<*>>,
    private val codecsByClass: Map<KClass<*>, TypeCodec<*>>,
    private val codecToOid: Map<TypeCodec<*>, Int>,
    val registeredCodecs: List<TypeCodec<*>>
) {
    companion object {

        fun createWithBuiltins(): CodecDictionary {
            val oidMap = IntObjectMap<TypeCodec<*>>()
            val classMap = mutableMapOf<KClass<*>, TypeCodec<*>>()
            val codecToOidMap = mutableMapOf<TypeCodec<*>, Int>()
            val registeredCodecsList = mutableListOf<TypeCodec<*>>()

            fun register(codec: TypeCodec<*>) {
                registeredCodecsList.add(codec)
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

            // Number Types
            register(SmallIntCodec)
            register(IntCodec)
            register(BigIntCodec)
            register(RealCodec)
            register(DoubleCodec)
            register(NumericCodec)
            // Text Types
            register(TextCodec)
            register(VarcharCodec)
            register(BpcharCodec)
            register(UnknownCodec)
            // Json
            register(JsonbCodec)
            register(JsonCodec)
            // DateTime
            register(TimestamptzCodec)
            register(TimestampCodec)
            register(DateCodec)
            register(TimeCodec)
            register(IntervalCodec)
            // Other
            register(ByteaCodec)
            register(UuidCodec)
            register(VoidCodec)
            register(BooleanCodec)

            return CodecDictionary(oidMap, classMap, codecToOidMap, registeredCodecsList)
        }
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

    fun withRegisteredCodec(codec: TypeCodec<*>, dictionary: TypeDictionary): CodecDictionary {
        val newOidMap = IntObjectMap(this.codecsByOid)
        val newClassMap = this.codecsByClass.toMutableMap()
        val newCodecToOid = this.codecToOid.toMutableMap()
        val newRegisteredCodecs = this.registeredCodecs + codec

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
            val resolvedOid = dictionary.resolveOid(codec.pgTypeName, codec.pgSchema, searchPath = emptyList())
            newOidMap[resolvedOid] = codec
            newCodecToOid[codec] = resolvedOid
        } else {
            dictionary.forEachType { oid, type ->
                if (type.name == codec.pgTypeName && (codec.pgSchema.isEmpty() || codec.pgSchema == type.schema)) {
                    newOidMap[oid] = codec
                }
            }
        }

        return CodecDictionary(newOidMap, newClassMap, newCodecToOid, newRegisteredCodecs)
    }

    fun buildUpdated(newTypes: Map<Int, PgType>, registry: TypeRegistry): CodecDictionary {
        val newOidMap = IntObjectMap<TypeCodec<*>>()
        val newCodecToOid = mutableMapOf<TypeCodec<*>, Int>()

        for (codec in this.registeredCodecs) {
            if (codec.oid != null) {
                newOidMap[codec.oid!!] = codec
                newCodecToOid[codec] = codec.oid!!
            } else if (codec.pgSchema.isNotBlank()) {
                for ((oid, type) in newTypes) {
                    if (type.name == codec.pgTypeName && type.schema == codec.pgSchema) {
                        newOidMap[oid] = codec
                        newCodecToOid[codec] = oid
                        break
                    }
                }
            } else {
                for ((oid, type) in newTypes) {
                    if (type.name == codec.pgTypeName) {
                        newOidMap[oid] = codec
                    }
                }
            }
        }

        for ((oid, type) in newTypes) {
            if (!newOidMap.containsKey(oid)) {
                val codec = when (type) {
                    is PgType.Enum -> DynamicEnumCodec(oid, type.name, type.schema)
                    is PgType.Domain -> DynamicDomainCodec<Any>(oid, type.name, type.schema, type.baseTypeOid, registry)
                    is PgType.Array -> DynamicContainerCodec(oid, type.name, type.schema, PgArray::class, registry)
                    is PgType.Composite -> DynamicContainerCodec(
                        oid,
                        type.name,
                        type.schema,
                        PgComposite::class,
                        registry
                    )

                    is PgType.Record -> DynamicContainerCodec(oid, type.name, type.schema, PgRecord::class, registry)
                    is PgType.Range -> DynamicContainerCodec(oid, type.name, type.schema, PgRange::class, registry)
                    is PgType.Multirange -> DynamicContainerCodec(
                        oid,
                        type.name,
                        type.schema,
                        PgMultirange::class,
                        registry
                    )

                    else -> null
                }
                if (codec != null) {
                    newOidMap[oid] = codec
                    newCodecToOid[codec] = oid
                }
            }
        }

        return CodecDictionary(newOidMap, this.codecsByClass, newCodecToOid, this.registeredCodecs)
    }
}
