package io.github.octaviusframework.driver.spring.exception

import io.github.octaviusframework.driver.exception.OctaviusException
import org.springframework.dao.DataAccessException

/**
 * Spring-specific DataAccessException wrapper for Octavius exceptions.
 *
 * @property octaviusException The original [OctaviusException] that is being wrapped.
 */
class OctaviusDataAccessException(
    val octaviusException: OctaviusException
) : DataAccessException(octaviusException.message, octaviusException)
