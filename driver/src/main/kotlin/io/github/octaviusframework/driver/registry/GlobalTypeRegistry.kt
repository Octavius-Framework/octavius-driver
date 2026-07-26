package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.query.QueryExecutor
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.withLock
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Global registry for managing database type registries associated with specific connection URLs.
 * 
 * In standard JDBC environments, connection pools (like HikariCP) use URLs to identify
 * different databases. This registry ensures that type mappings and user-registered converters 
 * are correctly cached and isolated per database URL.
 */
object GlobalTypeRegistry {
    /**
     * Cache mapping connection URLs to their respective TypeRegistry instances.
     */
    private val registries = ConcurrentHashMap<String, TypeRegistry>()

    /**
     * Retrieves or creates a TypeRegistry for the specified database URL.
     * This method is internal to the driver.
     */
    internal fun getRegistry(url: String): TypeRegistry {
        return registries.computeIfAbsent(url) { TypeRegistry() }
    }

    /**
     * Ensures that database types are loaded into the registry for the given URL.
     * This method is internal to the driver.
     */
    internal fun ensureLoaded(url: String, executor: QueryExecutor, searchPath: List<String>) {
        val registry = getRegistry(url)
        if (registry.isLoaded) return

        // Only one thread at a time can enter this block for a given registry
        registry.loadLock.withLock {
            if (!registry.isLoaded) {
                logger.trace { "Thread ${Thread.currentThread().name} loading types from database for URL: $url..." }
                TypeRegistryLoader.load(registry, executor, searchPath)
                registry.isLoaded = true
            }
        }
    }

    /**
     * Removes the registry for a given URL to prevent memory leaks if
     * a connection (pool) pointing to dynamic URLs is closed.
     *
     * In most standard backend applications, connection URLs are static and registries 
     * should not be removed. However, if your application dynamically connects to and 
     * disconnects from thousands of different databases at runtime, you should call this 
     * method upon closing the data source to allow the JVM Garbage Collector to free the registry.
     * 
     * @param url The JDBC connection URL of the database environment.
     */
    fun removeRegistry(url: String) {
        registries.remove(url)
    }

    /**
     * Explicitly reloads the type dictionary from the database.
     * This is internally invoked by driver connection mechanisms when a schema refresh is requested.
     */
    internal fun reload(url: String, executor: QueryExecutor, searchPath: List<String>) {
        val registry = getRegistry(url)
        registry.loadLock.withLock {
            logger.trace { "Explicit reload of type dictionary for URL: $url..." }
            TypeRegistryLoader.load(registry, executor, searchPath)
        }
    }
}