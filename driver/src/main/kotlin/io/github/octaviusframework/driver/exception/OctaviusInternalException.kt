package io.github.octaviusframework.driver.exception

/**
 * Exception used when a supposedly unreachable code path is reached.
 * Indicates a bug in the Octavius driver.
 */
class OctaviusInternalException(
    message: String = "This should never happen.",
    cause: Throwable? = null
) : OctaviusException(message, cause)