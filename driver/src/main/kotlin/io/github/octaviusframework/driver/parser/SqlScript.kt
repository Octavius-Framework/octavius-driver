package io.github.octaviusframework.driver.parser

/**
 * One statement out of a script, and where it stood in the text it was cut from.
 *
 * @property sql The statement itself, without the `;` that ended it and without the whitespace around it.
 * Comments that fell inside it are kept - they cost nothing to send and removing them would move every
 * position after them.
 * @property offset Index in the original script of this statement's first character. The server reports an
 * error position against what it was sent, so a statement sent on its own has its errors numbered from its
 * own beginning; adding this puts that number back where the author can find it.
 * @property keyword The statement's first word, upper-cased, with whatever comments stand in front of it
 * stepped over - `COMMIT` for `-- why we do this
COMMIT`. `null` where the statement opens with something
 * that is not a word: a quote, a dollar-quoted body, a parenthesis. Upper-cased because SQL keywords are
 * case-insensitive and comparing them as written is a bug waiting to happen.
 */
data class SqlStatement(val sql: String, val offset: Int, val keyword: String?)

/**
 * Cuts a script into the statements it is made of.
 *
 * PostgreSQL takes a whole script in one message and runs it inside an implicit transaction, which is what
 * is wanted right up until one of the statements is `CREATE INDEX CONCURRENTLY`, `VACUUM`, or anything else
 * the server refuses inside a transaction block. Those have to arrive one message at a time, and sending
 * them one at a time means knowing where each one ends.
 *
 * Nothing here parses SQL. A statement is the text between separators; whether the server will accept it is
 * the server's answer to give.
 */
object SqlScript {

    /**
     * Splits [sql] on the `;` that separate statements, and on no other `;`.
     *
     * A `;` does not separate anything when it stands inside `'...'`, inside `E'...'` where a backslash
     * escapes what follows it, inside `"..."`, inside `$tag$...$tag$` - which is where a PL/pgSQL body keeps
     * most of its semicolons - inside a `--` comment, inside a `/* */` comment nested to any depth, or
     * inside parentheses, where `CREATE RULE ... DO (a; b)` puts one legitimately.
     *
     * @param sql The script, one statement or many.
     * @return The statements in the order they appear. A stretch holding nothing but whitespace and comments
     * is not a statement and is not returned, so a trailing `;` adds nothing and a script of comments alone
     * comes back empty.
     * @throws io.github.octaviusframework.driver.exception.StatementException `UNCLOSED_TOKEN` where a
     * quote, dollar quote or block comment opens and never closes. The whole point of the walk is telling
     * which `;` are separators, and past an unterminated construct none of them can be told apart.
     */
    fun split(sql: String): List<SqlStatement> {
        val statements = mutableListOf<SqlStatement>()

        var i = 0
        var start = 0
        var parenDepth = 0
        // Where the first thing that makes this a statement rather than a gap between statements began.
        // Whitespace and comments are neither, so it stays -1 across them - which answers both "is there a
        // statement here at all" and "where does its first word start".
        var contentStart = -1

        while (i < sql.length) {
            val constructEnd = SqlScanner.findConstructEnd(sql, i)
            if (constructEnd > i) {
                if (!SqlScanner.isCommentStart(sql, i) && contentStart < 0) contentStart = i
                i = constructEnd + 1
                continue
            }

            val c = sql[i]
            if (c == ';' && parenDepth == 0) {
                if (contentStart >= 0) addStatement(sql, start, i, contentStart, statements)
                start = i + 1
                contentStart = -1
            } else {
                if (c == '(') {
                    parenDepth++
                } else if (c == ')' && parenDepth > 0) {
                    // Clamped rather than allowed to go negative: a script with an unbalanced `)` in it is
                    // the server's to refuse, and a negative depth here would hide every separator after it.
                    parenDepth--
                }
                if (!c.isWhitespace() && contentStart < 0) contentStart = i
            }
            i++
        }

        if (contentStart >= 0) addStatement(sql, start, sql.length, contentStart, statements)

        return statements
    }

    /** Trims the whitespace off `sql[start until end]` and files what is left, offset and keyword and all. */
    private fun addStatement(
        sql: String,
        start: Int,
        end: Int,
        contentStart: Int,
        into: MutableList<SqlStatement>
    ) {
        var from = start
        var to = end
        while (from < to && sql[from].isWhitespace()) from++
        while (to > from && sql[to - 1].isWhitespace()) to--
        if (from >= to) return

        var wordEnd = contentStart
        while (wordEnd < to && sql[wordEnd].isLetter()) wordEnd++
        val keyword = if (wordEnd > contentStart) sql.substring(contentStart, wordEnd).uppercase() else null

        into.add(SqlStatement(sql.substring(from, to), from, keyword))
    }
}
