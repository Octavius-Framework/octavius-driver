package io.github.octaviusframework.types

import io.github.octaviusframework.query.QueryExecutor

object GlobalTypeRegistry {
    // Rejestr, do którego będą miały dostęp wszystkie połączenia
    val registry = TypeRegistry()

    // Flaga @Volatile gwarantuje, że zmiana wartości będzie od razu widoczna dla innych wątków w CPU
    @Volatile
    private var isLoaded = false

    fun ensureLoaded(executor: QueryExecutor) {
        // Fast-path: jeśli już załadowane, zwracamy natychmiast (zero narzutu na blokowanie)
        if (isLoaded) return

        // Tylko jeden wątek na raz może wejść do tego bloku
        synchronized(this) {
            // Wątki 2-10, które czekały przed kłódką, wejdą tu gdy wątek 1 skończy.
            // Muszą sprawdzić drugi raz, żeby nie załadować znowu.
            if (!isLoaded) {
                println("Wątek ${Thread.currentThread().name} ładuje typy z bazy...")

                // Ładujemy typy!
                TypeRegistryLoader.load(registry, executor)

                // Zapisujemy flagę
                isLoaded = true
            }
        }
    }

    /**
     * Jawny reload do wywołania przez użytkownika (np. connection.reloadTypes())
     */
    fun reload(executor: QueryExecutor) {
        synchronized(this) {
            println("Jawne przeładowanie słownika typów...")
            registry.clearOidMappings()
            TypeRegistryLoader.load(registry, executor)
            isLoaded = true
        }
    }
}