package io.github.octaviusframework.types

import io.github.octaviusframework.query.QueryExecutor
import java.util.concurrent.ConcurrentHashMap

object GlobalTypeRegistry {
    // Rejestry na URL bazy
    private val registries = ConcurrentHashMap<String, TypeRegistry>()
    private val loadedFlags = ConcurrentHashMap<String, Boolean>()

    fun getRegistry(url: String): TypeRegistry {
        return registries.computeIfAbsent(url) { TypeRegistry() }
    }

    fun ensureLoaded(url: String, executor: QueryExecutor) {
        if (loadedFlags[url] == true) return

        val registry = getRegistry(url)
        // Tylko jeden wątek na raz może wejść do tego bloku dla danego rejestru
        synchronized(registry) {
            if (loadedFlags[url] != true) {
                println("Wątek ${Thread.currentThread().name} ładuje typy z bazy dla URL: $url...")
                TypeRegistryLoader.load(registry, executor)
                loadedFlags[url] = true
            }
        }
    }

    /**
     * Jawny reload do wywołania przez użytkownika (np. connection.reloadTypes())
     */
    fun reload(url: String, executor: QueryExecutor) {
        val registry = getRegistry(url)
        synchronized(registry) {
            println("Jawne przeładowanie słownika typów dla URL: $url...")
            registry.clearOidMappings()
            TypeRegistryLoader.load(registry, executor)
            loadedFlags[url] = true
        }
    }
}