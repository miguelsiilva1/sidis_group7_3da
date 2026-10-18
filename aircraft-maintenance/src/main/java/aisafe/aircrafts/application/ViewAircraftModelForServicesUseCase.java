package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.AircraftModelForServicesResponse;
import aisafe.aircrafts.domain.AircraftModelNotFoundException;
import aisafe.aircrafts.domain.AircraftModelRepository;
import aisafe.shared.application.UseCase;

/**
 * Returns what other services need to know about an aircraft model (contract C3). Models are
 * copied to every replica; one not copied here yet makes the request go to the peers.
 */
@UseCase(readOnly = true)
public class ViewAircraftModelForServicesUseCase {
    private final AircraftModelRepository repository;

    public ViewAircraftModelForServicesUseCase(AircraftModelRepository repository) {
        this.repository = repository;
    }

    public AircraftModelForServicesResponse execute(String modelName) {
        return repository.findByModelName(modelName)
                .map(AircraftModelForServicesResponse::from)
                .orElseThrow(() -> new AircraftModelNotFoundException("Aircraft model not found: " + modelName));
    }
}
