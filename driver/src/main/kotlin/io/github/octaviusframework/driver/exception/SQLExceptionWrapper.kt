package io.github.octaviusframework.driver.exception

import java.sql.SQLException

/**
 * A wrapper class that adapts an [OctaviusException] into a standard [SQLException].
 *
 * This wrapper is necessary for integration with standard JDBC components like connection pools
 * (e.g., HikariCP) which rely on intercepting [SQLException] to analyze connection state,
 * evict dead connections, and properly interpret the `SQLState`.
 *
 * @property wrappedException The original [OctaviusException] that is being wrapped.
 */
internal class SQLExceptionWrapper(val wrappedException: OctaviusException) : SQLException(wrappedException.message, wrappedException.sqlState)