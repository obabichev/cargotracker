package org.eclipse.cargotracker.interfaces.booking.rest

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Validates the JVM mechanism behind the Kotlin default-value trap on JAX-RS parameter
 * annotations (see KTIJ ticket referenced from CargoSearchResource).
 *
 * JAX-RS resolves each request parameter, then calls `Method.invoke(resourceInstance, args)`
 * on the primary method. Kotlin default arguments live in a synthetic `${name}$default`
 * overload selected by the *Kotlin caller* through a bitmask — `Method.invoke` never dispatches
 * through it. These tests exercise that mechanism directly rather than booting a full Jersey
 * container: what the tests do (resolve → invoke on primary) is exactly what JAX-RS does.
 */
class CargoSearchResourceReflectionTest {

    private val resource = CargoSearchResource()

    @Test
    fun `Kotlin call site applies the default — the idiom works when invoked from Kotlin`() {
        val result = resource.byQueryBroken(query = "widget")
        assertThat(result).hasSize(20)
    }

    @Test
    fun `Method_invoke on the primary overload bypasses the Kotlin default — mirrors JAX-RS`() {
        val method = CargoSearchResource::class.java.getDeclaredMethod(
            "byQueryBroken",
            String::class.java,
            Int::class.javaPrimitiveType,
        )

        // JAX-RS resolves a missing `@QueryParam("limit")` on an `Int` to the JVM primitive
        // zero and calls `Method.invoke(resource, "widget", 0)`. No `$default` dispatch runs.
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(resource, "widget", 0) as List<String>

        assertThat(result)
            .`as`("Kotlin default `= 20` is silently ignored; limit == 0 → empty page")
            .isEmpty()
    }

    @Test
    fun `Kotlin emits a synthetic default-dispatcher overload — but JAX-RS never calls it`() {
        val syntheticExists = CargoSearchResource::class.java.declaredMethods.any {
            it.name == "byQueryBroken\$default"
        }
        assertThat(syntheticExists)
            .`as`("Kotlin should have emitted the byQueryBroken\$default synthetic overload")
            .isTrue()
    }

    @Test
    fun `nullable + Elvis works for missing param, but empty-string value still throws at the runtime`() {
        // GET /query-nullable?query=widget           → limit resolves to null → Elvis picks 20.
        val nullPath = resource.byQueryNullable(query = "widget", limit = null)
        assertThat(nullPath).hasSize(20)

        // GET /query-nullable?query=widget&limit=    → runtime parses "" as Int before the
        // method body runs, throwing NumberFormatException from Integer.valueOf(""). The
        // Elvis fallback never gets a chance.
        assertThatThrownBy { Integer.valueOf("") }
            .isInstanceOf(NumberFormatException::class.java)
    }
}
