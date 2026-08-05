package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.registry.TypeDictionary
import io.github.octaviusframework.driver.type.PgStandardType
import io.github.octaviusframework.driver.type.withPgType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TypeExceptionTest {

    @Test
    fun `should throw NESTED_PGTYPED_NOT_ALLOWED when nesting PgTyped`() {
        val nested = "test".withPgType(PgStandardType.VARCHAR)
        
        val exception = assertFailsWith<TypeException> {
            nested.withPgType(PgStandardType.TEXT)
        }
        
        assertEquals(TypeExceptionReason.NESTED_PGTYPED_NOT_ALLOWED, exception.reason)
    }
    
    @Test
    fun `should throw TYPE_NOT_FOUND for unknown OID in TypeDictionary`() {
        val dictionary = TypeDictionary.EMPTY
        
        val exception = assertFailsWith<TypeException> {
            dictionary.getPgType(999999)
        }
        
        assertEquals(TypeExceptionReason.TYPE_NOT_FOUND, exception.reason)
        assertEquals(999999, exception.oid)
    }

    @Test
    fun `should throw TYPE_NOT_FOUND for unknown type name in TypeDictionary`() {
        val dictionary = TypeDictionary.EMPTY
        
        val exception = assertFailsWith<TypeException> {
            dictionary.resolveOid("some_non_existent_type", "", false, emptyList())
        }
        
        assertEquals(TypeExceptionReason.TYPE_NOT_FOUND, exception.reason)
        assertEquals("some_non_existent_type", exception.typeName)
    }
}
