package io.github.octaviusframework.driver.composite

import io.github.octaviusframework.driver.jdbc.getOctaviusConnection
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.query.get
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.type.container.PgComposite
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.reflect.KType

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ManualCompositeIntegrationTest {

    data class PaymentInfo(val amount: Int, val currency: String)

    class PaymentInfoResultConverter : ResultConverter<PaymentInfo> {
        override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
            return expectedType.classifier == PaymentInfo::class && sourceType is PgType.Composite && sourceType.name == "payment_info"
        }

        override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType): PaymentInfo {
            val composite = source as PgComposite
            val amount = composite.get<Int>("amount")
            val currency = composite.get<String>("currency")
            return PaymentInfo(amount, currency)
        }
    }

    class PaymentInfoParameterConverter : ParameterConverter<PaymentInfo> {
        override fun canConvert(source: Any, expectedOid: Int?, typeManager: TypeManager): Boolean {
            return source is PaymentInfo
        }

        override fun convert(source: Any, expectedOid: Int?, context: SerializationContext, typeManager: TypeManager): Any {
            val payment = source as PaymentInfo
            
            // Tworzenie kompozytu jest znacznie czystsze z użyciem TypeManager
            val composite = if (expectedOid != null) {
                typeManager.createComposite(expectedOid)
            } else {
                typeManager.createComposite("payment_info")
            }
            
            // Do atrybutów odwołujemy się po nazwie
            composite["amount"] = payment.amount
            composite["currency"] = payment.currency
            
            return composite
        }
    }

    @BeforeAll
    fun setup() {
        val conn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            conn.createNativeQuery("DROP TABLE IF EXISTS orders CASCADE").execute()
            conn.createNativeQuery("DROP TYPE IF EXISTS payment_info CASCADE").execute()
            conn.createNativeQuery("CREATE TYPE payment_info AS (amount int, currency text)").execute()
            conn.createNativeQuery("CREATE TABLE orders (id int PRIMARY KEY, payment payment_info)").execute()
        } finally {
            conn.close()
        }
    }

    @AfterAll
    fun teardown() {
        val conn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            conn.createNativeQuery("DROP TABLE IF EXISTS orders CASCADE").execute()
            conn.createNativeQuery("DROP TYPE IF EXISTS payment_info CASCADE").execute()
        } finally {
            conn.close()
        }
    }

    @Test
    fun testTransactionWithManualCompositeMapper() {
        val conn = getOctaviusConnection("jdbc:octavius://localhost:5432/octavius_test", "postgres", "1234")
        try {
            // Wymuszamy pobranie nowych typów (w tym payment_info)
            conn.reloadTypes()
            
            // Rejestrujemy nasze ręczne mappery
            conn.types.registerResultConverter(PaymentInfoResultConverter())
            conn.types.registerParameterConverter(PaymentInfoParameterConverter())

            conn.transactions.transaction {
                val payment = PaymentInfo(1500, "PLN")
                
                // Insert przy użyciu NamedQuery i rzutowania na typ payment_info
                val insertQuery = "INSERT INTO orders (id, payment) VALUES (1, @payment)"
                conn.createNamedQuery(insertQuery).update("payment" to payment)

                // Pobieramy wewnątrz transakcji
                val selectQuery = "SELECT payment FROM orders WHERE id = 1"
                val resultRow = conn.createNativeQuery(selectQuery).fetchOne()
                assertNotNull(resultRow)

                val fetchedPayment = resultRow.get<PaymentInfo>("payment")
                assertEquals(1500, fetchedPayment.amount)
                assertEquals("PLN", fetchedPayment.currency)
            }
            
            // Sprawdzenie poza transakcją
            val countRows = conn.createNativeQuery("SELECT COUNT(*) FROM orders").fetchOne().get<Long>(0)
            assertEquals(1L, countRows)

        } finally {
            conn.close()
        }
    }
}

