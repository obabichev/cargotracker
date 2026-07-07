package org.eclipse.cargotracker.application.internal

import jakarta.ejb.Stateless
import jakarta.inject.Inject
import org.eclipse.cargotracker.application.ApplicationEvents
import org.eclipse.cargotracker.application.CargoInspectionService
import org.eclipse.cargotracker.domain.model.cargo.CargoRepository
import org.eclipse.cargotracker.domain.model.cargo.TrackingId
import org.eclipse.cargotracker.domain.model.handling.HandlingEventRepository
import java.util.logging.Level
import java.util.logging.Logger

@Stateless
class DefaultCargoInspectionService : CargoInspectionService {
    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var applicationEvents: ApplicationEvents

    @Inject
    private lateinit var cargoRepository: CargoRepository

    @Inject
    private lateinit var handlingEventRepository: HandlingEventRepository

    override fun inspectCargo(trackingId: TrackingId?) {
        val cargo = cargoRepository.find(trackingId)

        if (cargo == null) {
            logger.log(Level.WARNING, "Can't inspect non-existing cargo {0}", trackingId)
            return
        }

        val handlingHistory =
            handlingEventRepository.lookupHandlingHistoryOfCargo(trackingId)

        cargo.deriveDeliveryProgress(handlingHistory)

        if (cargo.getDelivery().isMisdirected()) {
            applicationEvents.cargoWasMisdirected(cargo)
        }

        if (cargo.getDelivery().isUnloadedAtDestination()) {
            applicationEvents.cargoHasArrived(cargo)
        }

        cargoRepository.store(cargo)
    }
}
