package io.github.octaviusframework.type

import java.math.BigDecimal

/**
 * On the JVM, [BigDecimal] is `java.math.BigDecimal` - the same class the driver's `numeric` codec produces,
 * so nothing is wrapped and nothing is converted.
 */
actual typealias BigDecimal = BigDecimal
