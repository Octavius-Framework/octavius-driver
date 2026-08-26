package io.github.octaviusframework.client.transaction

import io.github.octaviusframework.driver.session.TransactionIsolationLevel
import kotlin.time.Duration

/**
 * How a transaction should be run.
 *
 * Gathered into one value rather than passed as five parameters because it is what a
 * [SessionProvider][io.github.octaviusframework.client.session.SessionProvider] is handed, and providers
 * differ in what they can honour: the default one sets everything here on the session it opens, while one
 * backed by another transaction manager may own some of these itself.
 *
 * @property propagation What to do about a transaction that is already running. Defaults to
 * [TransactionPropagation.REQUIRED].
 * @property isolation The isolation level to run at, or `null` to leave the session on the level the
 * server gave it. Applied only where this definition actually starts a transaction - joining one that is
 * already running cannot change the level it began at.
 * @property readOnly Whether the transaction refuses writes. Applied on the same terms as [isolation].
 * @property statementTimeout Aborts any single statement in the transaction that runs longer than this,
 * via `SET LOCAL statement_timeout`. `null` leaves the server's setting alone.
 * @property transactionTimeout Aborts the transaction once it has been open longer than this, via
 * `SET LOCAL transaction_timeout`. `null` leaves the server's setting alone.
 */
data class TransactionDefinition(
    val propagation: TransactionPropagation = TransactionPropagation.REQUIRED,
    val isolation: TransactionIsolationLevel? = null,
    val readOnly: Boolean = false,
    val statementTimeout: Duration? = null,
    val transactionTimeout: Duration? = null
)
