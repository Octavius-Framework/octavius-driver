package io.github.octaviusframework.network.messages

import io.github.octaviusframework.io.PgOutputStream

sealed interface FrontendMessage {
    /**
     * Zapisuje całą ramkę wiadomości do strumienia (włącznie ze znacznikiem typu i długością).
     */
    fun encode(out: PgOutputStream)
}
