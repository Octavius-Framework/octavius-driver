package com.example.demo.domain

import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import kotlin.uuid.Uuid


object UuidSerializer : ValueSerializer<Uuid>() {
    override fun serialize(value: Uuid, gen: JsonGenerator, serializers: SerializationContext) {
        gen.writeString(value.toString())
    }
}

object UuidDeserializer : ValueDeserializer<Uuid>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Uuid {
        return Uuid.parse(p.string)
    }
}

// Example composite types
data class Address(val city: String, val street: String, val buildingNumber: Int)
data class UserProfile(val age: Int, val nickname: String, val settings: Map<String, String>)

data class User(
    @field:JsonSerialize(using = UuidSerializer::class)
    @field:JsonDeserialize(using = UuidDeserializer::class)
    val id: Uuid? = null,
    val name: String,
    val role: UserRole,
    val primaryAddress: Address, // Single composite
    val shippingAddresses: List<Address>, // Array of composites
    val profile: UserProfile // Nested/another composite
)

enum class UserRole { ADMIN, USER }
