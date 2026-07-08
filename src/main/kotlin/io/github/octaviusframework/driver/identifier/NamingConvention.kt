package io.github.octaviusframework.driver.identifier

/**
 * Supported naming conventions for automatic string conversion.
 *
 * These conventions are primarily used during:
 * 1. Mapping between Kotlin property names (`camelCase`) and PostgreSQL column names (`snake_case`).
 * 2. Mapping between Kotlin Enum constants and their string representation in PostgreSQL.
 * 3. Automatic generation of PostgreSQL type names from Kotlin class names.
 */
enum class CaseConvention {
    SNAKE_CASE_UPPER,
    SNAKE_CASE_LOWER,
    PASCAL_CASE,
    CAMEL_CASE,
}

/**
 * Utility for converting strings between different naming conventions.
 */
object CaseConverter {
    fun convert(value: String, from: CaseConvention, to: CaseConvention): String {
        if (from == to || value.isEmpty()) {
            return value
        }

        val words = splitToWords(value, from)
        return joinWords(words, to)
    }

    private fun splitToWords(value: String, convention: CaseConvention): List<String> {
        return when (convention) {
            CaseConvention.SNAKE_CASE_LOWER,
            CaseConvention.SNAKE_CASE_UPPER ->
                value.split('_').filter { it.isNotEmpty() }

            CaseConvention.PASCAL_CASE,
            CaseConvention.CAMEL_CASE -> {
                val words = mutableListOf<String>()
                var currentWord = StringBuilder()

                for (i in value.indices) {
                    val c = value[i]
                    val prev = if (i > 0) value[i - 1] else null
                    val next = if (i < value.length - 1) value[i + 1] else null

                    val isNewWord = if (prev != null) {
                        when {
                            (prev.isLowerCase() || prev.isDigit()) && c.isUpperCase() -> true
                            prev.isUpperCase() && c.isUpperCase() && next?.isLowerCase() == true -> true
                            prev.isDigit() && c.isLetter() -> true
                            else -> false
                        }
                    } else false

                    if (isNewWord) {
                        words.add(currentWord.toString())
                        currentWord = StringBuilder()
                    }
                    currentWord.append(c)
                }

                if (currentWord.isNotEmpty()) {
                    words.add(currentWord.toString())
                }
                words
            }
        }
    }

    private fun joinWords(words: List<String>, convention: CaseConvention): String {
        return when (convention) {
            CaseConvention.SNAKE_CASE_UPPER ->
                words.joinToString("_") { it.uppercase() }

            CaseConvention.SNAKE_CASE_LOWER ->
                words.joinToString("_") { it.lowercase() }

            CaseConvention.PASCAL_CASE ->
                words.joinToString("") { word ->
                    word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }

            CaseConvention.CAMEL_CASE ->
                words.mapIndexed { index, word ->
                    val lower = word.lowercase()
                    if (index == 0) {
                        lower
                    } else {
                        lower.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                }.joinToString("")
        }
    }
}

// --- Extension Functions ---

fun String.toSnakeCase(): String {
    return CaseConverter.convert(this, CaseConvention.CAMEL_CASE, CaseConvention.SNAKE_CASE_LOWER)
}

fun String.toCamelCase(): String {
    return CaseConverter.convert(this, CaseConvention.SNAKE_CASE_LOWER, CaseConvention.CAMEL_CASE)
}

fun String.toPascalCase(): String {
    return CaseConverter.convert(this, CaseConvention.SNAKE_CASE_LOWER, CaseConvention.PASCAL_CASE)
}
