package io.github.octaviusframework.driver.container

/**
 * Base interface for all PostgreSQL container types (e.g., arrays, composites, ranges, multiranges, records).
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
