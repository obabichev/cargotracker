package org.eclipse.cargotracker.cdi.companion

import jakarta.enterprise.inject.spi.DeploymentException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jboss.weld.environment.se.Weld
import org.junit.jupiter.api.Test
import java.util.logging.Logger

/**
 * Runtime proof of the companion-producer trap: bootstraps Weld SE with each producer
 * shape plus a consumer that injects `Logger`. `disableDiscovery()` keeps each container
 * limited to exactly the beans we pass in, so the outcome is attributable to the
 * producer shape alone. See KTIJ ticket referencing finding 09.
 *
 * The runtime target is Weld 5.1 (CDI 4.0 — Jakarta EE 10).
 */
class CompanionProducerWeldTest {

    private fun weld(vararg beans: Class<*>): Weld =
        Weld().disableDiscovery().addBeanClasses(*beans)

    @Test
    fun `broken companion producer — Weld cannot resolve Logger, deploy fails with WELD-001408`() {
        // `@Produces` is on `BrokenCompanionProducerBean$Companion.logger()` — invisible
        // to a scan of the bean-defining class. `LoggerConsumer.@Inject log: Logger` has
        // no matching bean, so container validation fails at initialize().
        assertThatThrownBy {
            weld(
                BrokenCompanionProducerBean::class.java,
                LoggerConsumer::class.java,
            ).initialize()
        }
            .isInstanceOf(DeploymentException::class.java)
            .hasMessageContaining("WELD-001408")
            .hasMessageContaining("Unsatisfied dependencies")
            .hasMessageContaining("Logger")
    }

    @Test
    fun `instance @Produces producer — Weld resolves Logger, injection succeeds`() {
        weld(
            InstanceProducerBean::class.java,
            LoggerConsumer::class.java,
        ).initialize().use { container ->
            val consumer = container.select(LoggerConsumer::class.java).get()
            assertThat(consumer.log.name).isEqualTo("instance-produced")
        }
    }

    @Test
    fun `@JvmStatic companion producer on Weld 5-1 — record whether the CDI 4-0 static producer path fires`() {
        // CDI 4.0 (Jakarta EE 10) added "static producer methods". `@JvmStatic` promotes
        // the companion method to a real JVM static on the outer class and copies
        // `@Produces` onto the static bridge. This test records the empirical outcome on
        // the Weld 5.1 stack this reproducer runs against.
        weld(
            JvmStaticCompanionProducerBean::class.java,
            LoggerConsumer::class.java,
        ).initialize().use { container ->
            val consumer = container.select(LoggerConsumer::class.java).get()
            assertThat(consumer.log.name).isEqualTo("jvmstatic-produced")
        }
    }

    @Test
    fun `select Logger directly — broken companion producer yields UnsatisfiedResolutionException without a consumer bean`() {
        // Without a consumer, container validation passes (no @Inject to check), but
        // programmatic lookup for a producer that CDI never registered still fails.
        weld(BrokenCompanionProducerBean::class.java).initialize().use { container ->
            assertThatThrownBy { container.select(Logger::class.java).get() }
                .hasMessageContaining("Unsatisfied")
        }
    }
}
