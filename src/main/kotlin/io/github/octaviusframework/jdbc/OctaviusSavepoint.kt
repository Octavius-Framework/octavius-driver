package io.github.octaviusframework.jdbc

import io.github.octaviusframework.types.quoteAsPgIdentifier
import java.sql.SQLException
import java.sql.Savepoint

class OctaviusSavepoint : Savepoint {
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
            throw SQLException("Savepoint is named")
        }
        return savepointId
    }

    override fun getSavepointName(): String {
        return savepointName ?: throw SQLException("Savepoint is un-named")
    }
}
