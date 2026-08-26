package io.github.octaviusframework.client.transaction

/**
 * What a transaction does when it is started inside one that is already running.
 */
enum class TransactionPropagation {

    /**
     * Join the transaction already running, or start one where there is none.
     *
     * The joined block does not commit: the outermost transaction is the one that decides, so a failure
     * anywhere inside takes the whole thing down with it.
     */
    REQUIRED,

    /**
     * Always run in a transaction of its own, on a session of its own.
     *
     * The surrounding transaction is suspended for the duration and neither can roll the other back. This
     * is what an audit record wants: it has to survive the failure of the work that produced it.
     *
     * Note that the two transactions are then holding two connections at once, and the inner one cannot
     * see the outer one's uncommitted rows.
     */
    REQUIRES_NEW,

    /**
     * Run inside a savepoint of the transaction already running, or start one where there is none.
     *
     * A failure rolls back to the savepoint and leaves the surrounding transaction usable, which is what
     * separates this from [REQUIRED]. It is still the same transaction and the same connection, so the
     * outer one failing later still discards this work.
     */
    NESTED
}
