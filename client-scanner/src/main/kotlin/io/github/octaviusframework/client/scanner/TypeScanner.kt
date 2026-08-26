package io.github.octaviusframework.client.scanner

import io.github.classgraph.ClassGraph
import io.github.octaviusframework.annotation.DynamicallyMappable
import io.github.octaviusframework.annotation.PgCompositeType
import io.github.octaviusframework.annotation.PgEnumType
import io.github.octaviusframework.client.OctaviusClient
import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason
import io.github.octaviusframework.driver.exception.OctaviusException
import io.github.octaviusframework.identifier.CaseConvention
import io.github.octaviusframework.driver.identifier.CaseConverter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

private val logger = KotlinLogging.logger {}

/**
 * A class the scan registered, and the name it was registered under.
 *
 * @property kClass The Kotlin class.
 * @property name What the database calls it - a type name for an enum or composite, a discriminator for a
 * `dynamic_dto` class.
 */
data class RegisteredType(val kClass: KClass<*>, val name: String) {
    override fun toString(): String = "${kClass.simpleName} -> $name"
}

/**
 * What a scan found and registered.
 *
 * Worth logging or asserting on at startup. A scan that finds nothing is the failure this exists to make
 * visible: a package name with a typo in it registers nothing and raises nothing, and the first sign of it is
 * a query months later coming back with an unmapped column.
 *
 * @property enums Enums registered as PostgreSQL `ENUM` types.
 * @property composites Data classes registered as PostgreSQL `COMPOSITE` types.
 * @property dynamicTypes Classes registered as `dynamic_dto` discriminators.
 * @property unresolved The enums and composites whose type the database does not have. They are registered all
 * the same and appear in [enums] or [composites] as well - registering a name the catalogue does not carry yet
 * is allowed, and works once the type exists and `reloadTypes()` has run. It is reported because the other way
 * to get here is a name with a typo in it, and that one is otherwise silent until a query cannot map a column.
 */
data class ScanReport(
    val enums: List<RegisteredType>,
    val composites: List<RegisteredType>,
    val dynamicTypes: List<RegisteredType>,
    val unresolved: List<RegisteredType> = emptyList()
) {
    /** How many classes were registered in total. */
    val total: Int get() = enums.size + composites.size + dynamicTypes.size

    /** Whether the scan registered nothing at all. */
    fun isEmpty(): Boolean = total == 0

    override fun toString(): String = buildString {
        append("ScanReport(${enums.size} enums, ${composites.size} composites, ${dynamicTypes.size} dynamic types")
        if (unresolved.isNotEmpty()) append(", ${unresolved.size} against types the database does not have")
        append(")")
    }
}

/**
 * Finds the annotated classes in [packages] and registers each with this client.
 *
 * Three annotations are looked for, all of them from the multiplatform `annotations` module so that they can
 * sit on classes shared with another platform:
 * [PgEnumType], [PgCompositeType] and [DynamicallyMappable].
 *
 * ```kotlin
 * val db = OctaviusClient.fromDataSource(dataSource)
 * db.dynamicTypes.install()
 * val found = db.registerAnnotatedTypes("com.roma.domain", "com.roma.dto")
 * logger.info { "Octavius registered $found" }
 * ```
 *
 * This does the same thing as calling `typeManager.registerEnum`, `registerAutoComposite` and
 * `dynamicTypes.register` by hand, and nothing the hand-written calls cannot do - the annotations carry
 * everything those take, case conventions included. What it saves is naming thirty classes at startup and
 * remembering to add the thirty-first.
 *
 * A name the database has no type for is registered like any other and reported in [ScanReport.unresolved]:
 * registering ahead of a type that does not exist yet is a working flow, so it is not refused, only said out
 * loud.
 *
 * Registration is global to the database this client is connected to, so this belongs at startup and once.
 *
 * @param packages The packages to scan, and their subpackages. At least one; scanning everything on the
 * classpath is slow enough to be worth refusing.
 * @return What was found and registered.
 * @throws InvalidOperationException `INVALID_ARGUMENT` where no package was named, or where an annotation sits
 * on a class it cannot describe.
 */
fun OctaviusClient.registerAnnotatedTypes(vararg packages: String): ScanReport =
    registerAnnotatedTypes(packages.toList())

/**
 * Finds the annotated classes in [packages] and registers each with this client.
 *
 * @param packages The packages to scan, and their subpackages. At least one.
 * @param classLoader Where to look, for an application whose classes are not on the loader that loaded this
 * one - an OSGi container, a plugin host. `null` uses the default.
 * @return What was found and registered.
 * @throws InvalidOperationException `INVALID_ARGUMENT` where no package was named, or where an annotation sits
 * on a class it cannot describe.
 */
