package org.eclipse.cargotracker.cdi.companion

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import java.util.logging.Logger

/**
 * Fixtures for the KTIJ ticket on CDI meta-annotations on companion object members without
 * `@JvmStatic` (see finding 09). Each producer bean expresses the same *source-level* intent
 * — "provide a `Logger` bean to the container" — but only the shapes where `@Produces` lands
 * on a method of the bean-defining class actually work.
 *
 * `disableDiscovery()` + `addBeanClasses(...)` in the Weld tests keeps each bootstrap
 * minimal, so exactly one producer is in scope per test.
 */

/**
 * BROKEN — `@Produces` on a `companion object` member without `@JvmStatic`. The `@Produces`
 * annotation is emitted on `BrokenCompanionProducerBean$Companion.logger()`, *not* on
 * `BrokenCompanionProducerBean.logger()` (which doesn't exist on the outer class at all).
 * CDI scans the bean-defining class and finds no producer, so `@Inject Logger` at any
 * injection point fails at deploy with `WELD-001408 Unsatisfied dependencies`.
 */
@ApplicationScoped
open class BrokenCompanionProducerBean {
    companion object {
        @Produces
        fun logger(): Logger = Logger.getLogger("companion-produced")
    }
}

/**
 * CDI 4.0 candidate — `@JvmStatic` promotes the companion method to a real JVM static on
 * `JvmStaticCompanionProducerBean`, and `@Produces` is emitted on both the companion body
 * and the outer static bridge. Whether Weld picks it up depends on the CDI/Weld version:
 * CDI 4.0 (Jakarta EE 10) added "static producer methods"; this fixture lets the runtime
 * test record what Weld 5.1 actually does.
 */
@ApplicationScoped
open class JvmStaticCompanionProducerBean {
    companion object {
        @JvmStatic
        @Produces
        fun logger(): Logger = Logger.getLogger("jvmstatic-produced")
    }
}

/**
 * The canonical fix — a plain instance `@Produces` method on an `@ApplicationScoped` bean.
 * Mirrors Cargo Tracker's own `LoggerProducer.java` one-to-one; no Kotlin-specific
 * ceremony. This is what the KTIJ "Move to instance method of enclosing class" quick-fix
 * would generate.
 */
@ApplicationScoped
open class InstanceProducerBean {
    @Produces
    open fun logger(): Logger = Logger.getLogger("instance-produced")
}

/**
 * Consumer that forces Weld to actually resolve the producer at deploy time. `@Dependent`
 * avoids the normal-scope client-proxy machinery, so this class staying `final` (Kotlin's
 * default) is fine — see finding 07 / KTIJ-39483 for the proxy-scope trap.
 */
@Dependent
class LoggerConsumer {
    @Inject
    lateinit var log: Logger
}
