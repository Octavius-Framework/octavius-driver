package com.example.demo

import com.example.demo.domain.Address
import com.example.demo.domain.UserProfile
import com.example.demo.domain.UserRole
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.identifier.QualifiedName
import io.github.octaviusframework.driver.spring.OctaviusTemplate
import io.github.octaviusframework.driver.type.PgType
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.reflect.KClass
import kotlin.reflect.KType

class MapParameterConverter(private val objectMapper: ObjectMapper) : ParameterConverter<Map<*, *>> {
    override val supportedClass = Map::class

    override fun convert(source: Map<*, *>, expectedOid: Int, context: SerializationContext): Any {
        return objectMapper.writeValueAsString(source)
    }

    override fun getDefaultTypeName(context: SerializationContext): QualifiedName = QualifiedName("pg_catalog", "jsonb")
}

class MapResultConverter(private val objectMapper: ObjectMapper) : ResultConverter<String, Map<*, *>> {
    override val supportedSourceClass = String::class
    
    override fun canConvert(sourceClass: KClass<*>, expectedType: KType, sourceType: PgType, context: DeserializationContext): Boolean {
        return (expectedType.classifier == Map::class) && (sourceType.name == "json" || sourceType.name == "jsonb")
    }

    override fun convert(source: String, expectedType: KType, sourceType: PgType, context: DeserializationContext): Map<*, *> {
        return objectMapper.readValue(source, Map::class.java)
    }
}


@SpringBootApplication
class DemoApplication {



    @Bean
    fun initDatabase(octaviusTemplate: OctaviusTemplate): CommandLineRunner = CommandLineRunner {

        // Lokalny mapper tylko do jsonb w bazie, bez ruszania Springa
        val dbObjectMapper = jacksonObjectMapper()

        octaviusTemplate.execute { session ->
            // 0. Register custom JSON converters for Map to JSONB mapping
            session.types.registerParameterConverter(MapParameterConverter(dbObjectMapper))
            session.types.registerResultConverter(MapResultConverter(dbObjectMapper))

            // Register Enum
            session.types.registerEnum<UserRole>()

            // 1. Register our data classes as PostgreSQL composites using reflection
            // It automatically converts PascalCase (Kotlin) to snake_case (Postgres) by default.
            session.types.registerAutoComposite<Address>()
            session.types.registerAutoComposite<UserProfile>()

            // 2. Create the types in the database
            session.createNativeQuery(
                """
                DROP TABLE IF EXISTS users;
                DROP TYPE IF EXISTS address CASCADE;
                DROP TYPE IF EXISTS user_profile CASCADE;
                DROP TYPE IF EXISTS user_role CASCADE;
                
                CREATE TYPE user_role AS ENUM ('ADMIN', 'USER');
                
                CREATE TYPE address AS (
                    city text,
                    street text,
                    building_number int
                );
                
                -- We use jsonb for the map property
                CREATE TYPE user_profile AS (
                    age int,
                    nickname text,
                    settings jsonb
                );
                
                CREATE TABLE users (
                    id uuid primary key default gen_random_uuid(),
                    name varchar not null unique,
                    role user_role not null,
                    primary_address address not null,
                    shipping_addresses address[] not null,
                    profile user_profile not null
                );
            """
            ).execute()

            // Reload types so the session driver discovers the newly created OIDs
            session.reloadTypes()
        }
    }
}


fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
