package io.github.octaviusframework.identifier

/**
 * Supported naming conventions for automatic string conversion.
 *
 * These conventions are primarily used during:
 * 1. Mapping between Kotlin property names (`camelCase`) and PostgreSQL column names (`snake_case`).
 * 2. Mapping between Kotlin Enum constants and their string representation in PostgreSQL.
 * 3. Automatic generation of PostgreSQL type names from Kotlin class names.
 */
enum class CaseConvention {
    /**
     * e.g., `SOME_VARIABLE_NAME`
     */
    SNAKE_CASE_UPPER,

    /**
     * e.g., `some_variable_name`
     */
    SNAKE_CASE_LOWER,

    /**
     * e.g., `SomeVariableName`
     */
    PASCAL_CASE,

    /**
     * e.g., `someVariableName`
     */
    CAMEL_CASE,
}
