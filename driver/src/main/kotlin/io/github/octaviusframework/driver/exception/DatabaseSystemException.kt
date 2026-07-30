package io.github.octaviusframework.driver.exception

class DatabaseSystemException(
    message: String,
    sqlState: String? = null
) : OctaviusException(message, sqlState = sqlState)