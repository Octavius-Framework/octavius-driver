package io.github.octaviusframework.types

sealed class PgType(
    open val oid: UInt,
    open val name: String,
    open val schema: String,
    open val arrayOid: UInt = 0u
) {
    data class Base(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        override val arrayOid: UInt = 0u
    ) : PgType(oid, name, schema, arrayOid)

    data class Array(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        val elementOid: UInt
    ) : PgType(oid, name, schema, 0u)

    data class Range(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        override val arrayOid: UInt = 0u,
        val subtypeOid: UInt
    ) : PgType(oid, name, schema, arrayOid)

    data class Composite(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        override val arrayOid: UInt = 0u,
        val attributes: LinkedHashMap<String, UInt>
    ) : PgType(oid, name, schema, arrayOid)

    data class Domain(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        override val arrayOid: UInt = 0u,
        val baseTypeOid: UInt
    ) : PgType(oid, name, schema, arrayOid)

    data class Enum(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        override val arrayOid: UInt = 0u,
        val values: List<String>
    ) : PgType(oid, name, schema, arrayOid)

    data class Multirange(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        override val arrayOid: UInt = 0u,
        val rangeOid: UInt
    ) : PgType(oid, name, schema, arrayOid)

    data class Record(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        override val arrayOid: UInt = 0u
    ) : PgType(oid, name, schema, arrayOid)

    data class Void(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        override val arrayOid: UInt = 0u
    ) : PgType(oid, name, schema, arrayOid)
}
