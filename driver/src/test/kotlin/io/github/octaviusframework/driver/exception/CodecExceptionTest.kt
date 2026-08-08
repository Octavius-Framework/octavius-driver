package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.io.PgByteWriter
import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.codec.decodeSafely
import io.github.octaviusframework.driver.codec.encodeSafely
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CodecExceptionTest {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val failingCodec = object : TypeCodec<String> {
        override val pgTypeName: String = "varchar"
        override val oid: Int = 1043
        override val kotlinClass: KClass<String> = String::class
        override val fromBinary: (ByteArray, Int, Int) -> String = { _, _, _ ->
            throw IllegalArgumentException("Fake decode error")
        }
        override val toBinary: (String, PgByteWriter) -> Unit = { _, _ ->
            throw IllegalStateException("Fake encode error")
        }
    }

    private val successCodec = object : TypeCodec<String> {
        override val pgTypeName: String = "varchar"
        override val oid: Int = 1043
        override val kotlinClass: KClass<String> = String::class
        override val fromBinary: (ByteArray, Int, Int) -> String = { data, offset, length ->
            String(data, offset, length)
        }
        override val toBinary: (String, PgByteWriter) -> Unit = { _, _ ->
            // do nothing for successful encode test that doesn't check writer
        }
    }

    @Test
    fun `decodeSafely wraps exception in CodecException`() {
        val data = "test".toByteArray()
        val exception = assertFailsWith<CodecException> {
            failingCodec.decodeSafely(data, 0, data.size)
        }
        logger.error(exception) { "" }
        assertEquals(CodecAction.DECODING, exception.action)
        assertEquals(1043, exception.oid)
        assertEquals(String::class, exception.kotlinClass)
        assertEquals("Fake decode error", exception.cause?.message)
    }

    @Test
    fun `encodeSafely wraps exception in CodecException`() {
        val writer = PgByteWriter()
        val exception = assertFailsWith<CodecException> {
            failingCodec.encodeSafely("test", writer)
        }
        logger.error(exception) { "" }
        assertEquals(CodecAction.ENCODING, exception.action)
        assertEquals(1043, exception.oid)
        assertEquals(String::class, exception.kotlinClass)
        assertEquals("test", exception.value)
        assertEquals("Fake encode error", exception.cause?.message)
    }

    @Test
    fun `decodeSafely returns value on success`() {
        val data = "test".toByteArray()
        val result = successCodec.decodeSafely(data, 0, data.size)
        assertEquals("test", result)
    }

    @Test
    fun `encodeSafely succeeds without exception`() {
        val writer = PgByteWriter()
        successCodec.encodeSafely("test", writer)
        // No exception thrown
    }
}