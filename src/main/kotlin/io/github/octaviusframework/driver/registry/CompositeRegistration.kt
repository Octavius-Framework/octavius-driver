package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.identifier.CaseConvention
import io.github.octaviusframework.driver.identifier.QualifiedName

data class CompositeRegistration(
    val qualifiedName: QualifiedName,
    val pgConvention: CaseConvention,
    val kotlinConvention: CaseConvention
)