package org.eclipse.cargotracker.cdi.companion

import jakarta.enterprise.inject.Produces
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Universal proof that CDI's annotation-scanning view of a Kotlin `companion object` is
 * asymmetric across the `@JvmStatic` boundary. See KTIJ ticket referencing finding 09.
 *
 * CDI (Weld / OpenWebBeans / any other implementation) scans the *bean-defining class*
 * for `@Produces` / `@Observes` / `@Disposes` / `@Inject` on its `declaredMethods` and
 * `declaredFields`. These tests inspect what that scan actually sees — without booting
 * Weld — and generalise to every CDI implementation.
 */
class CompanionProducerReflectionTest {

    @Test
    fun `@Produces on companion member lands on the Companion nested class, not on the outer bean class`() {
        // The outer class (which is what CDI scans as a "bean") has no declared methods
        // annotated with @Produces — the annotation is inside the nested $Companion class.
        val outerHasProduces = BrokenCompanionProducerBean::class.java.declaredMethods
            .any { it.isAnnotationPresent(Produces::class.java) }
        assertThat(outerHasProduces)
            .`as`("CDI's scan of BrokenCompanionProducerBean sees no @Produces — this is why the bean produces nothing")
            .isFalse()

        // Meanwhile, the $Companion nested class does carry the @Produces method — but CDI
        // isn't looking at nested classes as beans.
        val companion = BrokenCompanionProducerBean::class.java.declaredClasses
            .single { it.simpleName == "Companion" }
        val companionHasProduces = companion.declaredMethods
            .any { it.isAnnotationPresent(Produces::class.java) }
        assertThat(companionHasProduces)
            .`as`("The @Produces annotation lives on \$Companion.logger — invisible to a scan of the outer class")
            .isTrue()
    }

    @Test
    fun `@JvmStatic emits a real static on the outer bean class — @Produces reaches the CDI-scanned surface`() {
        // With @JvmStatic, Kotlin emits a real static method on the outer class that
        // delegates to the companion. The @Produces annotation is copied onto that bridge,
        // so a scan of JvmStaticCompanionProducerBean.declaredMethods finds it.
        val outerStaticWithProduces = JvmStaticCompanionProducerBean::class.java.declaredMethods
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .any { it.isAnnotationPresent(Produces::class.java) }

        assertThat(outerStaticWithProduces)
            .`as`("@JvmStatic promotes the companion method to a static on the outer class carrying @Produces")
            .isTrue()
    }

    @Test
    fun `Instance @Produces on the bean-defining class is directly visible — the canonical fix`() {
        val hasProduces = InstanceProducerBean::class.java.declaredMethods
            .any { it.isAnnotationPresent(Produces::class.java) }
        assertThat(hasProduces).isTrue()
    }
}
