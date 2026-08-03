package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.codec.standard.*
import kotlin.reflect.KClass

class CodecDictionary(
    val codecsByOid: IntObjectMap<TypeCodec<*>>,
    val codecsByClass: Map<KClass<*>, TypeCodec<*>>,
    val codecToOid: Map<TypeCodec<*>, Int>,
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
}
