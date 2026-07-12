package io.github.octaviusframework.driver.message.frontend

import io.github.octaviusframework.driver.io.PgOutputStream

internal sealed interface FrontendMessage {
    /**
     * Writes the entire message frame to the stream (including type marker and length).
     */
    fun encode(out: PgOutputStream)
}
