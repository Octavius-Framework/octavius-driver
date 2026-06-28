package io.github.octaviusframework.driver.jdbc

import io.github.octaviusframework.driver.type.quoteAsPgIdentifier
import io.github.octaviusframework.driver.exception.JdbcExceptionMessage
import io.github.octaviusframework.driver.exception.OctaviusJdbcException
import java.sql.SQLException
import java.sql.Savepoint

internal class OctaviusSavepoint : Savepoint {
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
        return savepointName ?: throw OctaviusJdbcException(JdbcExceptionMessage.INVALID_SAVEPOINT, "Savepoint is un-named")
    }
}
