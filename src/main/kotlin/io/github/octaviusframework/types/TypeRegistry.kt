package io.github.octaviusframework.types

data class PgType(
    val oid: Int,
    val name: String,
    val relationId: Int, // OID tabeli (jeśli to kompozyt)
    val elementId: Int, // OID elementu (jeśli to tablica)
    val arrayId: Int // OID typu tablicowego dla tego typu
)

class TypeRegistry {
    val types = mutableMapOf<Int, PgType>()
    val decoders = mutableMapOf<Int, PgDecoder<*>>()

    init {
        // Rejestracja wbudowanych, niezmiennych OID-ów niezbędnych do samego odczytu pg_type
        decoders[23] = IntDecoder     // int4
        decoders[26] = IntDecoder     // oid (wewnętrznie to int32)
        decoders[25] = StringDecoder  // text
        decoders[19] = StringDecoder  // name (wewnętrznie 64-bajtowy C-String, ale traktujemy jako text)
    }
    
    fun getDecoder(oid: Int): PgDecoder<*>? = decoders[oid]
}
