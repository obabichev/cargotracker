package org.eclipse.cargotracker.valueclass

import jakarta.ws.rs.core.Application
import org.assertj.core.api.Assertions.assertThat
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.test.JerseyTest
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.junit.jupiter.api.Test

/**
 * Boots `ValueClassResource` and the registered `EmailAddressParamConverterProvider`
 * inside Jersey and drives them through actual HTTP requests to prove finding 08's claim:
 * a JAX-RS parameter typed as `@JvmInline value class` is bound as the unboxed underlying
 * type, the class's `init` invariant never runs, and any `ParamConverterProvider`
 * registered for the wrapper is silently skipped.
 */
class ValueClassJerseyTest : JerseyTest() {

    override fun configure(): Application = ResourceConfig(
        ValueClassResource::class.java,
        EmailAddressParamConverterProvider::class.java,
    )

    override fun getTestContainerFactory(): TestContainerFactory = InMemoryTestContainerFactory()

    @Test
    fun `GET path with a string that would fail EmailAddress-init returns 200 — invariant silently bypassed`() {
        // "not-an-email" would trip `require("@" in value)` if EmailAddress were actually
        // constructed. JAX-RS binds the raw String, so init never runs.
        val result = target("/value-class/path/not-an-email")
            .request()
            .get(String::class.java)

        assertThat(result)
            .`as`("Expected the request to fail EmailAddress invariant, but it returned the raw path segment")
            .isEqualTo("got:not-an-email")
    }

    @Test
    fun `GET query with a string that would fail EmailAddress-init returns 200 — same trap on @QueryParam`() {
        val result = target("/value-class/query")
            .queryParam("email", "not-an-email")
            .request()
            .get(String::class.java)

        assertThat(result).isEqualTo("got:not-an-email")
    }

    @Test
    fun `Registered ParamConverterProvider for EmailAddress is never consulted — provider prefix is absent`() {
        // If `EmailAddressParamConverterProvider.fromString` fired, the response would be
        // "got:provider:a@b". It never fires — the reflected parameter type is String, so
        // JAX-RS looks up a converter for String and finds the built-in identity path.
        val result = target("/value-class/path/a@b")
            .request()
            .get(String::class.java)

        assertThat(result)
            .`as`("Expected 'got:provider:a@b' if the EmailAddress ParamConverter had fired")
            .isEqualTo("got:a@b")
    }
}
