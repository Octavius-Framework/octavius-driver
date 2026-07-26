package io.github.octaviusframework.driver.composite

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.row.get
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
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            session.createNativeQuery("DROP TYPE IF EXISTS person_profile CASCADE").execute()
            session.createNativeQuery("CREATE TYPE person_profile AS (first_name text, last_name text)").execute()

            session.createNativeQuery("DROP TYPE IF EXISTS employee_data CASCADE").execute()
            session.createNativeQuery("CREATE TYPE employee_data AS (" +
                    "profile person_profile, " +
                    "roles text[], " +
                    "active_period daterange, " +
                    "schedule_shifts tsrange[], " +
                    "available_days datemultirange" +
                    ")").execute()
        } finally {
            session.close()
        }
    }

    @AfterAll
    fun teardown() {
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            session.createNativeQuery("DROP SCHEMA public CASCADE").execute()
            session.createNativeQuery("CREATE SCHEMA public").execute()
        } finally {
            session.close()
        }
    }

    @Test
    fun testEverythingWithNativeQuery() {
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            session.reloadTypes()
            session.types.registerAutoComposite<PersonProfile>("person_profile")
            session.types.registerAutoComposite<EmployeeData>("employee_data")

            val activePeriod = session.types.createRange(
                "daterange",
                lower = LocalDate(2023, 1, 1),
                upper = LocalDate(2023, 12, 31)
            )

            val shift1 = session.types.createRange(
                "tsrange",
                lower = LocalDateTime(2023, 5, 1, 8, 0),
                upper = LocalDateTime(2023, 5, 1, 16, 0)
            )
            val shift2 = session.types.createRange(
                "tsrange",
                lower = LocalDateTime(2023, 5, 2, 9, 0),
                upper = LocalDateTime(2023, 5, 2, 17, 0)
            )

            val availableDays = session.types.createMultirange(
                "datemultirange",
                ranges = arrayOf(
                    session.types.createRange("daterange", lower = LocalDate(2023, 6, 1), upper = LocalDate(2023, 6, 10)),
                    session.types.createRange("daterange", lower = LocalDate(2023, 7, 1), upper = LocalDate(2023, 7, 15))
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
            val resultRow = session.createNativeQuery(query).fetchOne(emp)
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
            session.close()
        }
    }

    @Test
    fun testEverythingWithNamedParameterQuery() {
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            session.reloadTypes()
            session.types.registerAutoComposite<PersonProfile>("person_profile")
            session.types.registerAutoComposite<EmployeeData>("employee_data")

            val activePeriod = session.types.createRange(
                "daterange",
                lower = LocalDate(2023, 1, 1),
                upper = LocalDate(2023, 12, 31)
            )

            val shift1 = session.types.createRange(
                "tsrange",
                lower = LocalDateTime(2023, 5, 1, 8, 0),
                upper = LocalDateTime(2023, 5, 1, 16, 0)
            )
            val shift2 = session.types.createRange(
                "tsrange",
                lower = LocalDateTime(2023, 5, 2, 9, 0),
                upper = LocalDateTime(2023, 5, 2, 17, 0)
            )

            val availableDays = session.types.createMultirange(
                "datemultirange",
                ranges = arrayOf(
                    session.types.createRange("daterange", lower = LocalDate(2023, 6, 1), upper = LocalDate(2023, 6, 10)),
                    session.types.createRange("daterange", lower = LocalDate(2023, 7, 1), upper = LocalDate(2023, 7, 15))
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
            val resultRow = session.createNamedQuery(query).fetchOne("employee" to emp)

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
            session.close()
        }
    }
}
