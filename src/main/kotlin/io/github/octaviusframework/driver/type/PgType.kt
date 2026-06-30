package io.github.octaviusframework.driver.type

sealed class PgType(
    open val oid: UInt,
    open val name: String,
    open val schema: String,
) {
    data class Base(
        override val oid: UInt,
        override val name: String,
        override val schema: String
    ) : PgType(oid, name, schema)

    data class Array(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        val elementOid: UInt
    ) : PgType(oid, name, schema)

    data class Range(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        val subtypeOid: UInt
    ) : PgType(oid, name, schema)

    data class Composite(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        val attributes: LinkedHashMap<String, UInt>
    ) : PgType(oid, name, schema) {
        val attributeOids: List<UInt> by lazy { attributes.values.toList() }
        val nameToIndex: Map<String, Int> by lazy {
            val map = HashMap<String, Int>()
            attributes.keys.forEachIndexed { index, name -> map[name] = index }
            map
        }
    }

    data class Domain(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        val baseTypeOid: UInt
    ) : PgType(oid, name, schema)

    data class Enum(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        val values: List<String>
    ) : PgType(oid, name, schema)

    data class Multirange(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
        val rangeOid: UInt
    ) : PgType(oid, name, schema)

    data class Record(
        override val oid: UInt,
        override val name: String,
        override val schema: String,
    ) : PgType(oid, name, schema)

    data class Void(
        override val oid: UInt,
        override val name: String,
        override val schema: String
    ) : PgType(oid, name, schema)
}
