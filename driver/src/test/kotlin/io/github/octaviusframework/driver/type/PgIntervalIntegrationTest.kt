package io.github.octaviusframework.driver.type

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.row.get
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PgIntervalIntegrationTest {

    @Test
    fun `test finite PgInterval roundtrip via DB`() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val finiteInterval = PgInterval.Finite(
            time = 3600_000_000L + 500_000L, // 1 hour + 0.5s in microseconds
            days = 15,
            months = 14 // 1 year and 2 months
        )

        val result = session.createNativeQuery("SELECT $1 as interval")
            .fetchOne(finiteInterval)
            
        assertEquals(finiteInterval, result.get(0))
    }

    @Test
    fun `test PgInterval infinity mappings via DB`() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val result = session.createNativeQuery("SELECT $1 as f, $2 as p")
            .fetchOne(PgInterval.Infinity, PgInterval.MinusInfinity)

        assertEquals(PgInterval.Infinity, result.get(0))
        assertEquals(PgInterval.MinusInfinity, result.get(1))
    }

    @Test
    fun `test Postgres string to PgInterval`() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val result = session.createNativeQuery("SELECT '1 year 2 months 15 days 01:00:00.5'::interval")
            .fetchOne()

        val expected = PgInterval.Finite(
            time = 3600_000_000L + 500_000L, // 1 hour + 0.5 sec
            days = 15,
            months = 14
        )
        assertEquals(expected, result.get(0))
    }
    
    @Test
    fun `test infinity from Postgres string`() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val result = session.createNativeQuery("SELECT 'infinity'::interval as f, '-infinity'::interval as p")
            .fetchOne()

        assertEquals(PgInterval.Infinity, result.get(0))
        assertEquals(PgInterval.MinusInfinity, result.get(1))
    }
}