fun OctaviusClient.registerAnnotatedTypes(
    packages: List<String>,
    classLoader: ClassLoader? = null
): ScanReport {
    if (packages.isEmpty()) {
        throw InvalidOperationException(
            InvalidOperationExceptionReason.INVALID_ARGUMENT,
            details = "Name at least one package to scan. Scanning the whole classpath is slow enough that " +
                "refusing is kinder than doing it."
        )
    }

    val found = findAnnotated(packages, classLoader)

    // The type manager wants a session, and every dynamic registration opens one of its own the first time.
    // Kept apart rather than nested: outside a transaction each `execute` borrows its own connection, and a
    // pool of one would have the inner borrow wait on the outer for as long as it is given.
    val enums = mutableListOf<RegisteredType>()
    val composites = mutableListOf<RegisteredType>()
    val unresolved = mutableListOf<RegisteredType>()

    execute {
        for (kClass in found.enums) {
            val annotation = kClass.findAnnotation<PgEnumType>()!!
            typeManager.registerEnum(
                kClass,
                annotation.name,
                annotation.schema,
                annotation.pgConvention,
                annotation.kotlinConvention
            )
            enums += RegisteredType(kClass, annotation.name.ifEmpty { derivedNameOf(kClass) })
        }
        for (kClass in found.composites) {
            val annotation = kClass.findAnnotation<PgCompositeType>()!!
            typeManager.registerAutoComposite(kClass, annotation.name, annotation.schema)
            composites += RegisteredType(kClass, annotation.name.ifEmpty { derivedNameOf(kClass) })
        }

        // Reported, not refused. Registering a name the catalogue does not have yet is a working flow - a
        // reload afterwards picks the type up and the converters survive it - so a missing type here is a
        // question rather than an answer.
        for (registered in enums + composites) {
            try {
                typeManager.resolveOid(registered.name, schemaOf(registered.kClass))
            } catch (_: OctaviusException) {
                unresolved += registered
            }
        }
    }

    val dynamicTypes = found.dynamic.map { kClass ->
        dynamicTypes.register(kClass)
        RegisteredType(kClass, kClass.findAnnotation<DynamicallyMappable>()!!.typeName)
    }

    val report = ScanReport(enums, composites, dynamicTypes, unresolved)
    if (report.isEmpty()) {
        logger.warn {
            "Scanned ${packages.joinToString(", ")} and found nothing annotated. Check the package names: " +
                "a scan that matches no class registers nothing and raises nothing."
        }
    } else {
        logger.info { "Registered $report from ${packages.joinToString(", ")}" }
        logger.debug { "enums=${enums}, composites=${composites}, dynamic=${dynamicTypes}" }
    }
    if (unresolved.isNotEmpty()) {
        logger.warn {
            "Registered against types the database does not have: ${unresolved.joinToString(", ")}. Either a " +
                "name is wrong, or the types are created after this pool was built - in which case " +
                "reloadTypes() once they exist. ScanReport.unresolved carries the same list."
        }
    }
    return report
}

/** The schema the class's annotation named, or empty for "resolve through the search path". */
private fun schemaOf(kClass: KClass<*>): String =
    kClass.findAnnotation<PgEnumType>()?.schema
        ?: kClass.findAnnotation<PgCompositeType>()?.schema
        ?: ""

/** The three lists a scan produces, before anything is registered. */
private class Annotated(
    val enums: List<KClass<*>>,
    val composites: List<KClass<*>>,
    val dynamic: List<KClass<*>>
)

private fun findAnnotated(packages: List<String>, classLoader: ClassLoader?): Annotated {
    val graph = ClassGraph()
        .enableAnnotationInfo()
        .acceptPackages(*packages.toTypedArray())
        .let { if (classLoader != null) it.overrideClassLoaders(classLoader) else it }

    return graph.scan().use { result ->
        fun classesWith(annotation: Class<*>): List<KClass<*>> =
            result.getClassesWithAnnotation(annotation.name).map { it.loadClass().kotlin }

        Annotated(
            enums = classesWith(PgEnumType::class.java),
            composites = classesWith(PgCompositeType::class.java),
            dynamic = classesWith(DynamicallyMappable::class.java)
        )
    }
}

/**
 * What the driver derives for a class registered without a name, so the report says what was really used.
 *
 * The driver's own converter rather than a rule of the same shape written here: two implementations of one
 * convention drift, and the one that drifts silently is the one only a report reads.
 */
private fun derivedNameOf(kClass: KClass<*>): String =
    CaseConverter.convert(kClass.simpleName!!, CaseConvention.PASCAL_CASE, CaseConvention.SNAKE_CASE_LOWER)
