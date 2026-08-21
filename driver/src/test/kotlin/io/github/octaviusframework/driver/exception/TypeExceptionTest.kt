package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.registry.TypeDictionary
import io.github.octaviusframework.driver.type.PgStandardType
import io.github.octaviusframework.driver.type.withPgType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TypeExceptionTest {

    companion object {
        val logger = KotlinLogging.logger {}
    }

    @Test
    fun `should throw NESTED_PGTYPED_NOT_ALLOWED when nesting PgTyped`() {
        val nested = "test".withPgType(PgStandardType.VARCHAR)
        
        val exception = assertFailsWith<TypeException> {
            nested.withPgType(PgStandardType.TEXT)
        }
        logger.error(exception) { "" }
        assertEquals(TypeExceptionReason.NESTED_PGTYPED_NOT_ALLOWED, exception.reason)
    }
    
    @Test
    fun `should throw TYPE_NOT_FOUND for unknown OID in TypeDictionary`() {
        val dictionary = TypeDictionary.build(emptyMap())
        
        val exception = assertFailsWith<TypeException> {
            dictionary.getPgType(999999)
        }
        logger.error(exception) { "" }
        assertEquals(TypeExceptionReason.TYPE_NOT_FOUND, exception.reason)
        assertEquals(999999, exception.oid)
    }

    @Test
    fun `should throw TYPE_NOT_FOUND for unknown type name in TypeDictionary`() {
        val dictionary = TypeDictionary.build(emptyMap())
        
        val exception = assertFailsWith<TypeException> {
            dictionary.resolveOid("some_non_existent_type", "", false, emptyList())
        }
        logger.error(exception) { "" }
        assertEquals(TypeExceptionReason.TYPE_NOT_FOUND, exception.reason)
        assertEquals("some_non_existent_type", exception.typeName)
    }
}
