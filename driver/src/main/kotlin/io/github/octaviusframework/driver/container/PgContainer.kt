package io.github.octaviusframework.driver.container

/**
 * Base interface for all PostgreSQL container types (e.g., arrays, composites, ranges, multiranges, records).
 *
 * A container is what the **codec** layer produces, one step below the converters: everything it holds is
 * what the codec bound to that element's or attribute's OID decoded, with no converter having run over it.
 * A nested composite is another [PgComposite], an array is a [PgArray], an enum is its label as a `String`.
 * Serialization takes the same route in reverse - a field goes straight to the codec for its OID - so a
 * container built by hand has to be filled with values of the classes those codecs encode.
 *
 * Converting what is inside is a converter's job, and a converter is where the context that does it is
 * handed to you. Outside one, read a container for what it says about shape - an array's dimensions, a
 * composite's attribute names and OIDs, a record's positional fields - and read the values through
 * [Row.get][io.github.octaviusframework.driver.row.Row.get], which runs the whole chain, nested values
 * included.
 */
interface PgContainer {
    /**
     * OID of the container's own type — the array type rather than its element type, the range type
     * rather than its subtype.
     *
     * A container carries its type with it, which is what lets it be sent as a parameter without the
     * driver having to resolve a type name for it.
     */
    val containerOid: Int
}
