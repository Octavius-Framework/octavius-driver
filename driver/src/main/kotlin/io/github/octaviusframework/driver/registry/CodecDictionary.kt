package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.codec.dynamic.DynamicContainerCodec
import io.github.octaviusframework.driver.codec.dynamic.DynamicDomainCodec
import io.github.octaviusframework.driver.codec.dynamic.DynamicEnumCodec
import io.github.octaviusframework.driver.codec.standard.*
import io.github.octaviusframework.driver.container.*
import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass

/**
 * A dictionary that maps PostgreSQL OIDs and Kotlin classes to their corresponding [TypeCodec]s.
 * 
 * Provides methods for looking up codecs for parameters and results mapping, as well as 
 * registering custom codecs. Instances are immutable.
 */
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
            register(BitCodec)
            register(VarbitCodec)

            return CodecDictionary(oidMap, classMap, codecToOidMap, registeredCodecsList)
        }
    }

    /**
     * Retrieves a [TypeCodec] suitable for the given PostgreSQL OID.
     *
     * @param oid the Object Identifier of the PostgreSQL type.
     * @return the corresponding [TypeCodec] or null if no codec is registered for this OID.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getCodecByOid(oid: Int): TypeCodec<T>? {
        return codecsByOid[oid] as TypeCodec<T>?
    }

    /**
     * Retrieves a default [TypeCodec] for the given Kotlin class.
     *
     * @param kClass the Kotlin class to find a codec for.
     * @return the corresponding [TypeCodec] or null if no default codec is registered for this class.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getCodecByClass(kClass: KClass<T>): TypeCodec<T>? {
        return codecsByClass[kClass] as TypeCodec<T>?
    }

    /**
     * Retrieves the mapped OID for a specific [TypeCodec].
     *
     * @param codec the codec to look up.
     * @return the OID associated with the codec or null if it cannot be determined.
     */
    fun getOidForCodec(codec: TypeCodec<*>): Int? {
        return codecToOid[codec]
    }

    /**
     * Creates a new [CodecDictionary] by registering an additional [TypeCodec].
     * 
     * @param codec the new codec to register.
     * @param dictionary the [TypeDictionary] used to resolve the type OID if not explicitly provided by the codec.
     * @return a new instance of [CodecDictionary] containing the newly registered codec.
     */
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

    /**
     * Rebuilds the dictionary with new dynamically resolved types from the database.
     * 
     * @param newTypes a map of newly discovered PostgreSQL types by OID.
     * @param registry the [TypeRegistry] used for creating dynamic container codecs.
     * @return a new updated instance of [CodecDictionary].
     */
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
