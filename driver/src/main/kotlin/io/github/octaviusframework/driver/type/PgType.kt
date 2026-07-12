package io.github.octaviusframework.driver.type

sealed class PgType(
    open val oid: Int,
    open val name: String,
    open val schema: String,
) {
    data class Base(
        override val oid: Int,
        override val name: String,
        override val schema: String
    ) : PgType(oid, name, schema)

    data class Array(
        override val oid: Int,
        override val name: String,
        override val schema: String,
        val elementOid: Int
    ) : PgType(oid, name, schema)

    data class Range(
        override val oid: Int,
        override val name: String,
        override val schema: String,
        val subtypeOid: Int
    ) : PgType(oid, name, schema)

    data class Composite(
        override val oid: Int,
        override val name: String,
        override val schema: String,
        val attributes: LinkedHashMap<String, Int>
    ) : PgType(oid, name, schema) {
        val attributeOids: List<Int> by lazy { attributes.values.toList() }

        val attributeNames: List<String> by lazy { attributes.keys.toList() }
        val nameToIndex: Map<String, Int> by lazy {
            val map = HashMap<String, Int>()
            attributes.keys.forEachIndexed { index, name -> map[name] = index }
            map
        }
    }

    data class Domain(
        override val oid: Int,
        override val name: String,
        override val schema: String,
        val baseTypeOid: Int
    ) : PgType(oid, name, schema)

    data class Enum(
        override val oid: Int,
        override val name: String,
        override val schema: String,
        val values: List<String>
    ) : PgType(oid, name, schema)

    data class Multirange(
        override val oid: Int,
        override val name: String,
        override val schema: String,
        val rangeOid: Int
    ) : PgType(oid, name, schema)

    data object Record : PgType(2249, "record", "pg_catalog")

    data object Void : PgType(2278, "void", "pg_catalog")
}

