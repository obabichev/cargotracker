package org.eclipse.cargotracker.domain.model.handling

import jakarta.persistence.*
import jakarta.validation.constraints.NotNull
import org.apache.commons.lang3.Validate
import org.apache.commons.lang3.builder.EqualsBuilder
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.eclipse.cargotracker.domain.model.cargo.Cargo
import org.eclipse.cargotracker.domain.model.location.Location
import org.eclipse.cargotracker.domain.model.voyage.Voyage
import org.eclipse.cargotracker.domain.shared.DomainObjectUtils
import java.io.Serializable
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * A HandlingEvent is used to register the event when, for instance, a cargo is unloaded from a
 * carrier at a some location at a given time.
 * 
 * 
 * The HandlingEvent's are sent from different Incident Logging Applications some time after the
 * event occurred and contain information about the null [TrackingId], [Location], time
 * stamp of the completion of the event, and possibly, if applicable a [Voyage].
 * 
 * 
 * This class is the only member, and consequently the root, of the HandlingEvent aggregate.
 * 
 * 
 * HandlingEvent's could contain information about a [Voyage] and if so, the event type
 * must be either [Type.LOAD] or [Type.UNLOAD].
 * 
 * 
 * All other events must be of [Type.RECEIVE], [Type.CLAIM] or [Type.CUSTOMS].
 */
@Entity
@NamedQuery(
    name = "HandlingEvent.findByTrackingId",
    query = "Select e from HandlingEvent e where e.cargo.trackingId = :trackingId"
)
open class HandlingEvent : Serializable {
    @Id
    @GeneratedValue
    private val id: Long? = null

    @Enumerated(EnumType.STRING)
    @NotNull
    var type: @NotNull Type? = null
        private set

    @ManyToOne
    @JoinColumn(name = "voyage_id")
    private var voyage: Voyage? = null

    @ManyToOne
    @JoinColumn(name = "location_id")
    @NotNull
    private var location: @NotNull Location? = null

    @NotNull
    @Column(name = "completionTime")
    var completionTime: @NotNull LocalDateTime? = null
        private set

    @NotNull
    @Column(name = "registration")
    var registrationTime: @NotNull LocalDateTime? = null
        private set

    @ManyToOne
    @JoinColumn(name = "cargo_id")
    @NotNull
    private var cargo: @NotNull Cargo? = null

    @get:Transient
    val summary: String?
        get() {
            val builder =
                StringBuilder(location!!.getName())
                    .append("\n")
                    .append(completionTime)
                    .append("\n")
                    .append("Type: ")
                    .append(type)
                    .append("\n")
                    .append("Reg.: ")
                    .append(registrationTime)
                    .append("\n")

            if (voyage != null) {
                builder.append("Voyage: ").append(voyage!!.getVoyageNumber())
            }

            return builder.toString()
        }

    constructor()

    /**
     * @param cargo The cargo
     * @param completionTime completion time, the reported time that the event actually happened (e.g.
     * the receive took place).
     * @param registrationTime registration time, the time the message is received
     * @param type type of event
     * @param location where the event took place
     * @param voyage the voyage
     */
    constructor(
        cargo: Cargo,
        completionTime: LocalDateTime?,
        registrationTime: LocalDateTime?,
        type: Type?,
        location: Location,
        voyage: Voyage?
    ) {
        Validate.notNull<Cargo?>(cargo, "Cargo is required")
        Validate.notNull<LocalDateTime?>(completionTime, "Completion time is required")
        Validate.notNull<LocalDateTime?>(registrationTime, "Registration time is required")
        Validate.notNull<Type?>(type, "Handling event type is required")
        Validate.notNull<Location?>(location, "Location is required")
        Validate.notNull<Voyage?>(voyage, "Voyage is required")

        require(!type!!.prohibitsVoyage()) { "Voyage is not allowed with event type " + type }

        this.voyage = voyage

        // This is a workaround to a Hibernate issue. when the `LocalDateTime` field is persisted into
        // the DB, and retrieved from the DB, the values are different by nanoseconds.
        this.completionTime = completionTime!!.truncatedTo(ChronoUnit.SECONDS)
        this.registrationTime = registrationTime!!.truncatedTo(ChronoUnit.SECONDS)
        this.type = type
        this.location = location
        this.cargo = cargo
    }

