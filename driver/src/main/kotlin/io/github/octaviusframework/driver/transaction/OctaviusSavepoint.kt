package io.github.octaviusframework.driver.transaction

import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason
import io.github.octaviusframework.driver.identifier.quoteAsPgIdentifier
import java.sql.Savepoint

/**
 * Represents a savepoint within a database transaction in the Octavius framework.
 *
 * A savepoint can be used to roll back portions of a transaction. It can be created
 * either with an auto-generated numeric ID or with a specific name.
 */
interface OctaviusSavepoint {
    /**
     * Retrieves the generated ID for the savepoint that this
     * `OctaviusSavepoint` object represents.
     *
     * @return The auto-generated numeric ID of this savepoint.
     * @throws InvalidOperationException if the savepoint is named.
     */
    fun getSavepointId(): Int

    /**
     * Retrieves the name of the savepoint that this
     * `OctaviusSavepoint` object represents.
     *
     * @return The name of this savepoint.
     * @throws InvalidOperationException if the savepoint is un-named.
     */
    fun getSavepointName(): String
}

/**
 * Internal implementation of the [OctaviusSavepoint] and standard JDBC [Savepoint] interfaces.
 */
internal class OctaviusSavepointImpl : OctaviusSavepoint, Savepoint {
    private val savepointId: Int
    private val savepointName: String?

    val pgName: String

    constructor(id: Int) {
        this.savepointId = id
        this.savepointName = null
        this.pgName = "octavius_savepoint_$id"
    }

    constructor(name: String) {
        this.savepointId = -1
        this.savepointName = name
        this.pgName = name.quoteAsPgIdentifier()
    }

    override fun getSavepointId(): Int {
        if (savepointName != null) {
            throw InvalidOperationException(InvalidOperationExceptionReason.INVALID_SAVEPOINT, "Savepoint is named")
        }
        return savepointId
    }

    override fun getSavepointName(): String {
        return savepointName ?: throw InvalidOperationException(
            InvalidOperationExceptionReason.INVALID_SAVEPOINT,
            "Savepoint is un-named"
        )
    }
}
