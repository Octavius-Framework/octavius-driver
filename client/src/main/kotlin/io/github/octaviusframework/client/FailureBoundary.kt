package io.github.octaviusframework.client

import io.github.octaviusframework.driver.exception.CodecException
import io.github.octaviusframework.driver.exception.InitializationException
import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.OctaviusException
import io.github.octaviusframework.driver.exception.StatementException
import io.github.octaviusframework.driver.exception.TypeException

/**
 * Runs [block] and hands back what it produced as a [DataResult], turning the failures an application is
 * expected to handle into [DataResult.Failure] and letting the ones that mean broken code go on being thrown.
 *
 * The result style is opt-in throughout, and nothing under it returns a `DataResult`: queries throw, the way
 * the driver throws, which is what keeps them usable from a `try`/`catch` and from a Spring `@Transactional`
 * without either knowing this module exists. Reach for the result style where a failure is a value you want to
 * carry - a fat client turning it into UI state is what it was written for.
 *
 * There are three doors into it, one per width, and this is the widest:
 *
 * - one query: [RunnableQuery.asResult][io.github.octaviusframework.client.query.RunnableQuery.asResult]
 * - a transaction: [transactionResult][io.github.octaviusframework.client.OctaviusClient.transactionResult]
 * - anything else - a `db.execute { }` block, a `RawQuery.execute()`, a whole call of your own: this
 *
 * ```kotlin
 * val senators: DataResult<List<Senator>> = dbResult {
 *     db.execute {
 *         createNativeQuery("SET LOCAL statement_timeout = 5000").execute()
 *         createNamedQuery("SELECT id, cognomen FROM senators WHERE province_id = @p")
 *             .fetchObjects<Senator>("p" to 7)
 *     }
 * }
 * ```
 *
 * **Not around the queries inside a transaction.** A `dbResult` there catches the failure, the block finishes
 * normally, and [OctaviusClient.transaction] commits over the very failure that was caught - the same trap
 * `runCatching` sets in the same place. [transactionResult][io.github.octaviusframework.client.OctaviusClient.transactionResult] is the door for that width, and it rolls back on a
 * returned failure rather than committing over one.
 *
 * @param block The work to run under the boundary.
 * @return What [block] produced, or the failure it raised.
 */
inline fun <T> dbResult(crossinline block: () -> T): DataResult<T> =
    try {
        DataResult.Success(block())
    } catch (e: OctaviusException) {
        if (isCallerBug(e)) throw e else DataResult.Failure(e)
    }

/**
 * Whether this failure says the calling code is wrong, rather than that the operation did not work out.
 *
 * The split is on the exception's type and nothing finer. Every one of these classes carries a `reason`
 * enum, and none of them is consulted here: those exist to say what happened in a log line, not to be
 * branched on, and a rule that read them would have to be rewritten every time the driver names a new one.
 *
 * The listed types are what the driver raises about the request itself - SQL the server would not parse,
 * a row that does not fit the class it was asked for, a type name the registry does not know, a value no
 * codec would encode, an operation the session's state does not allow. Every one of them is a defect that
 * is the same on every run, so a `DataResult.Failure` branch would only be a slower way of reaching a
 * stack trace.
 *
 * A `fetch*Strict` that found no row is thrown along with the rest, and that is the whole point of the
 * suffix: `Strict` states that exactly one row is there, so a run that finds none has falsified something
 * the calling code asserted. The same reading covers a non-nullable `T` over a `NULL`. Absence that is
 * expected has its own way of being said, and it is in the type rather than in a failure branch -
 * `fetchRow` over `fetchRowStrict`, `fetchField<String?>` over `fetchField<String>` - which return `null`
 * and raise nothing. Picking the strict form for a lookup that is allowed to miss is the bug being
 * reported.
 *
 * [InitializationException] is listed for a different reason: it is raised where no session could be
 * obtained at all - bad credentials, a server that refused the handshake, a pool that timed out handing
 * one over. That happens before the operation a result would describe, and it also happens in places that
 * have no result to put it in, such as [OctaviusClient.close]. Throwing it everywhere keeps one rule
 * instead of a rule with an exception; a caller that wants to retry a pool timeout still catches it.
 *
 * Anything not listed becomes a [DataResult.Failure], deliberately including
 * [UncategorizedDatabaseException][io.github.octaviusframework.driver.exception.UncategorizedDatabaseException]
 * and any type the driver gains later. A caller who reached for [dbResult] is already handling failures, so
 * an unrecognised one arriving there costs nothing; the same one thrown past a boundary that was asked to
 * catch it is the surprise worth avoiding.
 *
 * @param e The failure to classify.
 * @return `true` where the failure means broken code and should be thrown.
 */
@PublishedApi
internal fun isCallerBug(e: OctaviusException): Boolean = when (e) {
    is StatementException,
    is MappingException,
    is TypeException,
    is CodecException,
    is InvalidOperationException,
    is InitializationException -> true

    else -> false
}