    /**
     * @param cargo cargo
     * @param completionTime completion time, the reported time that the event actually happened (e.g.
     * the receive took place).
     * @param registrationTime registration time, the time the message is received
     * @param type type of event
     * @param location where the event took place
     */
    constructor(
        cargo: Cargo,
        completionTime: LocalDateTime?,
        registrationTime: LocalDateTime?,
        type: Type?,
        location: Location
    ) {
        Validate.notNull<Cargo?>(cargo, "Cargo is required")
        Validate.notNull<LocalDateTime?>(completionTime, "Completion time is required")
        Validate.notNull<LocalDateTime?>(registrationTime, "Registration time is required")
        Validate.notNull<Type?>(type, "Handling event type is required")
        Validate.notNull<Location?>(location, "Location is required")

        require(!type!!.requiresVoyage()) { "Voyage is required for event type " + type }

        // This is a workaround to a Hibernate issue. when the `LocalDateTime` field is persisted into
        // the DB, and retrieved from the DB, the values are different by nanoseconds.
        this.completionTime = completionTime!!.truncatedTo(ChronoUnit.SECONDS)
        this.registrationTime = registrationTime!!.truncatedTo(ChronoUnit.SECONDS)
        this.type = type
        this.location = location
        this.cargo = cargo
        this.voyage = null
    }

    fun getVoyage(): Voyage {
        return DomainObjectUtils.nullSafe<Voyage>(this.voyage, Voyage.NONE)
    }

    fun getLocation(): Location {
        return this.location!!
    }

    fun getCargo(): Cargo {
        return this.cargo!!
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }

        if (o == null || o !is HandlingEvent) {
            return false
        }

        val event = o

        return sameEventAs(event)
    }

    private fun sameEventAs(other: HandlingEvent?): Boolean {
        return other != null
                && EqualsBuilder()
            .append(this.cargo, other.cargo)
            .append(this.voyage, other.voyage)
            .append(this.completionTime, other.completionTime)
            .append(this.location, other.location)
            .append(this.type, other.type)
            .isEquals()
    }

    override fun hashCode(): Int {
        return HashCodeBuilder()
            .append(cargo)
            .append(voyage)
            .append(completionTime)
            .append(location)
            .append(type)
            .toHashCode()
    }

    override fun toString(): String {
        val builder =
            StringBuilder("\n--- Handling event ---\n")
                .append("Cargo: ")
                .append(cargo!!.getTrackingId())
                .append("\n")
                .append("Type: ")
                .append(type)
                .append("\n")
                .append("Location: ")
                .append(location!!.getName())
                .append("\n")
                .append("Completed on: ")
                .append(completionTime)
                .append("\n")
                .append("Registered on: ")
                .append(registrationTime)
                .append("\n")

        if (voyage != null) {
            builder.append("Voyage: ").append(voyage!!.getVoyageNumber()).append("\n")
        }

        return builder.toString()
    }

    /**
     * Handling event type. Either requires or prohibits a carrier movement association, it's never
     * optional.
     */
    enum class Type
    /**
     * Private enum constructor.
     * 
     * @param voyageRequired whether or not a voyage is associated with this event type
     */(private val voyageRequired: Boolean) {
        // Loaded onto voyage from port location.
        LOAD(true),

        // Unloaded from voyage to port location
        UNLOAD(true),

        // Received by carrier
        RECEIVE(false),

        // Cargo claimed by recepient
        CLAIM(false),

        // Cargo went through customs
        CUSTOMS(false);

        /** @return True if a voyage association is required for this event type.
         */
        fun requiresVoyage(): Boolean {
            return voyageRequired
        }

        /** @return True if a voyage association is prohibited for this event type.
         */
        fun prohibitsVoyage(): Boolean {
            return !requiresVoyage()
        }

        fun sameValueAs(other: Type?): Boolean {
            return other != null && this == other
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
