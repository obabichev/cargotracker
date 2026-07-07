package org.eclipse.cargotracker.cdi.proxy

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.context.RequestScoped

/**
 * Fixtures for `WeldFinalClassProxyabilityTest`. Each class illustrates one shape referenced
 * from the KTIJ ticket on WELD-001435 (Kotlin final class + normal CDI scope).
 *
 * Splitting them out of the test file keeps the failure/success matrix easy to scan and lets
 * Weld see each type as a plain top-level class (Weld does not proxy nested/anonymous types).
 */

/** Kotlin's default: `class` is final. `@RequestScoped` is normal-scoped → Weld needs a
 *  subclass proxy → deploy fails with WELD-001435. */
@RequestScoped
class FinalRequestScopedBean {
    fun greet(name: String) = "hi $name"
}

/** Same trap on `@ApplicationScoped`. */
@ApplicationScoped
class FinalApplicationScopedBean {
    fun greet(name: String) = "hi $name"
}

/** Fix 1: `open class` (and every public method `open`) lets Weld subclass and override. */
@RequestScoped
open class OpenRequestScopedBean {
    open fun greet(name: String) = "hi $name"
}

/** Fix 2: bean type is an interface → Weld uses a JDK dynamic interface proxy, class finality
 *  is irrelevant. The implementation class can stay `final` (Kotlin default). */
interface Greeter {
    fun greet(name: String): String
}

@RequestScoped
class InterfaceBackedBean : Greeter {
    override fun greet(name: String) = "hi $name"
}

/** Baseline: `@Dependent` is not a normal scope — Weld hands out the real instance, no proxy
 *  is generated, so class finality doesn't matter. Included to show *what* causes the trap
 *  (proxying) versus what doesn't. */
@Dependent
class FinalDependentBean {
    fun greet(name: String) = "hi $name"
}
