package io.github.octaviusframework.driver.annotation

/**
 * Annotation used to specify a custom key for a property
 * during object to/from map conversion or composite mapping.
 * It is used for composites registered by `registerAutoComposite` method to map Kotlin object
 * properties names to composite type attributes names.
 *
 * @property name Key name that will be used in the map or database composite.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class MapKey(val name: String)
