package io.github.octaviusframework.client.session

import io.github.octaviusframework.client.transaction.TransactionDefinition
import io.github.octaviusframework.client.transaction.TransactionPropagation
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.session.OctaviusSession
import io.github.octaviusframework.driver.session.OctaviusSessionOperations
import javax.sql.DataSource

/**
 * The [SessionProvider] for an application that runs its own transactions, binding the current one to the
 * thread that started it.
 *
 * Outside a transaction, every operation borrows a session from [dataSource] and gives it back when it is
 * done. That is as cheap as it sounds: the session itself is a handful of allocations over the connection,
 * and giving it back costs no round trip unless it registered a `LISTEN` or left a transaction open.
 *
 * Where something else owns the transaction - Spring, most obviously - this is the wrong provider; that one
 * finds the session where the framework put it rather than on a `ThreadLocal` of its own.
 *
 * @param dataSource Where connections come from. Not closed by [close]; whoever built the pool owns it.
 */
class DefaultSessionProvider(private val dataSource: DataSource) : SessionProvider {

    /**
     * The session of the transaction running on this thread, if one is.
     *
     * Only ever set for the duration of [inNewSession], which starts a transaction on it immediately - so a
     * value here always means a transaction is open, and [execute] can join it without asking.
     */
    private val bound = ThreadLocal<OctaviusSession?>()

    override fun <T> execute(action: OctaviusSessionOperations.() -> T): T {
        val active = bound.get()
        if (active != null) return active.action()
        return dataSource.getOctaviusSession().use { it.action() }
    }

    override fun <T> transaction(definition: TransactionDefinition, block: () -> T): T {
        val active = bound.get()

        return when (definition.propagation) {
            TransactionPropagation.REQUIRED ->
                if (active == null) newTransaction(definition, block)
                else
                    // Joining, so this does not commit: `required` runs the block as it stands while the
                    // session is already in a transaction, and the outermost scope is what decides. The
                    // definition is handed over all the same, so that the driver stays the one place that
                    // decides which of these terms a joined transaction can honour, and warns about the rest.
                    active.transaction.required(
                        definition.isolation,
                        definition.readOnly,
                        definition.statementTimeout,
                        definition.transactionTimeout
                    ) { block() }

            TransactionPropagation.NESTED ->
                if (active == null) newTransaction(definition, block)
                else
                    active.transaction.nested(
                        definition.isolation,
                        definition.readOnly,
                        definition.statementTimeout,
                        definition.transactionTimeout
                    ) { block() }

            // A session of its own, whether or not one was bound. `inNewSession` rebinds for the duration
            // and puts the outer one back afterwards, so the suspension needs nothing else.
            TransactionPropagation.REQUIRES_NEW -> newTransaction(definition, block)
        }
    }

    /**
     * Opens a session, starts a transaction on it and runs [block] inside.
     *
     * Every term of the definition goes to `required`, which sends the whole lot as one statement after the
     * `BEGIN` - `SET TRANSACTION` for the isolation level and the read-only flag, `SET LOCAL` for the
     * timeouts. All four end with the transaction, so a pooled connection goes back carrying none of them
     * and there is nothing here to undo.
     */
    private fun <T> newTransaction(definition: TransactionDefinition, block: () -> T): T =
        inNewSession { session ->
            session.transaction.required(
                definition.isolation,
                definition.readOnly,
                definition.statementTimeout,
                definition.transactionTimeout
            ) { block() }
        }

    /**
     * Borrows a session, binds it for the duration of [body], and gives it back afterwards.
     *
     * The previous binding is restored rather than cleared, which is what makes `REQUIRES_NEW` inside an
     * existing transaction come back to the outer session instead of to no session at all.
     */
    private fun <T> inNewSession(body: (OctaviusSession) -> T): T {
        val session = dataSource.getOctaviusSession()
        val previous = bound.get()
        try {
            bound.set(session)
            return body(session)
        } finally {
            if (previous != null) bound.set(previous) else bound.remove()
            session.close()
        }
    }
}
