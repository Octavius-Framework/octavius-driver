package io.github.octaviusframework.annotation

/**
 * Marks a Kotlin data class as standing for a PostgreSQL `COMPOSITE` type, for a scanner to find.
 *
 * The annotation registers nothing on its own; it is what a classpath scanner looks for. Registering by hand
 * with `typeManager.registerAutoComposite<T>()` needs no annotation.
 *
 * Properties are matched to attributes by converting `camelCase` to `snake_case`; [PgName] overrides that for
 * the one that does not follow it.
 *
 * Named with a `Type` suffix because `PgComposite` is already the driver's decoded composite **value**, and one
 * name for two things at different levels helps nobody.
 *
 * @property name The type's name in the database, or empty to derive it from the class name.
 * @property schema The schema the type lives in, or empty to resolve it through the search path.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PgCompositeType(val name: String = "", val schema: String = "")
