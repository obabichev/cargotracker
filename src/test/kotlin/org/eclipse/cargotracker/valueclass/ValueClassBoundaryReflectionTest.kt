package org.eclipse.cargotracker.valueclass

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Universal proof that `@JvmInline value class` disappears at every Jakarta reflection
 * boundary: JPA, JAX-RS, JSON-B, and Bean Validation providers all navigate the JVM
 * signature via `java.lang.reflect.Method` / `Field` / `Parameter`, which report the
 * unboxed underlying type. See KTIJ ticket referencing finding 08.
 *
 * These tests inspect the fixtures directly rather than booting Hibernate/Jersey — the
 * reflection layer these tests exercise is *exactly* the layer every Jakarta provider
 * consults, so the conclusion generalises.
 */
class ValueClassBoundaryReflectionTest {

    @Test
    fun `JAX-RS parameter reflection reports String, not EmailAddress — @PathParam`() {
        // `getConverter(rawType, ...)` on a ParamConverterProvider receives this exact class.
        val method = ValueClassResource::class.java.declaredMethods.single {
            it.name.startsWith("byPath")
        }
        val paramType = method.parameterTypes.single()

        assertThat(paramType)
            .`as`("JAX-RS providers see the unboxed underlying type of a value-class parameter")
            .isEqualTo(String::class.java)
            .isNotEqualTo(EmailAddress::class.java)
    }

    @Test
    fun `JAX-RS parameter reflection reports String, not EmailAddress — @QueryParam`() {
        val method = ValueClassResource::class.java.declaredMethods.single {
            it.name.startsWith("byQuery")
        }
        val paramType = method.parameterTypes.single()

        assertThat(paramType).isEqualTo(String::class.java)
    }

    @Test
    fun `Kotlin mangles the JVM method name when any parameter is a value class — routing by name breaks too`() {
        // Kotlin appends a hash suffix (e.g. `byPath-7OTboa0`) to disambiguate JVM overloads
        // that would otherwise clash after value-class unboxing. Any Jakarta hook that looks
        // up methods by source name (Bean Validation method-level constraints, custom
        // reflection tools, ByteBuddy interceptors) misses the mangled overload. Providers
        // that dispatch by annotation (JAX-RS, JPA lifecycle) route correctly, but their
        // *parameter/field reflection* still reports the unboxed underlying type.
        val methodNames = ValueClassResource::class.java.declaredMethods.map { it.name }

        assertThat(methodNames).noneMatch { it == "byPath" || it == "byQuery" }
        assertThat(methodNames).anyMatch { it.startsWith("byPath-") }
        assertThat(methodNames).anyMatch { it.startsWith("byQuery-") }
    }

    @Test
    fun `JPA @Entity field reflection reports String — AttributeConverter with EmailAddress model type cannot match`() {
        // JPA providers match `AttributeConverter<X, Y>` by comparing `X` against the
        // *reflected* field type. Because Kotlin unboxes `EmailAddress` to `String` at the
        // field, `X = EmailAddress` never binds — `EmailAddressAttributeConverter` is
        // registered, autoApply=true, and never runs on this field.
        val field = UserEntity::class.java.getDeclaredField("email")

        assertThat(field.type)
            .`as`("JPA sees the field as `String` — an EmailAddress-typed converter can never match")
            .isEqualTo(String::class.java)
            .isNotEqualTo(EmailAddress::class.java)
    }

    @Test
    fun `EmailAddress init require runs at Kotlin call sites — invariant works when Jakarta is not in the loop`() {
        // Kotlin-visible use: the invariant fires.
        assertThatThrownBy { EmailAddress("no-at-sign") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not an email")

        // Well-formed use: passes.
        val ok = EmailAddress("a@b")
        assertThat(ok.value).isEqualTo("a@b")
    }

    @Test
    fun `Storing a value-class-typed field through raw reflection bypasses the init block — same path Jakarta takes`() {
        // JPA / JAX-RS / JSON-B write into the field via reflection with the raw
        // underlying value. Because the JVM field type is `String`, no `init` block runs
        // and the invariant is silently violated.
        val bean = UserEntity()
        val field = UserEntity::class.java.getDeclaredField("email").apply { isAccessible = true }

        field.set(bean, "no-at-sign")

        // Kotlin-source view claims `email: EmailAddress`; the actual stored value is a
        // raw String that would have failed `require("@" in value)` — but the require
        // never ran, because the field type is `String` on the JVM.
        val raw = field.get(bean)
        assertThat(raw).isEqualTo("no-at-sign")
        assertThat(raw).isInstanceOf(String::class.java)
    }
}
