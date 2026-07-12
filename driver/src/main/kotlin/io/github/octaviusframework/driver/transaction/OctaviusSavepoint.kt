package io.github.octaviusframework.driver.transaction

import io.github.octaviusframework.driver.exception.JdbcExceptionMessage
import io.github.octaviusframework.driver.exception.OctaviusJdbcException
import io.github.octaviusframework.driver.identifier.quoteAsPgIdentifier
class OctaviusSavepoint {
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

    fun getSavepointId(): Int {
        if (savepointName != null) {
            throw OctaviusJdbcException(JdbcExceptionMessage.INVALID_SAVEPOINT, "Savepoint is named")
        }
        return savepointId
    }

    fun getSavepointName(): String {
        return savepointName ?: throw OctaviusJdbcException(
            JdbcExceptionMessage.INVALID_SAVEPOINT,
            "Savepoint is un-named"
        )
    }
}