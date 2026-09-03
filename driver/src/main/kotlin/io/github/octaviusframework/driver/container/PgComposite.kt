package io.github.octaviusframework.driver.container

import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason
import io.github.octaviusframework.driver.type.PgType

/**
 * Represents a composite structure (e.g., row of a specific type) loaded from the database.
 *
 * @property type The composite type definition.
 * @property fields Array containing the values of the fields in this composite structure.
 */
class PgComposite internal constructor(
    val type: PgType.Composite,
    val fields: Array<Any?>
) : PgContainer {
    override val containerOid: Int get() = type.oid

    /** The names of this composite's attributes, in declaration order. */
    val attributeNames: List<String>
        get() = type.attributeNames

    /**
     * Returns the attribute at [index], cast to [T].
     *
     * Also written `composite[0]`, where the expected type is what fixes [T] - `val id: Int = composite[0]`.
     * The counterpart of `set`.
     *
     * @param T The expected attribute type. Declare it nullable to accept a SQL `NULL`.
     * @param index Zero-based index in declaration order.
     * @return The attribute value.
     * @throws MappingException `COLUMN_NOT_FOUND` if [index] is outside this composite's attributes,
     *   `CONVERSION_ERROR` if the value is `null` under a non-nullable [T], or is not a [T].
     */
    inline operator fun <reified T> get(index: Int): T {
        if (index !in fields.indices) throw MappingException(
            MappingExceptionReason.COLUMN_NOT_FOUND,
            details = "Attribute index out of bounds: $index in composite '${type.name}' (${fields.size} attributes)"
        )

        val value = fields[index]

        if (value is T) {
            return value
        }

        if (value == null) {
            throw conversionErrorAt(
                type.attributeNames[index],
                "Expected non-null value for attribute at index $index, got null"
            )
        }

        throw conversionErrorAt(
            type.attributeNames[index],
            "Expected ${T::class.simpleName}, got ${value::class.simpleName}"
        )
    }

    /**
     * Resolves an attribute name to its zero-based index.
     *
     * @param columnName The attribute name, as declared in the database.
     * @return The zero-based index.
     * @throws MappingException `COLUMN_NOT_FOUND` if this composite has no such attribute.
     */
    fun getColumnIndex(columnName: String): Int {
        val index = type.nameToIndex[columnName] ?: -1
        if (index == -1) throw MappingException(
            MappingExceptionReason.COLUMN_NOT_FOUND,
            details = "Attribute: $columnName"
        )
        return index
    }

    /**
     * Sets the attribute at [index]. The value is converted when the composite is sent, not here, so
     * nothing is checked against the attribute's type at this point.
     *
     * @param index Zero-based index in declaration order.
     * @param newValue The value to store.
     * @throws MappingException `COLUMN_NOT_FOUND` if [index] is outside this composite's attributes.
     */
    operator fun set(index: Int, newValue: Any?) {
        if (index !in fields.indices) throw MappingException(
            MappingExceptionReason.COLUMN_NOT_FOUND,
            details = "Attribute index out of bounds: $index in composite '${type.name}' (${fields.size} attributes)"
        )
        fields[index] = newValue
    }

    /**
     * Sets the attribute named [columnName].
     *
     * @param columnName The attribute name, as declared in the database.
     * @param newValue The value to store.
     * @throws MappingException `COLUMN_NOT_FOUND` if this composite has no such attribute.
     */
    operator fun set(columnName: String, newValue: Any?) {
        set(getColumnIndex(columnName), newValue)
    }

    /**
     * Returns the attribute named [name], cast to [T].
     *
     * Also written `composite["cognomen"]`, where the expected type is what fixes [T] - and its
     * nullability with it, as on [Row][io.github.octaviusframework.driver.row.Row]:
     * `val name: String = composite["cognomen"]` refuses a `NULL`. The counterpart of `set`.
     *
     * @param T The expected attribute type. Declare it nullable to accept a SQL `NULL`.
     * @param name The attribute name, as declared in the database.
     * @return The attribute value.
     * @throws MappingException `COLUMN_NOT_FOUND` if there is no such attribute, `CONVERSION_ERROR` if
     *   the value is `null` under a non-nullable [T], or is not a [T].
     */
    inline operator fun <reified T> get(name: String): T {
        val index = type.nameToIndex[name] ?: -1
        if (index == -1) throw MappingException(
            MappingExceptionReason.COLUMN_NOT_FOUND,
            details = "Attribute '$name' in composite '${type.name}'"
        )
        return get<T>(index)
    }

    /**
     * Returns the PostgreSQL OID of the attribute at [index].
     *
     * @param index Zero-based index in declaration order.
     * @return The attribute's type OID.
     * @throws MappingException `COLUMN_NOT_FOUND` if [index] is outside this composite's attributes.
     */
    fun getAttributeOid(index: Int): Int {
        val attributeOids = type.attributeOids
        if (index !in attributeOids.indices) throw MappingException(
            MappingExceptionReason.COLUMN_NOT_FOUND,
            details = "Attribute index out of bounds: $index in composite '${type.name}' (${attributeOids.size} attributes)"
        )
        return attributeOids[index]
    }

    /**
     * Returns the PostgreSQL OID of the attribute named [name].
     *
     * @param name The attribute name, as declared in the database.
     * @return The attribute's type OID.
     * @throws MappingException `COLUMN_NOT_FOUND` if this composite has no such attribute.
     */
    fun getAttributeOid(name: String): Int {
        return getAttributeOid(getColumnIndex(name))
    }
}

