package org.eclipse.cargotracker.interfaces.booking.web

import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.inject.Named
import org.eclipse.cargotracker.interfaces.booking.facade.BookingServiceFacade
import org.eclipse.cargotracker.interfaces.booking.facade.dto.CargoRoute

/**
 * Handles viewing cargo details. Operates against a dedicated service facade, and could easily be
 * rewritten as a thick Swing client. Completely separated from the domain layer, unlike the
 * tracking user interface.
 * 
 * 
 * In order to successfully keep the domain model shielded from user interface considerations,
 * this approach is generally preferred to the one taken in the tracking controller. However, there
 * is never any one perfect solution for all situations, so we've chosen to demonstrate two
 * polarized ways to build user interfaces.
 */
@Named
@RequestScoped
class CargoDetails {
    @Inject
    private lateinit var bookingServiceFacade: BookingServiceFacade

    var trackingId: String? = null
    var cargo: CargoRoute? = null
        private set

    fun load() {
        cargo = bookingServiceFacade!!.loadCargoForRouting(trackingId)
    }
}
