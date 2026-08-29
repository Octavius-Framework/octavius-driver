package io.github.octaviusframework.type

/**
 * The arbitrary-precision decimal a PostgreSQL `numeric` column carries, in a form `commonMain` can name.
 *
 * On the **JVM** it is a `typealias` for `java.math.BigDecimal` and nothing else: the driver decodes `numeric`
 * into exactly that class, so a shared class declaring this property is the same class the driver already
 * fills, with no conversion anywhere and no second type to keep in step.
 *
 * On **JS** it is a wrapper around the decimal's text. JavaScript's `Number` is a 64-bit float, so a value
 * PostgreSQL stored exactly - a price, a measurement - would come back rounded from any arithmetic type the
 * platform has. Keeping the digits means the frontend can display and re-send them unchanged; arithmetic on
 * them is the application's own business, with whichever JS decimal library it prefers.
 *
 * Reach for it in a class shared with another platform. Backend-only code can go on writing
 * `java.math.BigDecimal`, which on the JVM is the same declaration under another name.
 *
 * ```kotlin
 * @Serializable
 * @DynamicallyMappable("tribute_assessment")
 * data class TributeAssessment(
 *     val province: String,
 *     @Contextual val denarii: BigDecimal
 * )
 * ```
 *
 * The `@Contextual` is what routes it through [io.github.octaviusframework.serializer.BigDecimalAsNumberSerializer]
 * when the payload is JSON, which is where the precision would otherwise be lost a second time. See
 * [octaviusSerializersModule][io.github.octaviusframework.serializer.octaviusSerializersModule].
 */
expect class BigDecimal {

    /**
     * Reads a decimal from its plain text, which is the one way of building one that means the same thing on
     * both platforms - `java.math.BigDecimal(String)` on the JVM, the digits themselves on JS.
     *
     * @param value The decimal written out, as `numeric` renders it.
     */
    constructor(value: String)
}
