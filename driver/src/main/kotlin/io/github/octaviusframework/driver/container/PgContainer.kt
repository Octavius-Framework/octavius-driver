package io.github.octaviusframework.driver.container

import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason

/**
 * A `CONVERSION_ERROR` naming where in the container the value was.
 *
 * A container's accessors are the leaves of the read chain, so a failure in one is where a `path` ends: the
 * layers above append their own segment as it unwinds, and this is what puts the last one on. It matters most
 * inside a hand-written converter, where the chain is what invoked you and `payload -> amount` is a great deal
 * more use than `payload`.
 *
 * Only a value that **is there** gets a segment. An index out of bounds or a name the container does not have
 * is not a location, and saying it twice - once in the message, once as a path - would read as though the
 * driver had found something there.
 *
 * @param segment What to call the position: an attribute's name, or `[i]` where it has only a number.
 * @param details The message.
 */
@PublishedApi
internal fun conversionErrorAt(segment: String, details: String): MappingException =
    MappingException(MappingExceptionReason.CONVERSION_ERROR, details = details).apply { path.add(segment) }

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
