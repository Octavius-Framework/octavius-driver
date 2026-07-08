package io.github.octaviusframework.driver.identifier

/**
 * Represents a qualified PostgreSQL name (schema + object name).
 * Handles quoting correctly even if names contain dots.
 */
data class QualifiedName(
    val schema: String,
    val name: String,
    val isArray: Boolean = false
) {
    override fun toString(): String {
        val base = if (schema.isBlank()) name else "$schema.$name"
        return if (isArray) "$base[]" else base
    }

    /**
     * Returns a SQL-safe representation. 
     * Respects existing quotes and adds new ones only if necessary (e.g. dots in name).
     */
    fun quote(): String {
        val quotedBase = if (schema.isBlank()) {
            name.quoteAsPgIdentifier()
        } else {
            "${schema.quoteAsPgIdentifier()}.${name.quoteAsPgIdentifier()}"
        }
        return if (isArray) "$quotedBase[]" else quotedBase
    }

    fun asArray(): QualifiedName = copy(isArray = true)
}
