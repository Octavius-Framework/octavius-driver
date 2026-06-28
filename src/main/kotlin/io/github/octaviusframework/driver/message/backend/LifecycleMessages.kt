package io.github.octaviusframework.driver.message.backend

/**
 * Information about connection parameter (Tag 'S').
 */
class ParameterStatusMessage(val name: String, val value: String) : BackendMessage {
    override fun toString(): String = "ParameterStatus($name=$value)"
}

/**
 * Data about keys for canceling queries (Tag 'K').
 */
class BackendKeyDataMessage(val processId: Int, val secretKey: Int) : BackendMessage {
    override fun toString(): String = "BackendKeyData(pid=$processId, key=$secretKey)"
}

/**
 * Ready to accept queries (Tag 'Z').
 */
class ReadyForQueryMessage(val transactionStatus: Char) : BackendMessage {
    override fun toString(): String = "ReadyForQuery(status=$transactionStatus)"
}

/**
 * Normal notice/warning from server (Tag 'N').
 */
class NoticeResponseMessage(val fields: Map<Char, String>) : BackendMessage {
    val message: String? get() = fields['M']
    override fun toString(): String = "NoticeResponse(message=$message)"
}

/**
 * Powiadomienie asynchroniczne z komendy LISTEN/NOTIFY (Tag 'A').
 */
class NotificationResponseMessage(val processId: Int, val channel: String, val payload: String) : BackendMessage {
    override fun toString(): String = "NotificationResponse(pid=$processId, channel=$channel, payload=$payload)"
}
