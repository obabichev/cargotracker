package org.eclipse.cargotracker.interfaces.booking.rest

import jakarta.ws.rs.core.Application
import jakarta.ws.rs.core.GenericType
import org.assertj.core.api.Assertions.assertThat
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.test.JerseyTest
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory
import org.glassfish.jersey.test.spi.TestContainerFactory
import org.junit.jupiter.api.Test

/**
 * Boots `CargoSearchResource` inside Jersey (the JAX-RS reference implementation) and drives
 * it through actual HTTP requests to prove the KTIJ ticket's claim: Kotlin default values on
 * `@QueryParam` are silently ignored — a request that omits the parameter binds the primitive
 * `Int` to `0`, not to the Kotlin default of `20`.
 */
class CargoSearchResourceJerseyTest : JerseyTest() {

    override fun configure(): Application = ResourceConfig(CargoSearchResource::class.java)

    override fun getTestContainerFactory(): TestContainerFactory = InMemoryTestContainerFactory()

    private val listOfString = object : GenericType<List<String>>() {}

    @Test
    fun `GET query-broken without limit returns empty list — Kotlin default is silently ignored`() {
        val result = target("/cargo-search/query-broken")
            .queryParam("query", "widget")
            .request()
            .get(listOfString)

        assertThat(result)
            .`as`("Expected 20 items from Kotlin default `= 20`, but JAX-RS bound limit to 0")
            .isEmpty()
    }

    @Test
    fun `GET query-broken with explicit limit works — proves the resource is wired correctly`() {
        val result = target("/cargo-search/query-broken")
            .queryParam("query", "widget")
            .queryParam("limit", 5)
            .request()
            .get(listOfString)

        assertThat(result).hasSize(5)
    }

    @Test
    fun `GET correct without limit returns 20 items — proves @DefaultValue works uniformly`() {
        val result = target("/cargo-search/correct")
            .queryParam("query", "widget")
            .request()
            .get(listOfString)

        assertThat(result).hasSize(20)
    }
}
