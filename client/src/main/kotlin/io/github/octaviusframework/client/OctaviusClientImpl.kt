package io.github.octaviusframework.client

import io.github.octaviusframework.client.dynamic.DynamicTypes
import io.github.octaviusframework.client.dynamic.DynamicWriteStrategy
import io.github.octaviusframework.client.query.DeleteQuery
import io.github.octaviusframework.client.query.InsertQuery
import io.github.octaviusframework.client.query.RawQuery
import io.github.octaviusframework.client.query.SelectQuery
import io.github.octaviusframework.client.query.UpdateQuery
import io.github.octaviusframework.client.session.SessionProvider
import io.github.octaviusframework.client.transaction.TransactionDefinition
import io.github.octaviusframework.client.transaction.TransactionPropagation
import io.github.octaviusframework.driver.session.OctaviusSessionOperations
import io.github.octaviusframework.driver.session.TransactionIsolationLevel
import io.github.octaviusframework.serializer.octaviusJson
import kotlinx.serialization.json.Json
import kotlin.time.Duration

/**
 * The only implementation of [OctaviusClient]. Everything it does, it does through its [SessionProvider].
 *
 * @param provider Decides which session each operation runs on.
 * @param onClose What [close] should release, or `null` where the client owns nothing.
 * @param dynamicJson How `dynamic_dto` payloads are read and written.
 * @param dynamicWriteStrategy When an unwrapped instance of a registered class is written as a `dynamic_dto`.
 */
internal class OctaviusClientImpl(
    private val provider: SessionProvider,
    private val onClose: (() -> Unit)? = null,
    dynamicJson: Json = octaviusJson,
    dynamicWriteStrategy: DynamicWriteStrategy = DynamicWriteStrategy.AUTOMATIC_WHEN_UNAMBIGUOUS
) : OctaviusClient {

    override val dynamicTypes: DynamicTypes = DynamicTypes(this, dynamicJson, dynamicWriteStrategy)

    override fun <T> execute(block: OctaviusSessionOperations.() -> T): T = provider.execute(block)

    override fun rawQuery(sql: String): RawQuery = RawQuery(provider, sql)

    override fun select(vararg columns: String): SelectQuery =
        SelectQuery(provider, columns.joinToString(", "))

    override fun insertInto(table: String): InsertQuery = InsertQuery(provider, table)

    override fun update(table: String): UpdateQuery = UpdateQuery(provider, table)

    override fun deleteFrom(table: String): DeleteQuery = DeleteQuery(provider, table)

    override fun <T> transaction(
        propagation: TransactionPropagation,
        isolation: TransactionIsolationLevel?,
        readOnly: Boolean,
        statementTimeout: Duration?,
        transactionTimeout: Duration?,
        block: OctaviusClient.() -> T
    ): T {
        val definition = TransactionDefinition(
            propagation = propagation,
            isolation = isolation,
            readOnly = readOnly,
            statementTimeout = statementTimeout,
            transactionTimeout = transactionTimeout
        )

        // The block gets this client, not a session: a query inside it finds the bound session through the
        // provider when its terminal runs, exactly as one outside a transaction finds a borrowed one.
        return provider.transaction(definition) { block() }
    }

    override fun close() {
        onClose?.invoke()
    }
}
