package io.github.octaviusframework.deserialization

import io.github.octaviusframework.converter.row.MapRowConverter
import io.github.octaviusframework.converter.row.ReflectionRowConverter
import java.util.concurrent.ConcurrentHashMap

object GlobalConverterRegistry {
    // Rejestry na URL bazy
    private val registries = ConcurrentHashMap<String, ConverterRegistry>()

    fun getRegistry(url: String): ConverterRegistry {
        return registries.computeIfAbsent(url) { 
            val registry = ConverterRegistry()
            registry.addConverter(AnyConverter())
            registry.addConverter(MapCompositeConverter())
            registry.addConverter(CollectionArrayConverter())
            registry.addConverter(ReflectionCompositeConverter())
            registry.addConverter(ReflectionRowConverter())
            registry.addConverter(MapRowConverter())
            registry.addConverter(JsonElementConverter())
            registry
        }
    }
}
