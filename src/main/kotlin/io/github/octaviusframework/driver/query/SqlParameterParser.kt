package io.github.octaviusframework.driver.query

import java.util.concurrent.ConcurrentHashMap

data class ParsedSql(val originalSql: String, val transformedSql: String, val paramNames: List<String>)

internal data class ParsedParameter(val name: String, val startIndex: Int, val endIndex: Int)

object SqlParameterParser {
    private val cache = ConcurrentHashMap<String, ParsedSql>()
    private const val MAX_CACHE_SIZE = 10_000

    private const val PARAMETER_SEPARATORS = "\"':&,;()|=+-*%/\\<>^[]@~!#`?"
    private val separatorIndex = BooleanArray(128).apply {
        PARAMETER_SEPARATORS.forEach { this[it.code] = true }
    }

    private fun isParameterSeparator(c: Char): Boolean {
        return (c.code < 128 && separatorIndex[c.code]) || c.isWhitespace()
    }

    fun parse(sql: String): ParsedSql {
        return cache[sql] ?: run {
            if (cache.size >= MAX_CACHE_SIZE) {
                cache.clear()
            }
            val parsed = doParse(sql)
            cache.putIfAbsent(sql, parsed) ?: parsed
        }
    }

    private fun doParse(sql: String): ParsedSql {
        val foundParameters = mutableListOf<ParsedParameter>()
        var i = 0

        while (i < sql.length) {
            val skipIndex = findConstructEnd(sql, i)
            if (skipIndex > i) {
                i = skipIndex + 1
                continue
            }

            if (sql[i] == '@') {
                i = processAt(sql, i, foundParameters)
            }
            i++
        }

        if (foundParameters.isEmpty()) {
            return ParsedSql(sql, sql, emptyList())
        }

        val uniqueParamNames = mutableListOf<String>()
        val paramIndices = mutableMapOf<String, Int>()

        val transformedSql = buildString(sql.length + 32) {
            var lastIndex = 0
            for (parsedParam in foundParameters) {
                val paramName = parsedParam.name
                val index = paramIndices.getOrPut(paramName) {
                    uniqueParamNames.add(paramName)
                    uniqueParamNames.size
                }

                append(sql.substring(lastIndex, parsedParam.startIndex))
                append("$").append(index)
                lastIndex = parsedParam.endIndex + 1
            }
            append(sql.substring(lastIndex, sql.length))
        }

        return ParsedSql(sql, transformedSql, uniqueParamNames)
    }

    private fun findConstructEnd(sql: String, i: Int): Int {
        return when (sql[i]) {
            '\'' -> processSingleQuote(sql, i)
            '"' -> skipUntil(sql, i, '"')
            '-' -> if (i + 1 < sql.length && sql[i + 1] == '-') skipUntil(sql, i, '\n') else i
            '/' -> if (i + 1 < sql.length && sql[i + 1] == '*') skipComment(sql, i) else i
            '$' -> {
                val end = findDollarQuoteEnd(sql, i)
                if (end != -1) end else i
            }
            else -> i
        }
    }

    private fun processSingleQuote(sql: String, index: Int): Int {
        return if (index > 0 && (sql[index - 1] == 'E' || sql[index - 1] == 'e')) {
            skipBackslashEscapedLiteral(sql, index)
        } else {
            skipUntil(sql, index, '\'')
        }
    }

    private fun processAt(
        sql: String,
        index: Int,
        foundParameters: MutableList<ParsedParameter>
    ): Int {
        var j = index + 1
        while (j < sql.length && !isParameterSeparator(sql[j])) {
            j++
        }

        if (j - index > 1) {
            val paramName = sql.substring(index + 1, j)
            foundParameters.add(ParsedParameter(paramName, index, j - 1))
            return j - 1
        }

        return index
    }

    private fun findDollarQuoteEnd(sql: String, start: Int): Int {
        if (start + 1 >= sql.length) return -1

        var tagEnd = start
        while (tagEnd + 1 < sql.length && sql[tagEnd + 1] != '$') {
            val char = sql[tagEnd + 1]
            if (!isValidTagCharacter(char, isFirstChar = tagEnd == start)) {
                return -1
            }
            tagEnd++
        }

        if (tagEnd + 1 >= sql.length || sql[tagEnd + 1] != '$') {
            return -1
        }

        val tagLength = (tagEnd + 1) - start + 1

        var searchPos = tagEnd + 2
        while (searchPos + tagLength <= sql.length) {
            if (sql.regionMatches(searchPos, sql, start, tagLength)) {
                return searchPos + tagLength - 1
            }
            searchPos++
        }

        return -1
    }

    private fun isValidTagCharacter(char: Char, isFirstChar: Boolean): Boolean {
        return when {
            char.isLetter() || char == '_' -> true
            !isFirstChar && char in '0'..'9' -> true
            else -> false
        }
    }

    private fun skipBackslashEscapedLiteral(sql: String, start: Int): Int {
        var i = start + 1
        while (i < sql.length) {
            if (sql[i] == '\\') {
                i++
            } else if (sql[i] == '\'') {
                if (i + 1 < sql.length && sql[i + 1] == '\'') {
                    i++
                } else {
                    return i
                }
            }
            i++
        }
        return i
    }

    private fun skipUntil(sql: String, start: Int, endChar: Char): Int {
        val index = sql.indexOf(endChar, start + 1)
        return if (index == -1) sql.length else index
    }

    private fun skipComment(sql: String, start: Int): Int {
        var i = start + 2
        var depth = 1
        while (i < sql.length && depth > 0) {
            if (i + 1 < sql.length) {
                if (sql[i] == '/' && sql[i + 1] == '*') {
                    depth++
                    i++
                } else if (sql[i] == '*' && sql[i + 1] == '/') {
                    depth--
                    i++
                }
            }
            i++
        }
        return i - 1
    }
}
