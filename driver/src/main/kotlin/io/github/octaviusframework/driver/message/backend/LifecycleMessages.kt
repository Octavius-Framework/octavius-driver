package io.github.octaviusframework.driver.message.backend

/**
 * Information about connection parameter (Tag 'S').
 */
internal class ParameterStatusMessage(val name: String, val value: String) : BackendMessage {
    override fun toString(): String = "ParameterStatus($name=$value)"
}

/**
 * Data about keys for canceling queries (Tag 'K').
 */
internal class BackendKeyDataMessage(val processId: Int, val secretKey: ByteArray) : BackendMessage {
    override fun toString(): String = "BackendKeyData(pid=$processId, keySize=${secretKey.size})"
}

/**
 * Ready to accept queries (Tag 'Z').
 */
internal class ReadyForQueryMessage(val transactionStatus: Char) : BackendMessage {
    override fun toString(): String = "ReadyForQuery(status=$transactionStatus)"
}

/**
 * Normal notice/warning from server (Tag 'N').
 */
internal class NoticeResponseMessage(val fields: Map<Char, String>) : BackendMessage {
    val severity: String get() = fields['V'] ?: fields['S'] ?: "NOTICE"
    val code: String get() = fields['C'] ?: "00000"
    val message: String get() = fields['M'] ?: "Unknown message"
    val detail: String? get() = fields['D']
    val hint: String? get() = fields['H']
    val where: String? get() = fields['W']

    override fun toString(): String = buildString {
        append("Postgres $severity [$code]: $message")
        if (detail != null) append(" | Detail: $detail")
        if (hint != null) append(" | Hint: $hint")
        if (where != null) append(" | Where: $where")
    }
}

