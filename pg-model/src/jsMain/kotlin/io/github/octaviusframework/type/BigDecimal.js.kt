package io.github.octaviusframework.type

/**
 * On JS, [BigDecimal] holds the decimal's text.
 *
 * `Number` is a 64-bit float, so the platform has no type that can hold what PostgreSQL's `numeric` held.
 * The digits are kept as they arrived, which is what a frontend needs to display a value and send it back
 * unchanged; arithmetic on it belongs to whichever decimal library the application already has.
 *
 * @property value The decimal in plain notation, as the wire carried it.
 */
actual class BigDecimal actual constructor(val value: String) {

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class.js != other::class.js) return false

        other as BigDecimal

        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()
}
