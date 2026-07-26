package io.github.octaviusframework.driver.type

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.row.get
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import org.junit.jupiter.api.Test
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class DateTimeIntegrationTest {

    @Test
    fun `test DateTime infinity mappings via DB`() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        // 1. Test LocalDate mapping
        val dateResult = session.createNativeQuery("SELECT $1 as f, $2 as p")
            .fetchOne(LocalDate.DISTANT_FUTURE, LocalDate.DISTANT_PAST)
        assertEquals(LocalDate.DISTANT_FUTURE, dateResult.get(0))
        assertEquals(LocalDate.DISTANT_PAST, dateResult.get(1))

        // 2. Test LocalDateTime mapping
        val dateTimeResult = session.createNativeQuery("SELECT $1 as f, $2 as p")
            .fetchOne(LocalDateTime.DISTANT_FUTURE, LocalDateTime.DISTANT_PAST)
        assertEquals(LocalDateTime.DISTANT_FUTURE, dateTimeResult.get(0))
        assertEquals(LocalDateTime.DISTANT_PAST, dateTimeResult.get(1))

        // 3. Test Instant (timestamptz) mapping
        val instantResult = session.createNativeQuery("SELECT $1 as f, $2 as p")
            .fetchOne(Instant.DISTANT_FUTURE, Instant.DISTANT_PAST)
        assertEquals(Instant.DISTANT_FUTURE, instantResult.get(0))
        assertEquals(Instant.DISTANT_PAST, instantResult.get(1))
    }

    @Test
    fun `test Date overlap with infinity throws exception via DB`() {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)

        val pgEpochDays = 10957L

        // Date that mathematically converts to Int.MAX_VALUE when mapping to Postgres
        val badFutureDays = Int.MAX_VALUE.toLong() + pgEpochDays
        val badFutureDate = java.time.LocalDate.ofEpochDay(badFutureDays).toKotlinLocalDate()

        val exFuture = assertFailsWith<OctaviusTypeException> {
            session.createNativeQuery("SELECT $1").fetchOne(badFutureDate)
        }
        assertEquals(true, exFuture.cause?.message?.contains("overlaps with PostgreSQL infinity representation"))

        // Date that mathematically converts to Int.MIN_VALUE when mapping to Postgres
        val badPastDays = Int.MIN_VALUE.toLong() + pgEpochDays
        val badPastDate = java.time.LocalDate.ofEpochDay(badPastDays).toKotlinLocalDate()

        val exPast = assertFailsWith<OctaviusTypeException> {
            session.createNativeQuery("SELECT $1").fetchOne(badPastDate)
        }
        assertEquals(true, exPast.cause?.message?.contains("overlaps with PostgreSQL infinity representation"))
    }
}
