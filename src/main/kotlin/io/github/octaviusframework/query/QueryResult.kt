package io.github.octaviusframework.query

import io.github.octaviusframework.network.messages.DataRowMessage
import io.github.octaviusframework.network.messages.RowDescriptionMessage

class QueryResult(
    val rows: List<Row>,
    val commandTag: String?
) : Iterable<Row> {
    
    override fun iterator(): Iterator<Row> = rows.iterator()

    fun <T> mapRows(mapper: (Row) -> T): List<T> {
        return rows.map(mapper)
    }
}
