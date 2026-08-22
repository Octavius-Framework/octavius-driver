package io.github.octaviusframework.driver.benchmarks

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.query.NativeQuery
import io.github.octaviusframework.driver.session.OctaviusSession
import org.openjdk.jmh.annotations.*
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * What a server-side prepared statement is actually worth, on the one workload where it should be
 * worth something.
 *
 * The read benchmarks elsewhere in this suite return ten thousand rows, where producing and mapping
 * them buries whatever `Parse` cost there is - which is why they say nothing about this question.
 * This one is the opposite shape: a primary-key lookup returning a single row, where the server's
 * work is one index probe and parsing the statement is a comparable share of it.
 *
 * The two pgjdbc benchmarks are the controlled half of the experiment. They run the same method
 * against the same table through the same driver, and differ in one connection property. At the
 * default `prepareThreshold` of 5, pgjdbc promotes the statement to a named server-side prepared
 * statement after the fifth execution, and every execution after that skips `Parse` and planning
 * both. At `0` it never promotes - `isOneShotQuery` returns true outright - so every execution is
 * parsed and planned afresh, which is what Octavius does for every statement by design. The gap
 * between those two is the feature's worth with the driver held constant; where `octavius` lands
 * beside them says whether anything else about the driver eats it.
 *
 * A different id is bound on every invocation, so the server answers a stream of point lookups
 * rather than the same one over and over, and a generic plan is exercised the way it would be in
 * production rather than against a single hot value.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Fork(3)
@Threads(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
open class PointLookupBenchmark {

    private lateinit var pgConnection: Connection
    private lateinit var pgPreparedStatement: PreparedStatement

    private lateinit var pgOneShotConnection: Connection
    private lateinit var pgOneShotStatement: PreparedStatement

    private lateinit var octaviusSession: OctaviusSession
    private lateinit var octaviusQuery: NativeQuery

    private val rowCount = 10000

    /** Rotated on every invocation, so no two consecutive lookups ask for the same row. */
    private var cursor = 0

    private fun nextId(): Int {
        cursor++
        if (cursor > rowCount) cursor = 1
        return cursor
    }

    @Setup(Level.Trial)
    fun setup() {
        Class.forName("org.postgresql.Driver")
        Class.forName("io.github.octaviusframework.driver.jdbc.OctaviusDriver")

        val props = Properties()
        props["user"] = "postgres"
        props["password"] = "1234"

        pgConnection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/octavius_test", props)

        // The same credentials and the same server; the property under test is the only difference.
        val oneShotProps = Properties()
        oneShotProps["user"] = "postgres"
        oneShotProps["password"] = "1234"
        oneShotProps["prepareThreshold"] = "0"

        pgOneShotConnection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/octavius_test", oneShotProps)

        pgConnection.createStatement().use { st ->
            st.execute("DROP TABLE IF EXISTS benchmark_point_lookup")
            st.execute("CREATE TABLE benchmark_point_lookup (id INT PRIMARY KEY, text_data TEXT NOT NULL)")
            st.execute("INSERT INTO benchmark_point_lookup SELECT i, 'senator ' || i FROM generate_series(1, $rowCount) i")
            // Without stats the planner is guessing, and a plan chosen by guesswork is not the plan
            // this benchmark means to time.
            st.execute("ANALYZE benchmark_point_lookup")
        }

        val sql = "SELECT id, text_data FROM benchmark_point_lookup WHERE id = ?"
        pgPreparedStatement = pgConnection.prepareStatement(sql)
        pgOneShotStatement = pgOneShotConnection.prepareStatement(sql)

        octaviusSession = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        octaviusQuery = octaviusSession.createNativeQuery(
            "SELECT id, text_data FROM benchmark_point_lookup WHERE id = $1"
        )
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        pgPreparedStatement.close()
        pgOneShotStatement.close()
        pgConnection.close()
        pgOneShotConnection.close()
        octaviusSession.close()
    }

    /**
     * Shared by both pgjdbc benchmarks on purpose: identical code on identical data, so the only
     * thing that can separate their numbers is the connection each statement came from.
     */
    private fun lookup(statement: PreparedStatement): Int {
        statement.setInt(1, nextId())
        statement.executeQuery().use { rs ->
            check(rs.next()) { "the row is seeded in setup and must be there" }
            return rs.getInt(1) + rs.getString(2).length
        }
    }

    /** pgjdbc at its default `prepareThreshold` of 5: a named server-side prepared statement. */
    @Benchmark
    fun pgjdbc_pointLookup_serverPrepared(): Int = lookup(pgPreparedStatement)

    /** The same, with `prepareThreshold=0`: parsed and planned on every execution. */
    @Benchmark
    fun pgjdbc_pointLookup_reparsedEveryTime(): Int = lookup(pgOneShotStatement)

    /** Octavius, which parses on every execution and has no other mode. */
    @Benchmark
    fun octavius_pointLookup(): Int {
        val row = octaviusQuery.fetchRowStrict(nextId())
        return row.get<Int>(0) + row.get<String>(1).length
    }
}
