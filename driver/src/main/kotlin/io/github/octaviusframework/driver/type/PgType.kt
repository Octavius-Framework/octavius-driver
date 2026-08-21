package io.github.octaviusframework.driver.type

/**
 * Represents a PostgreSQL data type.
 *
 * This sealed class hierarchy maps to PostgreSQL's internal type system,
 * providing a structured representation of types, including their Object Identifier (OID),
 * name, and the schema they belong to.
 *
 * @property oid The Object Identifier (OID) of the type.
 * @property name The name of the type.
 * @property schema The schema in which the type is defined.
 */
sealed class PgType(
    open val oid: Int,
    open val name: String,
    open val schema: String,
) {
    /**
     * Represents a standard PostgreSQL base type (e.g., int4, text, varchar).
     */
    data class Base(
        override val oid: Int,
        override val name: String,
        override val schema: String
    ) : PgType(oid, name, schema)

    /**
     * Represents a PostgreSQL array type.
     *
     * @property elementOid The OID of the elements contained in the array.
     */
    data class Array(
        override val oid: Int,
        override val name: String,
        override val schema: String,
        val elementOid: Int
    ) : PgType(oid, name, schema)

    /**
     * Represents a PostgreSQL range type.
     *
     * @property subtypeOid The OID of the subtype (the type of the bounds of the range).
     */
    data class Range(
        override val oid: Int,
        override val name: String,
        override val schema: String,
        val subtypeOid: Int
    ) : PgType(oid, name, schema)

    /**
     * Represents a PostgreSQL composite type (row type).
     *
     * Every relation has one of these - a table, a view, a materialized view - carrying that relation's columns
     * under the relation's own name, and [relationOid] is what ties the two together.
     *
     * @property attributes A map of attribute names to their respective OIDs, preserving declaration order.
     * @property relationOid The OID of the relation this type describes (`pg_class.oid`), which for a type declared
     *   with `CREATE TYPE ... AS (...)` is the entry PostgreSQL creates to hold its attributes.
     * @property attributeNumbers The attribute number of each attribute, in the same order as [attributes]. Not the
     *   index plus one: a dropped column keeps its number for good, leaving a hole that everything declared after it
     *   sits behind.
     */
    data class Composite(
        override val oid: Int,
        override val name: String,
        override val schema: String,
        val attributes: LinkedHashMap<String, Int>,
        val relationOid: Int = 0,
        val attributeNumbers: List<Int> = emptyList()
    ) : PgType(oid, name, schema) {
        /**
         * A list of the OIDs of the attributes in the composite type, in declaration order.
         */
        val attributeOids: List<Int> = attributes.values.toList()

        /**
         * A list of the names of the attributes in the composite type, in declaration order.
         */
        val attributeNames: List<String> = attributes.keys.toList()

        /**
         * A mapping from attribute name to its zero-based index in the composite type.
         */
        val nameToIndex: Map<String, Int> = run {
            val map = HashMap<String, Int>()
            attributes.keys.forEachIndexed { index, name -> map[name] = index }
            map
        }

        /**
         * Returns the name of the attribute carrying [attributeNumber], or `null` when this type has no attribute
         * under that number - a dropped column's, a system column's, or any number at all for a type loaded
         * without them.
         *
         * @param attributeNumber the attribute number to look up, as PostgreSQL numbers it.
         */
        fun attributeNameByNumber(attributeNumber: Int): String? {
            val index = attributeNumbers.indexOf(attributeNumber)
            return if (index == -1) null else attributeNames[index]
        }
    }

    /**
     * Represents a PostgreSQL domain type.
     *
     * @property baseTypeOid The OID of the underlying base type of the domain.
     */
    data class Domain(
        override val oid: Int,
        override val name: String,
        override val schema: String,
        val baseTypeOid: Int
    ) : PgType(oid, name, schema)

    /**
     * Represents a PostgreSQL enum type.
     *
     * @property values A list of the string values defined for the enum.
     */
    data class Enum(
        override val oid: Int,
        override val name: String,
        override val schema: String,
        val values: List<String>
    ) : PgType(oid, name, schema)

    /**
     * Represents a PostgreSQL multirange type.
     *
     * @property rangeOid The OID of the underlying range type.
     */
    data class Multirange(
        override val oid: Int,
        override val name: String,
        override val schema: String,
        val rangeOid: Int
    ) : PgType(oid, name, schema)

    /**
     * Represents the special PostgreSQL `record` pseudo-type.
     */
    data object Record : PgType(2249, "record", "pg_catalog")

    /**
     * Represents the special PostgreSQL `void` pseudo-type.
     */
    data object Void : PgType(2278, "void", "pg_catalog")
}

