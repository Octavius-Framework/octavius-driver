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


