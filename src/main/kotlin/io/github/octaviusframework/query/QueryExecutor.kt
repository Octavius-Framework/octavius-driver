package io.github.octaviusframework.query

import io.github.octaviusframework.network.PgStream
import io.github.octaviusframework.network.messages.*
import java.sql.SQLException

class QueryExecutor(private val stream: PgStream) {
    fun executeExtendedQuery(sql: String, params: List<ByteArray?> = emptyList()): QueryResult {
        // Używamy pustych ("") nazw statementu i portalu dla jednorazowych, anonimowych zapytań
        val statementName = ""
        val portalName = ""
        
        stream.sendMessage(ParseMessage(statementName, sql))
        stream.sendMessage(BindMessage(portalName, statementName, params, listOf(0), listOf(1)))
        stream.sendMessage(DescribeMessage('P', portalName))
        stream.sendMessage(ExecuteMessage(portalName, 0))
        stream.sendMessage(SyncMessage())
        
        // TCP Pipelining
        stream.flush()
        
        val rows = mutableListOf<DataRowMessage>()
        var rowDescription: RowDescriptionMessage? = null
        var commandTag: String? = null
        
        while (true) {
            val msg = stream.receiveMessage()
            when (msg) {
                is ParseCompleteMessage -> { /* Oczekiwane */ }
                is BindCompleteMessage -> { /* Oczekiwane */ }
                is RowDescriptionMessage -> rowDescription = msg
                is NoDataMessage -> { /* Zapytanie nie zwraca wierszy (np. UPDATE) */ }
                is DataRowMessage -> rows.add(msg)
                is CommandCompleteMessage -> commandTag = msg.tag
                is ErrorResponseMessage -> throw SQLException("Błąd bazy danych podczas wykonywania zapytania: ${msg.message}")
                is ReadyForQueryMessage -> break // Koniec przetwarzania zapytania
                else -> println("Ignoruje niespodziewana wiadomosc w trakcie zapytania: $msg")
            }
        }
        
        val finalRows = if (rowDescription != null) {
            val descriptors = rowDescription.fields
            rows.map { OctaviusRow(it.columns, descriptors) }
        } else {
            emptyList()
        }
        
        return QueryResult(finalRows, commandTag)
    }
}
