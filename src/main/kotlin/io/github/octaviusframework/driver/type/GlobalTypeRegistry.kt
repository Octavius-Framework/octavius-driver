package io.github.octaviusframework.driver.type

import io.github.octaviusframework.driver.query.QueryExecutor
import java.util.concurrent.ConcurrentHashMap

object GlobalTypeRegistry {
    // Rejestry na URL bazy
    private val registries = ConcurrentHashMap<String, TypeRegistry>()
    private val loadedFlags = ConcurrentHashMap<String, Boolean>()

    fun getRegistry(url: String): TypeRegistry {
        return registries.computeIfAbsent(url) { TypeRegistry() }
    }

    fun ensureLoaded(url: String, executor: QueryExecutor, searchPath: List<String>) {
        if (loadedFlags[url] == true) return

        val registry = getRegistry(url)
        // Only one thread at a time can enter this block for a given registry
        synchronized(registry) {
            if (loadedFlags[url] != true) {
                println("Thread ${Thread.currentThread().name} loading types from database for URL: $url...")
                TypeRegistryLoader.load(registry, executor, searchPath)
                loadedFlags[url] = true
            }
        }
    }

    /**
     * Explicit reload to be called by user (e.g. connection.reloadTypes())
     */
    fun reload(url: String, executor: QueryExecutor, searchPath: List<String>) {
        val registry = getRegistry(url)
        synchronized(registry) {
            println("Explicit reload of type dictionary for URL: $url...")
            TypeRegistryLoader.load(registry, executor, searchPath)
            loadedFlags[url] = true
        }
    }
}