package io.github.octaviusframework.driver.transaction

import io.github.octaviusframework.driver.exception.JdbcExceptionMessage
import io.github.octaviusframework.driver.exception.OctaviusJdbcException
import io.github.octaviusframework.driver.identifier.quoteAsPgIdentifier
import java.sql.Savepoint

interface OctaviusSavepoint {
    /**
     * Retrieves the generated ID for the savepoint that this
     * `OctaviusSavepoint` object represents.
     */
    fun getSavepointId(): Int

    /**
     * Retrieves the name of the savepoint that this
     * `OctaviusSavepoint` object represents.
     */
    fun getSavepointName(): String
}

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
            throw OctaviusJdbcException(JdbcExceptionMessage.INVALID_SAVEPOINT, "Savepoint is named")
        }
        return savepointId
    }

    override fun getSavepointName(): String {
        return savepointName ?: throw OctaviusJdbcException(
            JdbcExceptionMessage.INVALID_SAVEPOINT,
            "Savepoint is un-named"
        )
    }
}