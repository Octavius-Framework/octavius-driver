package io.github.octaviusframework.query

import io.github.octaviusframework.network.messages.DataRowMessage
import io.github.octaviusframework.network.messages.RowDescriptionMessage

class QueryResult(
    val rowDescription: RowDescriptionMessage?,
    val rawRows: List<DataRowMessage>,
    val commandTag: String?
) : Iterable<Row> {
    
    val rows: List<Row> by lazy {
        rawRows.map { OctaviusRow(it, rowDescription) }
    }
    
    override fun iterator(): Iterator<Row> = rows.iterator()

    fun <T> mapRows(mapper: (Row) -> T): List<T> {
        return rows.map(mapper)
    }
}
