package io.github.octaviusframework.spring.exception

import io.github.octaviusframework.driver.exception.OctaviusException
import org.springframework.dao.DataAccessException

/**
 * Spring-specific DataAccessException wrapper for Octavius exceptions.
 */
class OctaviusDataAccessException(
    message: String,
    val octaviusException: OctaviusException? = null,
    cause: Throwable? = octaviusException
) : DataAccessException(message, cause) {

    constructor(octaviusException: OctaviusException) : this(
        message = octaviusException.message ?: "Octavius Data Access Exception",
        octaviusException = octaviusException
    )
}
