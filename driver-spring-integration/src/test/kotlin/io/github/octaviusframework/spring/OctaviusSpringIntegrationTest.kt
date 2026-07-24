package io.github.octaviusframework.spring

import io.github.octaviusframework.driver.session.OctaviusSession
import io.github.octaviusframework.driver.row.get
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource
import org.springframework.transaction.annotation.Propagation

@SpringBootTest(classes = [TestApplication::class, OctaviusSpringAutoConfiguration::class, DataSourceAutoConfiguration::class], properties = [
    "spring.datasource.url=jdbc:octavius://localhost:5432/octavius_test",
    "spring.datasource.username=postgres",
    "spring.datasource.password=1234",
    "spring.datasource.driver-class-name=io.github.octaviusframework.driver.jdbc.OctaviusDriver"
])
class OctaviusSpringIntegrationTest {

    @Autowired
    lateinit var octaviusTemplate: OctaviusTemplate

    @Autowired
    lateinit var testService: TestService

    @Test
    fun `should autoconfigure OctaviusTemplate`() {
        assertNotNull(octaviusTemplate)
        val row = octaviusTemplate.execute { session -> session.createNativeQuery("SELECT 1 as num").fetchOne() }
        assertEquals(1, row.get<Int>("num"))
    }

    @Test
    fun `should work within transactions`() {
        testService.createTable()
        
        try {
            testService.insertWithRollback()
        } catch (e: RuntimeException) {
            // expected
        }

        val count = octaviusTemplate.execute { session -> session.createNativeQuery("SELECT count(*) as c FROM test_spring").fetchOne().get<Long>("c") }
        assertEquals(0L, count)
        
        testService.insertWithCommit()
        val countAfterCommit = octaviusTemplate.execute { session -> session.createNativeQuery("SELECT count(*) as c FROM test_spring").fetchOne().get<Long>("c") }
        assertEquals(1L, countAfterCommit)
    }
    
    @Test
    fun `should work with nested transactions`() {
        testService.createTable()
        
        try {
            testService.insertWithNestedRollback()
        } catch (e: RuntimeException) {
            // expected
        }
        
        val count = octaviusTemplate.execute { session -> session.createNativeQuery("SELECT count(*) as c FROM test_spring").fetchOne().get<Long>("c") }
        assertEquals(1L, count) // Outer insert should be there, nested should be rolled back
    }
}

@TestConfiguration
@org.springframework.transaction.annotation.EnableTransactionManagement
open class TestApplication {
    
    @Bean
    open fun testService(octaviusTemplate: OctaviusTemplate): TestService {
        return TestService(octaviusTemplate)
    }
}

open class TestService(private val octaviusTemplate: OctaviusTemplate) {

    open fun createTable() {
        octaviusTemplate.execute { session -> session.createNativeQuery("CREATE TABLE IF NOT EXISTS test_spring (id SERIAL PRIMARY KEY, val TEXT)").execute() }
        octaviusTemplate.execute { session -> session.createNativeQuery("TRUNCATE test_spring").execute() }
    }

    @Transactional
    open fun insertWithRollback() {
        octaviusTemplate.execute { session -> session.createNativeQuery("INSERT INTO test_spring (val) VALUES ('test')").execute() }
        throw RuntimeException("Rollback")
    }

    @Transactional
    open fun insertWithCommit() {
        octaviusTemplate.execute { session -> session.createNativeQuery("INSERT INTO test_spring (val) VALUES ('test')").execute() }
    }
    
    @org.springframework.context.annotation.Lazy
    @Autowired
    lateinit var self: TestService

    @Transactional
    open fun insertWithNestedRollback() {
        octaviusTemplate.execute { session -> session.createNativeQuery("INSERT INTO test_spring (val) VALUES ('outer')").execute() }
        try {
            self.nestedRollback()
        } catch (e: RuntimeException) {
            // caught
        }
    }

    @Transactional(propagation = Propagation.NESTED)
    open fun nestedRollback() {
        octaviusTemplate.execute { session -> session.createNativeQuery("INSERT INTO test_spring (val) VALUES ('nested')").execute() }
        throw RuntimeException("Nested rollback")
    }
}
