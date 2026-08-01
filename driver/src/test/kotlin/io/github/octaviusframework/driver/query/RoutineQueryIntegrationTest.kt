package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.row.get
import io.github.octaviusframework.driver.session.OctaviusSession
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RoutineQueryIntegrationTest {

    companion object {
        private lateinit var session: OctaviusSession

        @JvmStatic
        @BeforeAll
        fun setup() {
            val props = OctaviusProperties()
            props.user = "postgres"
            props.password = "1234"
            
            session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)
            
            // Create a test function
            session.createNativeQuery("""
                CREATE OR REPLACE FUNCTION add_numbers(a INT, b INT) RETURNS INT AS $$
                BEGIN
                    RETURN a + b;
                END;
                $$ LANGUAGE plpgsql;
            """).execute()

            // Create a test procedure
            session.createNativeQuery("""
                CREATE OR REPLACE PROCEDURE update_test_val(INOUT val INT, p_add INT) AS $$
                BEGIN
                    val := val + p_add;
                END;
                $$ LANGUAGE plpgsql;
            """).execute()
            
            // Create a table-returning function
            session.createNativeQuery("""
                CREATE OR REPLACE FUNCTION get_series(n INT) RETURNS TABLE(num INT) AS $$
                BEGIN
                    RETURN QUERY SELECT generate_series(1, n);
                END;
                $$ LANGUAGE plpgsql;
            """).execute()

            // Create a void-returning function
            session.createNativeQuery("""
                CREATE OR REPLACE FUNCTION returns_void() RETURNS VOID AS $$
                BEGIN
                END;
                $$ LANGUAGE plpgsql;
            """).execute()

            // Create a procedure without OUT parameters
            session.createNativeQuery("""
                CREATE OR REPLACE PROCEDURE do_nothing() AS $$
                BEGIN
                END;
                $$ LANGUAGE plpgsql;
            """).execute()
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            session.createNativeQuery("DROP FUNCTION IF EXISTS add_numbers(INT, INT)").execute()
            session.createNativeQuery("DROP PROCEDURE IF EXISTS update_test_val(INT, INT)").execute()
            session.createNativeQuery("DROP FUNCTION IF EXISTS get_series(INT)").execute()
            session.createNativeQuery("DROP FUNCTION IF EXISTS returns_void()").execute()
            session.createNativeQuery("DROP PROCEDURE IF EXISTS do_nothing()").execute()
        }
    }

    @Test
    fun testCallFunction() {
        val result = session.createNativeQuery("SELECT add_numbers($1, $2)").fetchFieldStrict<Int>(10, 20)
        assertEquals(30, result)
    }

    @Test
    fun testCallFunctionWithNamedParameters() {
        val result = session.createNamedQuery("SELECT add_numbers(@a, @b)")
            .fetchFieldStrict<Int>("a" to 5, "b" to 15)
        assertEquals(20, result)
    }
    
    @Test
    fun testCallTableFunction() {
        val rows = session.createNativeQuery("SELECT * FROM get_series($1)").fetchFields<Int>(5)
        assertEquals(listOf(1, 2, 3, 4, 5), rows)
    }

    @Test
    fun testCallProcedure() {
        // Procedure with INOUT requires CALL and returns a row in PostgreSQL
        val resultRow = session.createNativeQuery("CALL update_test_val($1, $2)").fetchRowStrict(100, 50)
        assertEquals(150, resultRow.get<Int>(0))
    }

    @Test
    fun testCallVoidFunction() {
        val result = session.createNativeQuery("SELECT returns_void()").fetchField<Unit>()
        assertEquals(Unit, result)
    }

    @Test
    fun testCallProcedureWithoutOut() {
        session.createNativeQuery("CALL do_nothing()").execute()
        val updateCount = session.createNativeQuery("CALL do_nothing()").update()
        assertEquals(0L, updateCount)
    }
}
