package io.github.octaviusframework.driver.composite

import io.github.octaviusframework.driver.jdbc.getOctaviusConnection
import io.github.octaviusframework.driver.query.get
import io.github.octaviusframework.driver.container.PgMultirange
import io.github.octaviusframework.driver.container.PgRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutoCompositeIntegrationTest {

    data class PersonProfile(val firstName: String, val lastName: String)

    data class EmployeeData(
        val profile: PersonProfile,
        val roles: List<String>,
        val activePeriod: PgRange,
        val scheduleShifts: List<PgRange>,
        val availableDays: PgMultirange
    )

    @BeforeAll
    fun setup() {
        val conn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            conn.createNativeQuery("DROP TYPE IF EXISTS person_profile CASCADE").execute()
            conn.createNativeQuery("CREATE TYPE person_profile AS (first_name text, last_name text)").execute()

            conn.createNativeQuery("DROP TYPE IF EXISTS employee_data CASCADE").execute()
            conn.createNativeQuery("CREATE TYPE employee_data AS (" +
                    "profile person_profile, " +
                    "roles text[], " +
                    "active_period daterange, " +
                    "schedule_shifts tsrange[], " +
                    "available_days datemultirange" +
                    ")").execute()
        } finally {
            conn.close()
        }
    }

    @AfterAll
    fun teardown() {
        val conn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            conn.createNativeQuery("DROP SCHEMA public CASCADE").execute()
            conn.createNativeQuery("CREATE SCHEMA public").execute()
        } finally {
            conn.close()
        }
    }

    @Test
    fun testEverythingWithNativeQuery() {
        val conn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            conn.reloadTypes()
            conn.typeRegistry.registerAutoCompositeType<PersonProfile>("person_profile")
            conn.typeRegistry.registerAutoCompositeType<EmployeeData>("employee_data")

            val activePeriod = conn.types.createRange(
                "daterange",
                lower = LocalDate(2023, 1, 1),
                upper = LocalDate(2023, 12, 31)
            )

            val shift1 = conn.types.createRange(
                "tsrange",
                lower = LocalDateTime(2023, 5, 1, 8, 0),
                upper = LocalDateTime(2023, 5, 1, 16, 0)
            )
            val shift2 = conn.types.createRange(
                "tsrange",
                lower = LocalDateTime(2023, 5, 2, 9, 0),
                upper = LocalDateTime(2023, 5, 2, 17, 0)
            )

            val availableDays = conn.types.createMultirange(
                "datemultirange",
                ranges = arrayOf(
                    conn.types.createRange("daterange", lower = LocalDate(2023, 6, 1), upper = LocalDate(2023, 6, 10)),
                    conn.types.createRange("daterange", lower = LocalDate(2023, 7, 1), upper = LocalDate(2023, 7, 15))
                )
            )

            val emp = EmployeeData(
                profile = PersonProfile("Jan", "Kowalski"),
                roles = listOf("admin", "user"),
                activePeriod = activePeriod,
                scheduleShifts = listOf(shift1, shift2),
                availableDays = availableDays
            )

            val query = $$"SELECT $1 AS emp"
            println("Sending EmployeeData Native: $emp")
            val resultRow = conn.createNativeQuery(query).fetchOne(emp)
            println("Result Row Native: $resultRow")

            val parsedEmp = resultRow.get<EmployeeData>("emp")
            println("Parsed EmployeeData Native: $parsedEmp")

            assertEquals("Jan", parsedEmp.profile.firstName)
            assertEquals("Kowalski", parsedEmp.profile.lastName)
            assertEquals(listOf("admin", "user"), parsedEmp.roles)
            
            assertEquals(LocalDate(2023, 1, 1), parsedEmp.activePeriod.lowerBound<LocalDate>())
            assertEquals(LocalDate(2023, 12, 31), parsedEmp.activePeriod.upperBound<LocalDate>())

            assertEquals(2, parsedEmp.scheduleShifts.size)
            assertEquals(LocalDateTime(2023, 5, 1, 8, 0), parsedEmp.scheduleShifts[0].lowerBound<LocalDateTime>())
            
            assertEquals(2, parsedEmp.availableDays.ranges.size)
            assertEquals(LocalDate(2023, 6, 1), parsedEmp.availableDays.ranges[0].lowerBound<LocalDate>())

        } finally {
            conn.close()
        }
    }

    @Test
    fun testEverythingWithNamedParameterQuery() {
        val conn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            conn.reloadTypes()
            conn.typeRegistry.registerAutoCompositeType<PersonProfile>("person_profile")
            conn.typeRegistry.registerAutoCompositeType<EmployeeData>("employee_data")

            val activePeriod = conn.types.createRange(
                "daterange",
                lower = LocalDate(2023, 1, 1),
                upper = LocalDate(2023, 12, 31)
            )

            val shift1 = conn.types.createRange(
                "tsrange",
                lower = LocalDateTime(2023, 5, 1, 8, 0),
                upper = LocalDateTime(2023, 5, 1, 16, 0)
            )
            val shift2 = conn.types.createRange(
                "tsrange",
                lower = LocalDateTime(2023, 5, 2, 9, 0),
                upper = LocalDateTime(2023, 5, 2, 17, 0)
            )

            val availableDays = conn.types.createMultirange(
                "datemultirange",
                ranges = arrayOf(
                    conn.types.createRange("daterange", lower = LocalDate(2023, 6, 1), upper = LocalDate(2023, 6, 10)),
                    conn.types.createRange("daterange", lower = LocalDate(2023, 7, 1), upper = LocalDate(2023, 7, 15))
                )
            )

            val emp = EmployeeData(
                profile = PersonProfile("Jan", "Kowalski"),
                roles = listOf("admin", "user"),
                activePeriod = activePeriod,
                scheduleShifts = listOf(shift1, shift2),
                availableDays = availableDays
            )

            val query = "SELECT @employee AS emp"
            val resultRow = conn.createNamedQuery(query).fetchOne("employee" to emp)

            val parsedEmp = resultRow.get<EmployeeData>("emp")

            assertEquals("Jan", parsedEmp.profile.firstName)
            assertEquals("Kowalski", parsedEmp.profile.lastName)
            assertEquals(listOf("admin", "user"), parsedEmp.roles)
            
            assertEquals(LocalDate(2023, 1, 1), parsedEmp.activePeriod.lowerBound<LocalDate>())
            assertEquals(LocalDate(2023, 12, 31), parsedEmp.activePeriod.upperBound<LocalDate>())

            assertEquals(2, parsedEmp.scheduleShifts.size)
            assertEquals(LocalDateTime(2023, 5, 1, 8, 0), parsedEmp.scheduleShifts[0].lowerBound<LocalDateTime>())
            
            assertEquals(2, parsedEmp.availableDays.ranges.size)
            assertEquals(LocalDate(2023, 6, 1), parsedEmp.availableDays.ranges[0].lowerBound<LocalDate>())
            
        } finally {
            conn.close()
        }
    }
}
