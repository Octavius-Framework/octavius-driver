package io.github.octaviusframework.spring.exception

import io.github.octaviusframework.driver.exception.OctaviusException
import org.springframework.dao.DataAccessException

/**
 * Spring-specific DataAccessException wrapper for Octavius exceptions.
 */
class OctaviusDataAccessException(
    val octaviusException: OctaviusException
) : DataAccessException(octaviusException.message, octaviusException)
