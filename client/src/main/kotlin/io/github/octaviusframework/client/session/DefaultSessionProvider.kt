package io.github.octaviusframework.client.session

import io.github.octaviusframework.client.transaction.TransactionDefinition
import io.github.octaviusframework.client.transaction.TransactionPropagation
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.session.OctaviusSession
import io.github.octaviusframework.driver.session.OctaviusSessionOperations
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

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
                else {
                    warnIfSettingsCannotBeHonoured(definition)
                    // Joining, so this does not commit: `required` runs the block as it stands while the
                    // session is already in a transaction, and the outermost scope is what decides.
                    active.transaction.required { block() }
                }

            TransactionPropagation.NESTED ->
                if (active == null) newTransaction(definition, block)
                else {
                    warnIfSettingsCannotBeHonoured(definition)
                    active.transaction.nested { block() }
                }

            // A session of its own, whether or not one was bound. `inNewSession` rebinds for the duration
            // and puts the outer one back afterwards, so the suspension needs nothing else.
            TransactionPropagation.REQUIRES_NEW -> newTransaction(definition, block)
        }
    }

    /**
     * Opens a session, starts a transaction on it and runs [block] inside.
     */
    private fun <T> newTransaction(definition: TransactionDefinition, block: () -> T): T =
        inNewSession(definition) { session ->
            session.transaction.required {
                // Inside the transaction on purpose: `SET LOCAL` reverts at its end and does nothing
                // outside one, so these cannot be applied alongside isolation before it begins.
                applyTimeouts(definition)
                block()
            }
        }

    /**
     * Borrows a session, binds it for the duration of [body], and gives it back afterwards.
     *
     * The previous binding is restored rather than cleared, which is what makes `REQUIRES_NEW` inside an
     * existing transaction come back to the outer session instead of to no session at all.
     */
    private fun <T> inNewSession(definition: TransactionDefinition, body: (OctaviusSession) -> T): T {
        val session = dataSource.getOctaviusSession()
        val previous = bound.get()
        try {
            // Both have to be set before the transaction begins - the driver sends `BEGIN` as soon as
            // auto-commit goes off, and neither can be changed once it has.
            definition.isolation?.let { session.transactionIsolationLevel = it }
            if (definition.readOnly) session.readOnly = true

            bound.set(session)
            return body(session)
        } finally {
            if (previous != null) bound.set(previous) else bound.remove()
            session.close()
        }
    }

    /**
     * Says out loud that a transaction being joined began on somebody else's terms.
     *
     * Neither setting can be changed once a transaction is running, so the alternative to a log line is
     * that the block quietly runs at an isolation level it did not ask for. Raising instead would be the
     * stricter reading, but it would also break the ordinary case where an inner unit of work states the
     * level it needs and happens to be called from an outer one that already provides it.
     */
    private fun warnIfSettingsCannotBeHonoured(definition: TransactionDefinition) {
        if (definition.isolation == null && !definition.readOnly) return
        logger.warn {
            "Joining a transaction that is already running; its isolation level and read-only setting " +
                "stand, and this block's (isolation=${definition.isolation}, readOnly=${definition.readOnly}) " +
                "are ignored. Use REQUIRES_NEW where they have to hold."
        }
    }

    /**
     * Applies the definition's timeouts to the transaction that has just begun.
     *
     * Sent as `SET LOCAL`, so they revert when the transaction ends and cannot follow the connection back
     * into the pool.
     */
    private fun OctaviusSessionOperations.applyTimeouts(definition: TransactionDefinition) {
        definition.statementTimeout?.let {
            createNativeQuery("SET LOCAL statement_timeout = ${it.inWholeMilliseconds}").execute()
        }
        definition.transactionTimeout?.let {
            createNativeQuery("SET LOCAL transaction_timeout = ${it.inWholeMilliseconds}").execute()
        }
    }
}
