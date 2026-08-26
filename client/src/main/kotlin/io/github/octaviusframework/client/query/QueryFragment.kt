package io.github.octaviusframework.client.query

import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason

/**
 * A piece of SQL and the parameters it names, kept together so that neither can be dropped on the way.
 *
 * The point of it is a `WHERE` assembled at runtime. Written as plain strings, a filter that is sometimes there
 * and sometimes not means keeping a condition list and a parameter map in step by hand, and the failure when
 * they drift is a parameter the query names and nobody supplied. Here the two travel together and [join] keeps
 * them together.
 *
 * ```kotlin
 * val filter = listOfNotNull(
 *     name?.let { "cognomen ILIKE @name" withParam ("name" to "%$it%") },
 *     minRank?.let { "rank_order >= @minRank" withParam ("minRank" to it) },
 *     province?.let { "home_province = @province" withParam ("province" to it) }
 * ).join(" AND ")
 *
 * val senators = db.select("*").from("senate")
 *     .where(filter.sql)
 *     .fetchObjects<Senator>(filter.params)
 * ```
 *
 * The two halves are handed over separately and on purpose. A builder never carries parameters - that is what
 * lets [RunnableQuery.toSql] be embedded in a larger statement without leaving values behind - so `filter.sql`
 * goes to the clause and `filter.params` to the terminal. Writing both is what keeps the pair visible.
 *
 * @property sql The SQL text of this fragment. Empty where the fragment contributes no condition.
 * @property params The parameters the text names, by name and without the leading `@`.
 */
class QueryFragment(val sql: String, val params: Map<String, Any?> = emptyMap()) {

    /** Whether this fragment contributes nothing, which is how a clause knows to leave itself out. */
    val isEmpty: Boolean get() = sql.isBlank()

    override fun toString(): String = sql
}

/**
 * Pairs this SQL text with one parameter it names.
 *
 * @param param The parameter, by name without the leading `@`, and its value.
 * @return The two, kept together.
 */
infix fun String.withParam(param: Pair<String, Any?>): QueryFragment = QueryFragment(this, mapOf(param))

/**
 * Pairs this SQL text with the parameters it names.
 *
 * @param params The parameters, by name without the leading `@`.
 * @return The two, kept together.
 */
infix fun String.withParams(params: Map<String, Any?>): QueryFragment = QueryFragment(this, params)

/**
 * Joins the fragments that carry anything, merging their parameters.
 *
 * Empty fragments are dropped rather than producing a dangling separator, so a list built with `listOfNotNull`
 * and a few `null`s in it joins to exactly the conditions that survived. Joining nothing gives an empty
 * fragment, which a clause then leaves out entirely - and [prefix] and [postfix] are dropped with it, so an
 * empty filter cannot render a bare `WHERE ()`.
 *
 * Each fragment is parenthesised, which is not cosmetic: `"a = 1 OR b = 2"` joined to `"c = 3"` with `" AND "`
 * means `(a = 1 OR b = 2) AND (c = 3)`, and without the parentheses `AND` would bind tighter and quietly
 * change which rows come back. Turn it off with [addParenthesis] only where every fragment is a single term.
 *
 * @param separator What to put between the fragments - `" AND "` and `" OR "` being the two that come up.
 * @param prefix Put in front of the whole thing where it renders at all, `"WHERE "` for a hand-written query.
 * The builders supply their own keyword, so leave it empty there.
 * @param postfix Put after the whole thing where it renders at all.
 * @param addParenthesis Whether to wrap each fragment. Leave it on unless you know every one is a single term.
 * @return One fragment carrying every surviving condition and every parameter they name.
 * @throws InvalidOperationException `INVALID_ARGUMENT` where two fragments name the same parameter with
 * different values. One would replace the other, and which one would depend on the order the filters were
 * listed in.
 */
fun List<QueryFragment>.join(
    separator: String,
    prefix: String = "",
    postfix: String = "",
    addParenthesis: Boolean = true
): QueryFragment {
    val present = filterNot { it.isEmpty }
    if (present.isEmpty()) return QueryFragment("")

    val params = LinkedHashMap<String, Any?>()
    for (fragment in present) {
        for ((name, value) in fragment.params) {
            if (params.containsKey(name) && params[name] != value) {
                throw InvalidOperationException(
                    InvalidOperationExceptionReason.INVALID_ARGUMENT,
                    details = "Two fragments being joined both name the parameter '$name' with different " +
                        "values; one would replace the other. Rename one of them."
                )
            }
            params[name] = value
        }
    }

    val sql = present.joinToString(separator, prefix, postfix) { if (addParenthesis) "(${it.sql})" else it.sql }
    return QueryFragment(sql, params)
}
