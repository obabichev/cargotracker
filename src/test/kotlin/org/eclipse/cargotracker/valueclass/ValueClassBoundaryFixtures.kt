package org.eclipse.cargotracker.valueclass

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.ext.ParamConverter
import jakarta.ws.rs.ext.ParamConverterProvider
import jakarta.ws.rs.ext.Provider
import java.lang.reflect.Type

/**
 * Fixtures for the value-class Jakarta-boundary reproducer (see KTIJ ticket referencing
 * finding 08). Every declaration reads like the idiomatic Kotlin one-liner — and each is
 * silently unsafe at a Jakarta reflection boundary because `@JvmInline value class` erases
 * to its underlying type on the JVM.
 *
 * The reflection tests inspect the JVM view of these declarations directly; the Jersey
 * test drives `ValueClassResource` through the real JAX-RS runtime.
 */

/**
 * Canonical strong-typing wrapper — the exact idiom Kotlin promotes as a replacement for
 * "stringly-typed" identifiers. The `init` block is the user's invariant: if Jakarta
 * bypasses construction, the invariant is bypassed too.
 */
@JvmInline
value class EmailAddress(val value: String) {
    init { require("@" in value) { "not an email: $value" } }
}

/**
 * A would-be JPA entity. `email: EmailAddress` reads as a typed field, but JPA reflects
 * the JVM signature — `Field.type == String::class.java`. Any `AttributeConverter` with
 * `EmailAddress` on the model side is never matched because there is no such field type
 * on the JVM.
 *
 * `lateinit` is not allowed on inline-class-typed properties, so we use a sentinel
 * initializer — a common Kotlin workaround for JPA entities that need a no-arg constructor.
 * Making the field nullable (`EmailAddress?`) would force Kotlin to box, and the reflected
 * type would then be `EmailAddress`, not `String` — hiding the trap on nullable fields but
 * leaving it wide open on the idiomatic non-nullable ones (which is what this reproducer
 * demonstrates).
 *
 * We never boot a JPA provider — the reflection tests inspect this class directly to
 * prove what Hibernate / EclipseLink / OpenJPA would see at their reflection layer.
 */
@Entity
@Table(name = "user_email_value_class_demo")
class UserEntity {
    @Id
    @GeneratedValue
    var id: Long? = null

    @Column(name = "email", nullable = false)
    var email: EmailAddress = EmailAddress("placeholder@example.com")
}

/**
 * A conforming `AttributeConverter` for the wrapper. Registration is harmless — JPA
 * providers match converters by the model-side generic parameter against the reflected
 * field type; since the reflected field type is `String`, this converter's `EmailAddress`
 * type parameter never binds.
 */
@Converter(autoApply = true)
class EmailAddressAttributeConverter : AttributeConverter<EmailAddress, String> {
    override fun convertToDatabaseColumn(attribute: EmailAddress?): String? = attribute?.value
    override fun convertToEntityAttribute(dbData: String?): EmailAddress? =
        dbData?.let { EmailAddress(it) }
}

/**
 * A `ParamConverterProvider` that would normalize an incoming email string if JAX-RS ever
 * consulted it for an `EmailAddress` parameter. Marker prefix `"provider:"` lets the
 * Jersey test assert on whether the converter actually fired.
 */
@Provider
class EmailAddressParamConverterProvider : ParamConverterProvider {
    override fun <T : Any?> getConverter(
        rawType: Class<T>,
        genericType: Type?,
        annotations: Array<out Annotation>?,
    ): ParamConverter<T>? {
        // rawType for a value-class parameter is `java.lang.String` — never EmailAddress —
        // so this branch is unreachable through JAX-RS parameter binding.
        if (rawType == EmailAddress::class.java) {
            @Suppress("UNCHECKED_CAST")
            return object : ParamConverter<EmailAddress> {
                override fun fromString(value: String): EmailAddress = EmailAddress("provider:$value")
                override fun toString(value: EmailAddress): String = value.value
            } as ParamConverter<T>
        }
        return null
    }
}

/**
 * A JAX-RS resource that reads like it's binding an `EmailAddress`. In practice both
 * methods are `(String)`-typed on the JVM: JAX-RS binds the raw string and hands it to
 * the Kotlin method, which stores it as an unboxed `EmailAddress` — the `init` block
 * never runs, no `ParamConverterProvider` is consulted.
 */
@Path("/value-class")
class ValueClassResource {

    @GET
    @Path("/path/{email}")
    @Produces(MediaType.TEXT_PLAIN)
    fun byPath(@PathParam("email") email: EmailAddress): String = "got:${email.value}"

    @GET
    @Path("/query")
    @Produces(MediaType.TEXT_PLAIN)
    fun byQuery(@QueryParam("email") email: EmailAddress): String = "got:${email.value}"
}
