package org.eclipse.cargotracker.cdi.proxy

import jakarta.enterprise.context.control.RequestContextController
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jboss.weld.environment.se.Weld
import org.jboss.weld.exceptions.UnproxyableResolutionException
import org.junit.jupiter.api.Test

/**
 * Boots Weld SE in isolation for each bean shape to reproduce the KTIJ ticket's claim: a
 * Kotlin class that is `final` (Kotlin's default) and annotated with a normal CDI scope
 * (`@RequestScoped`, `@ApplicationScoped`, …) is not proxyable — Weld cannot generate a
 * subclass client proxy for a final class.
 *
 * `disableDiscovery()` + `addBeanClasses(...)` keeps each bootstrap minimal — no `beans.xml`
 * scan, no transitive `@Inject` resolution. In Weld 5 the proxyability check fires when the
 * bean is first resolved via `select().get()`, not at `initialize()`; the failing tests wrap
 * that call. The runtime error is `WELD-001437: Bean type … is not proxyable because it is
 * final` (older Weld/GlassFish releases surface the same fault as `WELD-001435`).
 */
class WeldFinalClassProxyabilityTest {

    private fun weld(vararg beans: Class<*>): Weld =
        Weld().disableDiscovery().addBeanClasses(*beans)

    @Test
    fun `final class + @RequestScoped is not proxyable — WELD-001437 on first resolution`() {
        weld(FinalRequestScopedBean::class.java).initialize().use { container ->
            assertThatThrownBy { container.select(FinalRequestScopedBean::class.java).get() }
                .isInstanceOf(UnproxyableResolutionException::class.java)
                .hasMessageContaining("WELD-001437")
                .hasMessageContaining("FinalRequestScopedBean")
                .hasMessageContaining("not proxyable because it is final")
        }
    }

    @Test
    fun `final class + @ApplicationScoped is not proxyable — WELD-001437 on first resolution`() {
        weld(FinalApplicationScopedBean::class.java).initialize().use { container ->
            assertThatThrownBy { container.select(FinalApplicationScopedBean::class.java).get() }
                .isInstanceOf(UnproxyableResolutionException::class.java)
                .hasMessageContaining("WELD-001437")
                .hasMessageContaining("FinalApplicationScopedBean")
                .hasMessageContaining("not proxyable because it is final")
        }
    }

    @Test
    fun `open class + @RequestScoped deploys — Weld generates a subclass proxy`() {
        weld(OpenRequestScopedBean::class.java, RequestContextController::class.java)
            .initialize().use { container ->
                val controller = container.select(RequestContextController::class.java).get()
                controller.activate()
                try {
                    val bean = container.select(OpenRequestScopedBean::class.java).get()
                    assertThat(bean.greet("weld")).isEqualTo("hi weld")
                    // A Weld-generated subclass proxy — not the raw bean class.
                    assertThat(bean.javaClass).isNotEqualTo(OpenRequestScopedBean::class.java)
                    assertThat(bean).isInstanceOf(OpenRequestScopedBean::class.java)
                } finally {
                    controller.deactivate()
                }
            }
    }

    @Test
    fun `final class implementing an interface + @RequestScoped deploys — JDK dynamic proxy`() {
        weld(InterfaceBackedBean::class.java, RequestContextController::class.java)
            .initialize().use { container ->
                val controller = container.select(RequestContextController::class.java).get()
                controller.activate()
                try {
                    val bean = container.select(Greeter::class.java).get()
                    assertThat(bean.greet("weld")).isEqualTo("hi weld")
                } finally {
                    controller.deactivate()
                }
            }
    }

    @Test
    fun `final class + @Dependent deploys — non-normal scope, no proxy needed`() {
        weld(FinalDependentBean::class.java).initialize().use { container ->
            val bean = container.select(FinalDependentBean::class.java).get()
            assertThat(bean.greet("weld")).isEqualTo("hi weld")
            // @Dependent hands out the real instance directly.
            assertThat(bean.javaClass).isEqualTo(FinalDependentBean::class.java)
        }
    }
}
