package io.github.octaviusframework.annotation

/**
 * Names the `dynamic_dto` discriminator a class stands for.
 *
 * The name travels in the `type_name` attribute of the composite and is what turns a JSON payload back into
 * one class rather than another. It is what SQL writes when it builds a value by hand -
 * `dynamic_dto('land_grant', jsonb_build_object(...))` - so it has to be stated rather than derived: the
 * discriminator lives in the data, and a name derived from the class would change silently when the class was
 * renamed, leaving every row written before that unreadable.
 *
 * The class also has to be `@Serializable`; the payload is JSON and nothing else reads it.
 *
 * ```kotlin
 * @Serializable
 * @DynamicallyMappable("land_grant")
 * data class LandGrant(val province: String, val iugera: Int)
 * ```
 *
 * @property typeName The discriminator, matching what the database stores in `type_name`.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DynamicallyMappable(val typeName: String)
