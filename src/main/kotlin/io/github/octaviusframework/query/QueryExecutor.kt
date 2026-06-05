package io.github.octaviusframework.query

import io.github.octaviusframework.network.PgStream
import io.github.octaviusframework.network.messages.*
import java.sql.SQLException
import io.github.octaviusframework.types.TypeRegistry

class QueryExecutor(private val stream: PgStream, private val typeRegistry: TypeRegistry) {

    /**
     * Używa Simple Query Protocol (Q). 
     * Przeznaczone do wywołań, które nie zwracają wyników lub ignorujemy ich wyniki (np. SET TIME ZONE, BEGIN).
     */
    fun execute(sql: String) {
        stream.sendMessage(QueryMessage(sql))
        stream.flush()

        var errorMessage: String? = null
        while (true) {
            val msg = stream.receiveMessage()
            when (msg) {
                is ErrorResponseMessage -> errorMessage = msg.message
                is ReadyForQueryMessage -> break
                // Ignorujemy inne wiadomości (RowDescription, DataRow, CommandComplete) 
                // ponieważ ta metoda służy tylko do wykonywania kodu.
                else -> { /* Ignore */ }
            }
        }

        if (errorMessage != null) {
            throw SQLException("Błąd bazy danych podczas wykonywania zapytania: $errorMessage")
        }
    }

    /**
     * Używa Extended Query Protocol (Parse, Bind, Execute, Sync).
     * Przeznaczone do DML (INSERT, UPDATE, DELETE). Oczekuje braku zwracanych wierszy.
     * Zwraca liczbę zaktualizowanych wierszy.
     */
    fun update(sql: String, paramTypes: List<UInt> = emptyList(), paramValues: List<ByteArray?> = emptyList()): Long {
        val statementName = ""
        val portalName = ""
        
        stream.sendMessage(ParseMessage(statementName, sql, paramTypes))
        stream.sendMessage(BindMessage(portalName, statementName, paramValues, listOf(1), listOf(1)))
        stream.sendMessage(DescribeMessage('P', portalName))
        stream.sendMessage(ExecuteMessage(portalName, 0))
        stream.sendMessage(SyncMessage())
        
        stream.flush()
        
        var rowsAffected = 0L
        var errorMessage: String? = null
        
        while (true) {
            val msg = stream.receiveMessage()
            when (msg) {
                is ParseCompleteMessage, is BindCompleteMessage, is NoDataMessage -> { /* Oczekiwane */ }
                is CommandCompleteMessage -> {
                    // tag ma postać np. "INSERT 0 1", "UPDATE 5", "DELETE 2"
                    val parts = msg.tag.split(" ")
                    if (parts.size >= 2) {
                        rowsAffected = parts.last().toLongOrNull() ?: 0L
                    }
                }
                is DataRowMessage, is RowDescriptionMessage -> {
                    if (errorMessage == null) errorMessage = "Metoda update() otrzymała wiersze z wynikami. Użyj query() dla zapytań DQL."
                }
                is ErrorResponseMessage -> {
                    if (errorMessage == null) errorMessage = "Błąd bazy danych podczas wykonywania zapytania (update): ${msg.message}"
                }
                is ReadyForQueryMessage -> break
                else -> { /* Ignore */ }
            }
        }

        if (errorMessage != null) {
            throw SQLException(errorMessage)
        }
        
        return rowsAffected
    }

    /**
     * Używa Extended Query Protocol.
     * Przeznaczone do DQL (SELECT).
     * Zwraca od razu sparsowaną listę wierszy (Row).
     */
    fun query(sql: String, paramTypes: List<UInt> = emptyList(), paramValues: List<ByteArray?> = emptyList()): List<Row> {
        val statementName = ""
        val portalName = ""
        
        stream.sendMessage(ParseMessage(statementName, sql, paramTypes))
        stream.sendMessage(BindMessage(portalName, statementName, paramValues, listOf(1), listOf(1)))
        stream.sendMessage(DescribeMessage('P', portalName))
        stream.sendMessage(ExecuteMessage(portalName, 0))
        stream.sendMessage(SyncMessage())
        
        stream.flush()
        
        val rows = mutableListOf<DataRowMessage>()
        var rowDescription: RowDescriptionMessage? = null
        var errorMessage: String? = null
        
        while (true) {
            val msg = stream.receiveMessage()
            when (msg) {
                is ParseCompleteMessage, is BindCompleteMessage -> { /* Oczekiwane */ }
                is RowDescriptionMessage -> rowDescription = msg
                is NoDataMessage -> { /* Oczekiwane jeśli zapytanie nie zwraca wierszy */ }
                is DataRowMessage -> rows.add(msg)
                is CommandCompleteMessage -> { /* Ignorujemy w zapytaniach DQL */ }
                is ErrorResponseMessage -> {
                    if (errorMessage == null) errorMessage = "Błąd bazy danych podczas wykonywania zapytania (query): ${msg.message}"
                }
                is ReadyForQueryMessage -> break
                else -> { /* Ignore */ }
            }
        }
        
        if (errorMessage != null) {
            throw SQLException(errorMessage)
        }

        if (rowDescription == null) {
            throw SQLException("Metoda query() nie otrzymała opisu wierszy (RowDescriptionMessage). Upewnij się, że zapytanie to DQL (SELECT).")
        }

        val descriptors = rowDescription.fields
        return rows.map { OctaviusRow(it.columns, descriptors, typeRegistry) }
    }
}
