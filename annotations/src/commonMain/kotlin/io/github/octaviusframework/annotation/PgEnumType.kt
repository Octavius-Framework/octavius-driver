package io.github.octaviusframework.annotation

import io.github.octaviusframework.identifier.CaseConvention

/**
 * Marks a Kotlin enum as standing for a PostgreSQL `ENUM` type, for a scanner to find.
 *
 * The annotation registers nothing on its own. It is what a classpath scanner looks for, so that an
 * application with thirty enums says where they live once instead of naming each of them at startup;
 * registering by hand with `typeManager.registerEnum<T>()` needs no annotation and never will.
 *
 * Leaving [name] empty derives it from the class name, `PascalCase` to `snake_case`, which is the same rule
 * the manual call uses. Leaving [schema] empty resolves the type through the search path.
 *
 * [pgConvention] and [kotlinConvention] carry over from the manual call with the same defaults, so an enum
 * whose labels are lowercase in the database says so here rather than having to drop out of the scan.
 *
 * @property name The type's name in the database, or empty to derive it from the class name.
 * @property schema The schema the type lives in, or empty to resolve it through the search path.
 * @property pgConvention How the labels are written in PostgreSQL.
 * @property kotlinConvention How the constants are written in Kotlin.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PgEnumType(
    val name: String = "",
    val schema: String = "",
    val pgConvention: CaseConvention = CaseConvention.SNAKE_CASE_UPPER,
    val kotlinConvention: CaseConvention = CaseConvention.PASCAL_CASE
)
