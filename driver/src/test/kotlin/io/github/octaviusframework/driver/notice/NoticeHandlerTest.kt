package io.github.octaviusframework.driver.notice

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

object TestNoticeHandler : NoticeHandler {
    var lastNotice: PgNotice? = null
    override fun handleNotice(notice: PgNotice) {
        lastNotice = notice
    }
}

class NoticeHandlerTest {

    @Test
    fun testNoticeHandlerReceivesNotice() = runBlocking {
        TestNoticeHandler.lastNotice = null
        
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"
        props.noticeHandler = "io.github.octaviusframework.driver.notice.TestNoticeHandler"
        
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)
        
        // Generate a notice
        session.createNativeQuery("DO $$ BEGIN RAISE NOTICE 'test notice from test'; END; $$;").execute()
        
        val notice = TestNoticeHandler.lastNotice
        assertNotNull(notice)
        assertEquals("test notice from test", notice.message)
        assertEquals("NOTICE", notice.severity)
        
        session.close()
    }
}
