package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.execution.QueryExecutor
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.withLock

private val logger = KotlinLogging.logger {}

/**
 * Global registry for managing database type registries, keyed by the physical database
 * (host, port, database name) they belong to.
 *
 * In standard JDBC environments, connection pools (like HikariCP) use URLs to identify
 * different databases. This registry ensures that type mappings and user-registered converters
 * are correctly cached and isolated per database, ignoring the parts of the URL (credentials,
 * SSL settings, timeouts, ...) that don't affect the type catalog.
 */
object GlobalTypeRegistry {
    /**
     * Cache mapping databases to their respective TypeRegistry instances.
     */
    private val registries = ConcurrentHashMap<RegistryKey, TypeRegistry>()

    /**
     * Retrieves or creates a TypeRegistry for the specified database.
     * This method is internal to the driver.
     */
    internal fun getRegistry(key: RegistryKey): TypeRegistry {
        return registries.computeIfAbsent(key) { TypeRegistry() }
    }

    /**
     * Ensures that database types are loaded into the registry for the given database.
     * This method is internal to the driver.
     */
    internal fun ensureLoaded(key: RegistryKey, executor: QueryExecutor) {
        val registry = getRegistry(key)
        if (registry.isLoaded) return

        // Only one thread at a time can enter this block for a given registry
        registry.lock.withLock {
            if (registry.isLoaded) return
            logger.trace { "Thread ${Thread.currentThread().name} loading types from database for: $key..." }
            val startedAt = System.nanoTime()
            TypeRegistryLoader.load(registry, executor)
            registry.isLoaded = true
            logger.info {
                "Loaded ${registry.dictionary.size} types for $key in ${(System.nanoTime() - startedAt) / 1_000_000}ms"
            }
        }
    }

    /**
     * Explicitly reloads the type dictionary from the database.
     * This is internally invoked by driver connection mechanisms when a schema refresh is requested.
     */
    internal fun reload(key: RegistryKey, executor: QueryExecutor) {
        val registry = getRegistry(key)
        registry.lock.withLock {
            logger.trace { "Explicit reload of type dictionary for: $key..." }
            val startedAt = System.nanoTime()
            TypeRegistryLoader.load(registry, executor)
            logger.info {
                "Reloaded ${registry.dictionary.size} types for $key in ${(System.nanoTime() - startedAt) / 1_000_000}ms"
            }
        }
    }

    /**
     * Removes the registry for a given database URL to prevent memory leaks if
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
        registries.remove(RegistryKey.from(OctaviusProperties.parse(url)))
    }
}
