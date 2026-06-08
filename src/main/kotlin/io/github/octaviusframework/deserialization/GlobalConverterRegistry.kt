package io.github.octaviusframework.deserialization

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
            
            // Primitive array converters
            registry.addConverter(IntArrayConverter())
            registry.addConverter(DoubleArrayConverter())
            registry.addConverter(FloatArrayConverter())
            registry.addConverter(LongArrayConverter())
            registry.addConverter(ShortArrayConverter())
            registry.addConverter(ByteArrayConverter())
            registry.addConverter(BooleanArrayConverter())
            registry.addConverter(CharArrayConverter())
            
            registry
        }
    }
}
